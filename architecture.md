# DriveBay — Architettura, funzionalità e tecnologie

Documento di progettazione · ottobre 2026 · versione 0.2

## 1. Obiettivo e ambito

DriveBay è un'interfaccia da terminale (TUI) per amministrare un server Linux. Il programma gira **sul server come utente normale**; l'utente lo raggiunge con `ssh -t`. Solo i singoli comandi che richiedono privilegi vengono eseguiti con `sudo -n`. Non servono un browser, un servizio di rete DriveBay o un agent separato.

**In ambito:** monitoraggio (CPU, memoria, dischi, rete), processi, servizi systemd, log, storage, rete, utenti, aggiornamenti, file e container. L'accesso alla shell remota è fornito da SSH, non da una feature terminale interna.

**Fuori ambito (almeno all'inizio):** interfaccia web, gestione di molte macchine in parallelo nella stessa TUI, protocollo client-server DriveBay, configurazione avanzata di firewall e SELinux, archiviazione di storici di metriche sul server.

## 2. Principi di progetto

- **Un solo programma e un solo processo**: TUI e accesso al sistema girano sul server. SSH fornisce terminale, autenticazione e cifratura.
- **Privilegi minimi**: il processo resta un utente normale; solo operazioni esplicitamente autorizzate invocano `sudo -n -- <comando>`.
- **Un solo confine per i comandi di sistema**: ogni comando passa da `CommandRunner`, sostituibile con un fake nei test.
- **Funzionalità indipendenti**: ogni area tiene insieme i suoi dati, Provider/Manager e Page.
- **Sola lettura per default**: le azioni che modificano il sistema sono esplicite, confermate e tracciate.
- **Niente shell intermedie**: `ProcessBuilder` riceve argomenti distinti, mai comandi composti per `sh -c`.
- **Tutto testabile senza terminale e senza un Linux reale**: un `CommandRunner` fake e output di comandi registrati.

## 3. Architettura generale

```
Computer dell'utente                      Server Linux
┌───────────────────┐        SSH -t      ┌──────────────────────────────┐
│ Terminale locale  │ ═════════════════▶ │ Main → Shell → Page          │
│ tastiera/schermo  │   terminale remoto  │                 │            │
└───────────────────┘                    │           Provider/Manager   │
                                         │                 │            │
                                         │          CommandRunner        │
                                         │                 │            │
                                         │    ProcessBuilder / OSHI      │
                                         └──────────────────────────────┘
                                             utente normale; sudo -n
                                             solo per comandi autorizzati
```

### Un solo modulo Maven

| Modulo | Gira su | Responsabilità |
| --- | --- | --- |
| `drivebay` | server Linux | Un solo jar: entry point, TUI, funzionalità, accesso al sistema e utility di esecuzione comandi. |

Non ci sono moduli `protocol`, `agent` o `tui` distinti, né un trasporto applicativo: il terminale SSH trasporta già input e output interattivi. Le dipendenze tra classi restano interne al programma: `Page → Provider/Manager → CommandRunner → ProcessBuilder` (oppure API locali come OSHI).

Il codice già presente nel repository è ancora organizzato nei vecchi moduli `protocol`, `agent` e `tui`; questa è l'architettura di destinazione, non una descrizione della migrazione già completata.

## 4. Esecuzione dei comandi

Ogni comando esterno deve passare dall'interfaccia `CommandRunner`. Il codice delle funzionalità fornisce una lista di argomenti e un timeout e riceve un risultato strutturato; non avvia processi direttamente.

```java
public interface CommandRunner {
    Result run(List<String> command, Duration timeout)
            throws IOException, InterruptedException;

    record Result(int exitCode, String stdout, String stderr) {
        public boolean ok() { return exitCode == 0; }
    }
}
```

`ProcessCommandRunner` è l'implementazione reale basata su `ProcessBuilder`; i test possono passare un `CommandRunner` finto con risultati registrati. L'implementazione deve:

- ricevere gli argomenti separatamente, non interpretarli come una riga di shell;
- chiudere lo stdin del sottoprocesso (su Linux, per esempio, collegandolo a `/dev/null`);
- drenare stdout e stderr in parallelo per evitare che il processo si blocchi su un buffer pieno;
- imporre un timeout e terminare il processo quando scade;
- limitare i byte conservati in memoria per ciascun output, continuando a drenare e scartando l'eccedenza.

Gli errori di avvio, timeout e lettura dell'output devono essere espliciti. Un codice di uscita non-zero è un risultato da interpretare dal chiamante, non va trasformato in successo.

## 5. Privilegi e confini applicativi

La regola di sicurezza è semplice: **l'intero processo DriveBay gira come utente normale; solo i singoli comandi che lo richiedono sono prefissati con `sudo -n --`**. `-n` impedisce richieste interattive di password che rovinerebbero la TUI; `--` separa le opzioni di sudo dal comando.

```java
public final class Sudo {
    private Sudo() {}

    public static List<String> asRoot(String... command) {
        var full = new ArrayList<>(List.of("sudo", "-n", "--"));
        full.addAll(List.of(command));
        return full;
    }
}
```

`Sudo` prepara la lista di argomenti; non esegue processi e non concede privilegi. Solo il `CommandRunner` avvia il comando risultante. Le operazioni di sola lettura usano il runner normale; solo le specifiche operazioni di scrittura passano da `Sudo.asRoot(...)`.

Ogni funzionalità è composta dai suoi modelli, da un Provider o Manager e dalla Page. La Page mostra i risultati e raccoglie l'intento dell'utente; Provider/Manager applica la logica e invoca `CommandRunner`. In questo modo la TUI non contiene invocazioni dirette di comandi e i privilegi restano visibili in un punto circoscritto.

Per operazioni potenzialmente distruttive la Page chiede conferma; Provider/Manager valida i parametri anche dopo la conferma. Le feature non pertinenti non devono conoscere `sudo`, sottoprocessi o dettagli di systemd.

## 6. Interfaccia TUI

### Schermata

```
┌ Host: srv01 (100.64.0.7) · connesso · 12 ms ─────────────────────────┐
│ Panoramica │                                                         │
│ Metriche   │            pagina attiva                                │
│ Processi   │                                                         │
│ Servizi    │                                                         │
│ Log        │                                                         │
│ …          │                                                         │
├──────────────────────────────────────────────────────────────────────┤
│ q esci · / filtra · ? aiuto · r aggiorna                             │
└──────────────────────────────────────────────────────────────────────┘
```

### Componenti previsti

- **Shell**: barra laterale, area pagina e barra di stato. Gestisce il ciclo UI e la pagina attiva; gira sul terminale remoto fornito da SSH.
- **Page**: una per funzionalità; interagisce con i Provider/Manager, non con processi di sistema direttamente.

```java
public abstract class Page {
    public abstract String title();
    public void onShow() {}
    public void onHide() {}
    public abstract void handleKey(KeyStroke key);
    public abstract void render(Screen screen, Rect area);
}
```

- **Un solo thread UI** possiede lo schermo e disegna; operazioni lente possono usare task in background e consegnare aggiornamenti alla coda UI.
- **ViewModel/Presenter** separato dal disegno: la logica di ogni pagina (ordinamento, filtri, selezione) si testa senza terminale.
- **Widget riusabili**: tabella ordinabile e filtrabile, barra/gauge, grafico storico a buffer circolare (per esempio 60 campioni), vista log con scroll e follow, dialogo di conferma, form, campo di filtro.
- **Tasti**: frecce o `j`/`k`, `Tab` tra pannelli, `/` filtro, `?` aiuto, `h` cambio host, `r` aggiorna, `q` esci.
- **Host**: la sessione SSH sceglie la macchina prima che DriveBay parta. La TUI iniziale gestisce un solo server e non include host switcher o riconnessione di un trasporto applicativo.

## 7. Catalogo delle funzionalità

| Area | Cosa mostra / fa | Fonte dati | Modalità | Privilegi | Fase |
| --- | --- | --- | --- | --- | --- |
| Panoramica | Hostname, OS, kernel, uptime, carico, riepilogo uso | OSHI, `/etc/os-release` | lettura + polling | no | 1 |
| Metriche | CPU totale e per core, RAM/swap, I/O dischi, rete, temperature | OSHI, `/proc`, `/sys/class/hwmon` | polling periodico | no | 1 |
| Processi | Lista ordinabile e filtrabile, dettaglio, invio segnali | OSHI | lettura + azione | azioni su altri utenti: sì | 2 |
| Servizi | Stato, abilitazione, start/stop/restart/enable | `systemctl --output=json` (poi D-Bus) | lettura + azione | azioni: sì | 3 |
| Log | Journal con filtri (unità, priorità, periodo), follow | `journalctl -o json -f` | lettura/follow | alcuni log: gruppo `systemd-journal` | 3 |
| Storage | Dischi, partizioni, mount, uso, SMART di base | `lsblk --json`, `/proc/mounts` | lettura | modifiche: sì | 4 |
| Rete | Interfacce, IP, route, DNS, traffico | `ip -j`, `/proc/net/dev`, `nmcli` | lettura + polling | modifiche: sì | 4 |
| Utenti e gruppi | Elenco, shell, gruppi, ultimi accessi | `getent`, `/etc/passwd`, `lastlog` | lettura | modifiche: sì | 4 |
| File | Navigazione, lettura, modifica semplice, watch | `java.nio.file`, `WatchService` | lettura + watch | come l'utente | 5 |
| Container | Lista, stato, log, start/stop (Docker/Podman) | CLI `--format json`, poi socket Unix | lettura + azione | accesso socket: gruppo `docker` | 5 |
| Aggiornamenti | Pacchetti aggiornabili, applicazione | `apt` / `dnf` / `pacman` | lettura + azione lunga | sì | 6 |
| Firewall, SELinux | Stato, poi regole | firewalld (D-Bus), `nft` | lettura + azione | sì | futuro |

### Ricetta per aggiungere una funzionalità

1. **`feature/<nome>/`**: i record dei dati e il Provider o Manager della feature.
2. La feature usa `CommandRunner` per ogni comando esterno e `Sudo.asRoot(...)` solo per le azioni che richiedono privilegi.
3. Aggiungere una Page/ViewModel e registrare la pagina nella Shell.
4. Testare Provider/Manager con un `CommandRunner` fake che restituisce output noti.

La feature non deve introdurre un dispatcher, un protocollo di rete o un runner di comandi parallelo.

## 8. Avvio remoto con SSH

La TUI gira sul server, quindi SSH non trasporta messaggi applicativi: apre una shell interattiva e mostra il terminale remoto.

```sh
ssh -t ricky@100.x.y.z java -jar drivebay.jar
```

`-t` alloca il pseudo-terminale necessario alla TUI. Funziona con IP, DNS, alias in `~/.ssh/config` e host Tailscale. Il jar e un runtime Java compatibile devono essere installati sul server; non c'è deploy automatico da parte di un client DriveBay. SSH gestisce autenticazione, chiavi, cifratura e routing.

## 9. Sicurezza

- **Autenticazione e cifratura**: delegate a SSH; DriveBay non gestisce credenziali SSH.
- **Utente normale**: non avviare l'intero jar con `sudo`. Le sole elevazioni devono essere invocazioni esplicite di `sudo -n --` attraverso `CommandRunner`.
- **Allowlist sudoers**: autorizzare comandi specifici e argomenti precisi. Evitare wildcard ampie: in sudoers possono corrispondere a spazi e argomenti inattesi.
- **Niente shell injection**: `ProcessBuilder` riceve una lista di argomenti, mai `sh -c` con stringhe concatenate; validare unità systemd, percorsi e ogni dato che diventa argomento.
- **Conferma**: le azioni che modificano il sistema sono esplicite e chiedono conferma nella TUI. La validazione deve comunque avvenire anche nel Manager.
- **Timeout e output**: ogni comando ha timeout e cap sull'output conservato; alla scadenza il sottoprocesso viene terminato.
- **Errori sudo**: l'assenza di autorizzazione deve essere mostrata come errore comprensibile, non come successo. Il riconoscimento basato sul testo di stderr è solo euristico e non deve essere il controllo di sicurezza: la sicurezza deriva dalla lista di comandi consentiti in sudoers e dagli argomenti validati.
- **Rete**: DriveBay non apre porte proprie; l'accesso remoto è la sessione SSH.

Esempio illustrativo di regole ristrette (adattare utente e percorsi all'host e verificare con `sudo -l`):

```sudoers
Cmnd_Alias DRIVEBAY = /usr/bin/systemctl restart nginx.service, \
                      /usr/bin/systemctl restart sshd.service
ricky ALL=(root) NOPASSWD: DRIVEBAY
```

Creare e validare il file con `visudo`, per esempio `sudo visudo -f /etc/sudoers.d/drivebay`. Non usare regole generiche come `systemctl *`.

## 10. Tecnologie

| Ambito | Scelta | Note |
| --- | --- | --- |
| Linguaggio | Java 25 (LTS) | `maven.compiler.release=25` |
| Build | Maven + Maven Wrapper | un solo progetto, `packaging` jar |
| Terminale | Lanterna 3.1.x | già usata in TRAM; la TUI nasconde Lanterna dietro propri widget, così si può cambiare |
| Metriche | OSHI | già usata in TRAM |
| JSON | Jackson | output strutturati e modelli; nessun codec di protocollo DriveBay |
| Concorrenza | Virtual thread (se utile) | task in background per UI e comandi bloccanti |
| Sottoprocessi | `ProcessBuilder` | usato esclusivamente da `ProcessCommandRunner`, con timeout e limite output |
| File | `java.nio.file` + `WatchService` | equivalente di `fswatch1` |
| D-Bus | dbus-java (opzionale, dopo) | all'inizio bastano comandi di sistema |
| SSH | `ssh` di sistema | avvia il jar in un terminale remoto con `-t` |
| Test | JUnit 5 (+ AssertJ, JaCoCo opzionali) | fake `CommandRunner` e output registrati |
| Qualità | Spotless (google-java-format), maven-enforcer | leggeri, aggiungibili in qualsiasi momento |
| Packaging | Maven Shade (fat jar), `jlink`, opzionale native-image | un solo artefatto da installare sul server |
| Logging | SLF4J | evitare diagnostica che sporca la schermata TUI |

### Funzioni del Java moderno e dove servono

| Funzione | Uso nel progetto |
| --- | --- |
| `record` | Dati immutabili delle feature (snapshot, informazioni servizio) |
| Virtual thread | Task bloccanti di comando o aggiornamenti UI, se necessari |
| Text block | Fixture JSON e output di comandi nei test |
| `var`, variabili anonime `_` | Codice meno verboso dove non toglie chiarezza |

Non servono, per ora: JPMS, structured concurrency, FFM API, framework come Spring, protocollo di rete applicativo.

## 11. Struttura del repository

Questa è la struttura di destinazione. I sorgenti attuali sono ancora nei moduli `protocol/`, `agent/` e `tui/`; non sono ancora stati spostati o rimossi.

```
DriveBay/
├─ pom.xml                         un solo progetto (packaging jar)
└─ src/main/java/com/ricky/drivebay/
   ├─ Main.java
   ├─ exec/
   │  ├─ CommandRunner.java
   │  ├─ ProcessCommandRunner.java
   │  └─ Sudo.java
   ├─ ui/                          Shell, Page e widget
   └─ feature/
      ├─ memory/                   MemorySnapshot, MemoryProvider, MemoryPage
      └─ services/                 ServiceInfo, ServiceManager, ServicesPage
```

Le funzionalità sono raggruppate per area; `exec/` è il confine condiviso per tutti i processi esterni. Non aggiungere un secondo meccanismo per invocare comandi dentro una feature.

## 12. Strategia di test

- **CommandRunner**: verificare argomenti, timeout, exit code, stdout/stderr, output sopra il limite e terminazione del processo usando comandi controllati.
- **Provider/Manager**: testare parsing degli output registrati (`systemctl`, `lsblk`, `ip`) e propagazione degli errori usando un `CommandRunner` fake.
- **UI**: testare ViewModel senza terminale e widget su uno schermo virtuale.
- **Privilegi**: testare che le letture non aggiungano `sudo` e che solo le azioni esplicitamente privilegiate usino `Sudo.asRoot(...)`.
- **Su Linux reale**: test manuali o in container per systemd e le altre sorgenti dipendenti dal sistema.
- **Build**: `mvn verify` sul singolo progetto.

## 13. Roadmap

| Fase | Contenuto | Risultato verificabile |
| --- | --- | --- |
| 0 | Backup della struttura attuale, conversione Maven a un solo modulo | Un jar avviabile con `ssh -t` |
| 1 | `Main`, `Shell`, `CommandRunner` e schermata minima | La TUI gira sul server come utente normale |
| 2 | Memoria con OSHI e pagina memoria | Mostra RAM e swap reali |
| 3 | Servizi con letture non privilegiate e restart selettivo tramite `sudo -n` | `sudoers` autorizza solo unità deliberate |
| 4 | Processi, Log, Storage, Rete, Utenti | Informazioni di sistema testabili con runner fake |
| 5 | File, Container, Aggiornamenti e altre feature | Operazioni esplicite e privilegi minimi |

## 14. Decisioni aperte

- **Distribuzione del jar**: copia manuale iniziale; valutare `jlink` o eseguibile nativo più avanti.
- **TUI**: Lanterna è la prima scelta; isolare i widget per poter valutare alternative.
- **Servizi: CLI o D-Bus**: iniziare con `systemctl`; introdurre D-Bus solo se serve.
- **Regole sudoers**: definire l'elenco minimo dei comandi/argomenti autorizzati per ciascun deployment.
- **Uso locale**: il programma può funzionare anche su una macchina locale se avviato in un terminale, ma il flusso primario è SSH verso il server.

## 15. Guida ai file e al flusso dei dati

Questa sezione descrive i file dell'architettura di destinazione. **La migrazione non è ancora stata eseguita**: i file Java presenti si trovano ancora in `protocol/` e `agent/`; `tui/` non ha ancora sorgenti Java. La tabella non va quindi letta come inventario dei file già esistenti.

### Ruolo di ogni classe prevista

| File Java | Tipo | Responsabilità e confine |
|---|---|---|
| `Main.java` | classe con `main` | Punto di composizione: crea `ProcessCommandRunner`, Provider/Manager e Shell, poi avvia la TUI. Non contiene la logica di ogni feature e non avvia il programma come root. |
| `exec/CommandRunner.java` | interfaccia + `Result` record | Contratto unico per eseguire una lista di argomenti con timeout. `Result` trasporta `exitCode`, `stdout`, `stderr`; `ok()` è una scorciatoia per `exitCode == 0`. I test forniscono implementazioni fake. |
| `exec/ProcessCommandRunner.java` | implementazione concreta | Crea processi con `ProcessBuilder`, chiude stdin, legge stdout/stderr in parallelo, applica timeout e limite alla quantità di output conservata. È l'unico punto ordinario che avvia comandi esterni. |
| `exec/Sudo.java` | utility senza stato | Prepara la lista `sudo -n -- <comando>`; non avvia il processo. L'autorizzazione effettiva è decisa da sudoers, non da questa classe. Un eventuale metodo `refused(...)` è solo classificazione UX euristica, mai un controllo di sicurezza. |
| `ui/Shell.java` | componente UI | Gestisce il ciclo Lanterna, il layout, la pagina attiva e le risorse UI. È il contenitore delle pagine e il proprietario del disegno del terminale. |
| `ui/Page.java` | classe base astratta | Comportamento comune e punti di estensione per titolo, comparsa/scomparsa, gestione input e rendering. Non esegue comandi di sistema direttamente. |
| `ui/widget/*` | widget | Componenti grafici riutilizzabili (tabelle, gauge, dialoghi, grafici); non conoscono OSHI, sudo o `ProcessBuilder`. |
| `feature/memory/MemorySnapshot.java` | `record` | Valori immutabili della memoria e unità esplicite (byte); modello presentabile e facile da verificare nei test. |
| `feature/memory/MemoryProvider.java` | classe concreta | Legge i dati memoria con OSHI e restituisce `MemorySnapshot`. Non esegue comandi privilegiati; per ora non si aggiunge un'interfaccia solo per questo singolo provider. |
| `feature/memory/MemoryPage.java` | Page | Mostra lo snapshot e orchestra l'aggiornamento periodico; non conosce i dettagli interni di OSHI. |
| `feature/services/ServiceInfo.java` | `record` | Rappresenta i campi di un servizio systemd usati dalla UI. Non avvia comandi. |
| `feature/services/ServiceManager.java` | manager | Usa `CommandRunner` per leggere e interpretare i servizi. Le letture usano il runner senza sudo; le singole azioni autorizzate usano `Sudo.asRoot(...)`. Valida l'unità prima di passarla come argomento e propaga errori espliciti. |
| `feature/services/ServicesPage.java` | Page | Visualizza servizi, raccoglie selezione/conferma e invoca il Manager; presenta all'utente errori di permesso o di comando. |

Non c'è una classe `Agent`, `Dispatcher`, `Channel`, `Feature`, `Transport`, `Session` o `MessageCodec` nell'architettura di destinazione: erano necessari al modello distribuito con protocollo applicativo e non servono quando TUI e accesso al sistema sono nello stesso processo.

### Esempio: lettura e riavvio di un servizio

Il Manager separa i comandi di sola lettura dalle azioni privilegiate:

```java
public String listJson() throws IOException, InterruptedException {
    var result = runner.run(List.of(
            "systemctl", "list-units", "--type=service", "--all",
            "--no-pager", "--output=json"), TIMEOUT);
    if (!result.ok()) throw new IOException("systemctl: " + result.stderr().strip());
    return result.stdout();
}

public void restart(String unit) throws IOException, InterruptedException {
    validateUnit(unit);
    var result = runner.run(Sudo.asRoot("systemctl", "restart", unit), TIMEOUT);
    if (!result.ok()) throw new IOException("restart fallito: " + result.stderr().strip());
}
```

La Page chiama `listJson()` per mostrare i servizi senza elevazione. Per un restart mostra una conferma, poi chiama `restart(unit)`. La validazione resta nel Manager, quindi non dipende dalla correttezza della UI. I percorsi dei binari e le regole sudoers vanno adattati alla distribuzione e verificati sul server: un test fake non dimostra che l'allowlist sudoers reale corrisponda.

### Flusso dei dati completo

```
Tastiera → Shell/Page → Provider o Manager → CommandRunner → sistema
             ↑                 │                 │
             └──── stato/dati ─┘                 └─ Result (exitCode, stdout, stderr)
```

1. L'utente si collega con `ssh -t` e avvia il jar. SSH fornisce il terminale; `Main` crea l'oggetto `Shell` e le dipendenze applicative.
2. La Shell mostra una Page. Per la memoria la Page richiede uno snapshot al Provider, che legge OSHI; per i servizi chiama il Manager.
3. Il Manager costruisce una lista di argomenti (mai una riga shell) e la passa a `CommandRunner` con un timeout.
4. Per una lettura, il runner esegue il comando come utente normale. Per un'azione privilegiata, il Manager valida prima i dati, poi passa a `Sudo.asRoot(...)`; il runner esegue `sudo -n -- ...`, che termina senza prompt se l'autorizzazione manca.
5. `ProcessCommandRunner` restituisce exit code e output, oppure segnala errori di avvio, timeout o lettura. Il Manager interpreta l'output e propaga gli errori senza convertirli in valori di successo.
6. La Page aggiorna il proprio stato/ViewModel e la Shell ridisegna sul terminale. Tutto avviene nello stesso processo remoto: non ci sono JSON Lines, canali o thread di trasporto.

### Sicurezza operativa di sudo

`sudo -n` impedisce il prompt ma **non limita da solo** cosa può fare il programma. L'allowlist sudoers è il controllo effettivo: autorizzare solo binari e argomenti specifici. Validare l'input prima che diventi un argomento e non usare regole con wildcard generiche. Messaggi di errore e codici di uscita possono variare tra versioni e locale, quindi il parsing di stderr per identificare un rifiuto di sudo deve restare un aiuto per l'utente, non una decisione di autorizzazione.

### Migrazione dalla struttura corrente

Prima di rimuovere `protocol/` e `agent/`, creare un branch di backup della struttura e del codice attuali, committare lì il contenuto e tornare al branch di lavoro. Non eliminare i moduli prima di avere verificato il backup. Poi:

1. Convertire il parent Maven in un progetto singolo con `packaging` jar e dipendenze TUI/OSHI/Jackson/SLF4J.
2. Spostare `MemorySnapshot` sotto `feature/memory/` e sostituire l'attuale interfaccia vuota `MemoryProvider` con la classe concreta che legge OSHI.
3. Portare la UI (per esempio quella di TRAM) nel progetto, iniziando dalla Shell e dalla pagina memoria.
4. Aggiungere `CommandRunner`, `ProcessCommandRunner` e `Sudo`, con test mirati del runner e un fake per i test dei Manager.
5. Aggiungere `ServiceManager` e `ServicesPage`; configurare sudoers solo per le azioni necessarie e verificate.

La separazione delle responsabilità resta: **Page presenta e raccoglie intenzioni; Provider/Manager fornisce dati e applica regole; `CommandRunner` esegue processi; `Sudo` prepara solo le invocazioni elevate.** È questo confine che permette di testare e, se in futuro servirà davvero, estrarre di nuovo l'accesso al sistema in un processo separato.
