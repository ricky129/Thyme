# Thyme

Thyme is a terminal dashboard for monitoring a Linux host over SSH. It has two parts: a small client runs on your computer, while a modular server runtime runs on the monitored host. SSH carries the terminal input and output.

## Features

- Dashboard for CPU usage, temperature, load, memory, GPUs, disks, filesystems, and system services.
- Container view for Docker containers, including recent logs and confirmed start, stop, and restart actions.

## Requirements

- **Client computer:** Java 25 or later and an OpenSSH client.
- **Deploy/build computer:** Linux x86_64 and a full JDK 25 or later, with `jlink` and the JDK `jmods` directory.

## Install and deploy

1. Copy the example configuration:

   ```sh
   cp config.properties.example config.properties
   ```

2. Edit `config.properties` with the SSH username and host:

   ```properties
   deploy.user=your-user
   deploy.host=your-server.example
   deploy.remote_dir=~/docker/thyme
   # deploy.port=2222
   # deploy.key=~/.ssh/id_ed25519
   ```

   `deploy.remote_dir` is on the **monitored host**. It defaults to `~/docker/thyme`; `deploy.sh` creates it if needed. You do not need to create that directory on your computer.

3. Deploy from the project directory:

   ```sh
   ./deploy.sh
   ```

   The script builds the client and server runtime, copies the runtime to the remote host, and installs the client locally in `~/.local/bin/thyme` and `~/.local/lib/thyme/`. It may ask SSH to authenticate. Ensure `~/.local/bin` is on your local `PATH`.

4. Start Thyme:

   ```sh
   thyme
   ```

   You can also pass a target directly:

   ```sh
   thyme your-user@your-server.example
   ```

## Configuration

The client reads its configuration in this order:

1. The file named by `THYME_CONFIG`, if set.
2. `~/.config/thyme/config.properties`, if present.
3. `config.properties` in the current directory, if present.

`deploy.user` is required when using `thyme` without a target argument. Set `deploy.host` to a hostname or IPv4 address. Alternatively, omit `deploy.host` and set `server.tailscale` or `server.local` to a URL; the hostname is taken from that URL.

Optional settings:

| Property            | Purpose                                              | Default                     |
| ------------------- | ---------------------------------------------------- | --------------------------- |
| `deploy.remote_dir` | Runtime installation directory on the monitored host | `~/docker/thyme`            |
| `deploy.port`       | SSH port                                             | OpenSSH default             |
| `deploy.key`        | Private key file used by SSH                         | OpenSSH configuration/agent |

`config.properties` is ignored by Git so local connection settings are not committed.

## Build and test

Build both Maven modules, run tests, and create the server runtime:

```sh
./mvnw clean verify
```

The client JAR is written to `client/target/thyme-client.jar`. The server's `jlink` runtime is written to `server/target/thyme-server-runtime/`. These are generated build outputs and do not need to be committed.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for the module layout, runtime flow, dependency and JPMS decisions, and guidance for common changes.
