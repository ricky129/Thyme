# Thyme Architecture

Thyme is a Linux host-monitoring TUI with two separately built programs. The **client** runs on the computer where the user types `thyme`; it reads SSH settings and opens an interactive SSH terminal. The **server** runs on the monitored host; it collects system data and renders the TUI. SSH transports terminal input and output, so Thyme does not define an application-level network protocol.

## Runtime flow

```text
Client computer                                         Monitored Linux host
┌──────────────────────────┐       SSH with TTY          ┌─────────────────────────────┐
│ bin/thyme                │ ─────────────────────────▶ │ thyme-server-runtime/bin/java│
│   client module          │                            │   --enable-native-access     │
│   config + SSH launcher  │                            │   ServerMain                 │
└──────────────────────────┘                            │     └─ Shell                 │
                                                        │        ├─ DashboardPage      │
                                                        │        └─ ContainerPage      │
                                                        │             └─ Providers     │
                                                        └─────────────────────────────┘
```

1. `bin/thyme` starts the local Java client module from `~/.local/lib/thyme/thyme-client.jar`.
2. `com.ricky.thyme.Main` reads the optional target and `ThymeConfig`, validates the destination, and calls `SshSessionLauncher`.
3. `SshSessionLauncher` invokes `ssh -t`. Its remote command starts the deployed `thyme-server-runtime` image and enables native access for the named JNA module.
4. `ServerMain` configures OSHI's host paths, constructs the providers and pages, and starts `Shell`.
5. `Shell` owns the Lanterna screen, input/render loop, background executor, and navigation. The dashboard samples metrics and renders snapshots; the container page uses its provider for Docker data and actions.

These are different programs, artifacts, and JVM processes. Only the server artifact is copied to the monitored host. The server image includes its own Java runtime; the local client is a small Java module and still requires Java 25 or newer locally.

## Build modules and JPMS

Maven modules separate build inputs and outputs. JPMS modules define runtime readability and package exports; they are related but not interchangeable.

| Maven module | JPMS module | Purpose and output |
|---|---|---|
| Root `pom.xml` | — | Reactor build, shared Java/dependency versions, and dependency-convergence enforcement. |
| `client/` | `com.ricky.thyme.client` | Local SSH/configuration client; produces `client/target/thyme-client.jar`. |
| `server/` | `com.ricky.thyme.server` | Remote dashboard, providers, command execution, and UI; produces the server module and a `jlink` runtime image at `server/target/thyme-server-runtime/`. |

Each application module has a `module-info.java`. The client has no third-party runtime dependencies. The server requires named modules for Lanterna, Jackson, OSHI, and SLF4J.

Lanterna, Jackson, and SLF4J already publish explicit module descriptors. The pinned OSHI and JNA dependencies do not. `server/pom.xml` uses ModiTect to generate OSHI's descriptor with only the packages Thyme consumes exported. The checked-in descriptors in `server/src/moditect/jna/` and `server/src/moditect/jna-platform/` are compiled and patched into the staged JARs; JNA's packages are qualified to OSHI/JNA Platform, and JNA Platform's packages are qualified to OSHI/JNA for its native type conversion. The JNA module is launched with `--enable-native-access=com.sun.jna` to permit its native library loading without the unnamed-module warning.

`jlink` only links explicit named modules. The `server/target/modules/` staging directory contains patched dependencies and the server module, and the linked image contains those modules plus the required JDK modules. Maven still resolves and pins dependency versions; JPMS does not replace dependency management or guarantee that arbitrary library versions are compatible.

Build and deployment require a **full JDK** with `jlink` and its `jmods` directory. Application bytecode targets Java 25; the linked server runtime uses the Java version of the builder JDK. The deploy workflow supports building and deploying an x86_64 Linux server image; the generated image is platform-specific. A remote host does not need Java installed. The local client launcher does need Java 25 or newer.

## Shared feature and data-flow pattern

```text
Shell / Page ── schedules ──▶ Provider ── reads ──▶ OSHI, /proc, /sys, or CLI
      ▲                           │
      └──── renders ◀── Snapshot ─┘
```

- A **Page** owns feature interaction and rendering; it does not collect system data itself.
- A **Provider** reads or decodes data and returns typed values. OSHI is used for CPU and memory; Linux providers use proc/sys files or system commands for other metrics and features.
- A **Snapshot** carries immutable data from collection to rendering. Missing data remains distinct from a measured zero.
- `Shell` keeps slow collection and command work off the terminal input/render loop. It alone owns and updates the Lanterna screen.
- External feature commands use `ICommandRunner`, which accepts argument lists and timeouts rather than shell command strings. `ProcessCommandRunner` drains and caps output and terminates timed-out children.
- UI actions that mutate host state are validated before execution; Docker actions require confirmation in `ContainerPage`.

For example, `DashboardPage` asks the CPU, memory, GPU, disk, and service providers for data, combines the results into `DashboardSnapshot`, and renders the available panels. The shell can continue drawing while collection runs.

## Source file map

Paths below are relative to the repository root.

### Client

| File | Role |
|---|---|
| `client/src/main/java/module-info.java` | Defines the client JPMS module. |
| `client/src/main/java/com/ricky/thyme/Main.java` | Client entry point; validates command-line input, reads configuration, and launches SSH. |
| `client/src/main/java/com/ricky/thyme/remote/ThymeConfig.java` | Finds and loads user configuration and validates SSH settings. |
| `client/src/main/java/com/ricky/thyme/remote/SshTarget.java` | Validates the `user@host` target, including its username, hostname, and IPv4 address. |
| `client/src/main/java/com/ricky/thyme/remote/SshSessionLauncher.java` | Constructs the SSH command, starts it with inherited terminal streams, and returns its exit status. |
| `client/src/test/java/` | Client argument, configuration, target validation, and SSH command tests. |

### Server

| File or area | Role |
|---|---|
| `server/src/main/java/module-info.java` | Declares the server module's runtime requirements. |
| `server/src/main/java/com/ricky/thyme/server/ServerMain.java` | Server entry point; configures OSHI, composes providers/pages, and starts the shell. |
| `server/src/main/java/com/ricky/thyme/ui/Page.java` | Shared page lifecycle, input, and rendering contract. |
| `server/src/main/java/com/ricky/thyme/ui/Shell.java` | Owns the screen, input/render loop, refresh scheduling, and page navigation. |
| `server/src/main/java/com/ricky/thyme/exec/ICommandRunner.java` | Interface for external feature commands, including the structured result record. |
| `server/src/main/java/com/ricky/thyme/exec/ProcessICommandRunner.java` | Runs bounded child processes and collects their output. |
| `server/src/main/java/com/ricky/thyme/feature/dashboard/DashboardPage.java`, `DashboardSnapshot.java` | Collects/composes system metrics and renders the main dashboard. |
| `server/src/main/java/com/ricky/thyme/feature/cpu/` | CPU snapshot and OSHI provider. |
| `server/src/main/java/com/ricky/thyme/feature/memory/` | Memory snapshot, OSHI provider, and standalone memory page. |
| `server/src/main/java/com/ricky/thyme/feature/gpu/` | GPU provider contract, readings/snapshots, NVIDIA NVML support via OSHI, and Linux sysfs providers. |
| `server/src/main/java/com/ricky/thyme/feature/disk/` | Disk/filesystem snapshot and Linux collection provider. |
| `server/src/main/java/com/ricky/thyme/feature/network/` | Network snapshot and provider contract (collection logic still to be implemented). |
| `server/src/main/java/com/ricky/thyme/feature/services/` | systemd service model, provider, and standalone page implementation. |
| `server/src/main/java/com/ricky/thyme/feature/containers/` | Docker container model, provider/socket client contracts (`IContainerProvider`, `DockerContainerProvider`, `IDockerEngineClient`, `UnixSocketDockerClient`; socket logic still to be implemented), and the card-grid container page. `SvgIconRasterizer` turns Simple Icons SVGs into 6x3 sprites; `ContainerIconCache` keeps them in `~/.cache/thyme/container-icons.json`, fills it at deploy time (`ServerMain --sync-icons`, run by `deploy.sh`) and converts icons for new containers while the page runs. |
| `server/src/moditect/jna-platform/module-info.java` | Explicit descriptor used when staging the JNA Platform dependency for `jlink`. |
| `server/src/test/java/` | Provider, command runner, shell, and server-entry tests. |

### Build, deployment, and container files

| File | Role |
|---|---|
| `pom.xml` | Maven reactor, shared Java release and dependency versions, and convergence rule. |
| `client/pom.xml` | Client artifact and test configuration. |
| `server/pom.xml` | Server dependencies, external module descriptors, module staging, and `jlink` image creation. |
| `deploy.sh` | Checks architecture/JDK prerequisites, builds both artifacts, deploys only the server runtime, and installs the local client launcher and jar. |
| `bin/thyme` | Starts the installed client module using the local Java runtime. |
| `config.properties.example` | Documents SSH target and deployment settings. |
| `Dockerfile` | Builds the server runtime image and provides a container entry point. |
| `docker-compose.yml` | Defines the container host-monitoring setup; its current entrypoint override keeps the container idle. |

## Where to make common changes

| Change | Main files to update | Tests or checks |
|---|---|---|
| Add a monitored metric | Relevant `server/.../feature/<name>/*Provider.java` and `*Snapshot.java`; connect it in `DashboardPage.java` and `DashboardSnapshot.java` | Tests under `server/src/test/java/com/ricky/thyme/feature/<name>/`; dashboard tests if composition/rendering changes |
| Change dashboard layout or metric display | `server/.../feature/dashboard/DashboardPage.java`; update the snapshot only if collected data changes | Dashboard-focused tests; `server/.../ui/ShellTest.java` only if navigation/lifecycle changes |
| Change terminal lifecycle, refresh cadence, or page navigation | `server/.../ui/Shell.java` and `Page.java` | `server/src/test/java/com/ricky/thyme/ui/ShellTest.java` |
| Change container data or actions | `server/.../feature/containers/IContainerProvider.java`, `DockerContainerProvider.java`, `UnixSocketDockerClient.java`, `ContainerSnapshot.java`, and/or `ContainerPage.java` | Container provider tests and shell tests for navigation |
| Change service discovery or actions | `server/.../feature/services/ServiceProvider.java`, `ServiceSnapshot.java`, and active page integration | Service provider tests and page/shell tests |
| Change local CLI options or SSH behavior | `client/.../Main.java`, `client/.../remote/ThymeConfig.java`, `SshTarget.java`, or `SshSessionLauncher.java` | `client/src/test/java/com/ricky/thyme/` and `remote/` tests |
| Change command execution safety/limits | `server/.../exec/ICommandRunner.java` or `ProcessICommandRunner.java` | `server/src/test/java/com/ricky/thyme/exec/ProcessCommandRunnerTest.java` |
| Change dependencies, JPMS names, or linked modules | Root `pom.xml`, module `pom.xml` files, and `module-info.java`; update the JNA Platform descriptor if that dependency changes | `./mvnw clean verify` with a full JDK; inspect the linked runtime's module list |
| Change deployment/install behavior | `deploy.sh`, `bin/thyme`, and `config.properties.example` | Shell syntax check and a deployment dry run against a test host |
| Change the container image or Compose behavior | `Dockerfile`, `docker-compose.yml`, and `.dockerignore` | Build the image and inspect the resulting command/mounts |

Run `./mvnw clean verify` to test both Maven modules and create the `jlink` image. `deploy.sh` additionally checks that both the builder and remote host are Linux x86_64 and requires `JAVA_HOME` to point to a full JDK containing `jlink` and `jmods`.
