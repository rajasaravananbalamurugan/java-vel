# Hybrid / Multi-ATM Network Extension

This version adds **Modification 2: Multiple ATMs sharing one central banking database** while preserving PIN security and the automatic ONLINE/OFFLINE detection.

## Architecture

```text
                     CENTRAL SERVER
                 MySQL -> atm_db
                         |
               Network / Wi-Fi / LAN
              /            |             \
           ATM-01         ATM-02         ATM-03
             |              |              |
           LVDS-01        LVDS-02        LVDS-03
             |              |              |
      Offline Journal Offline Journal Offline Journal
```

The central database is the authoritative account record. Each physical ATM has its own local LVDS snapshot and local offline journal.

## ATM configuration

Edit:

`config/atm.properties`

For ATM 1:

```properties
atm.id=ATM-01
central.db.host=192.168.1.100
central.db.port=3306
central.db.name=atm_db
central.db.user=atm_user
central.db.password=CHANGE_ME
```

For ATM 2, use the same central server but a different ID:

```properties
atm.id=ATM-02
central.db.host=192.168.1.100
...
```

Never give two physical ATMs the same `atm.id`.

## Central server setup

1. Choose one computer as the central server.
2. Install MySQL Server and create `atm_db` using `databaseschema.sql`.
3. Make MySQL listen for remote clients on the LAN, using the standard MySQL server configuration for your installation.
4. Create a dedicated database user for the ATMs, for example:

```sql
CREATE USER 'atm_user'@'%' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE ON atm_db.* TO 'atm_user'@'%';
FLUSH PRIVILEGES;
```

5. Allow TCP port `3306` through the central server's firewall for your private LAN.
6. Run `ipconfig` on the central server and note its IPv4 address.
7. Put that IP address in every ATM's `central.db.host`.

Do not expose the MySQL port directly to the public Internet. This student project is intended for a controlled LAN/demo environment.

## How multi-ATM behavior works

### Online

```text
ATM-01 -> Central Server -> atm_db
ATM-02 -> Central Server -> atm_db
ATM-03 -> Central Server -> atm_db
```

All online ATMs see the same central account balances.

The Java application refreshes the current central account state before an online balance/transaction operation. Optimistic version checks reject a stale concurrent update instead of silently overwriting another ATM's change.

### Offline

Only the disconnected ATM falls back:

```text
ATM-01 -> OFFLINE -> LVDS-01 -> Offline Journal
ATM-02 -> ONLINE  -> Central Server
ATM-03 -> ONLINE  -> Central Server
```

The other ATMs can continue using the shared central database.

### Reconnection

```text
LVDS-01 / Offline Journal
          |
          v
Synchronization + Version Reconciliation
          |
          v
Central Server / atm_db
```

Each offline transaction ID is prefixed with the ATM ID so transaction origins remain distinguishable, for example:

`ATM-01-TX-a1b2c3d4`

## Local LVDS

Every ATM writes local state below:

```text
local-lvds/
  ATM-01/
    accounts/
    customers.properties
    offline_transactions.log
    sync_conflicts.log
  ATM-02/
    ...
```

The offline journal is local to that ATM. It is not written into the central database while the ATM is offline.

The LVDS customer cache stores a SHA-256 PIN digest rather than the plaintext PIN.

## Important demo requirement

The first time an ATM is started while online, it refreshes its LVDS snapshot from the central database. This gives that ATM a local account/customer snapshot for a later network outage.

For a clean demo, start each ATM once while connected to the central server before testing offline mode.

## Build without Maven

From:

`Project Configuration & Database Setup`

```cmd
javac -encoding UTF-8 -d target\classes src\main\java\com\atm\infrastructure\*.java src\main\java\com\atm\model\*.java src\main\java\com\atm\exception\*.java src\main\java\com\atm\dao\*.java src\main\java\com\atm\service\*.java src\main\java\com\atm\synchronization\*.java src\main\java\com\atm\ui\*.java src\main\java\com\atm\main\*.java
```

At runtime the MySQL Connector/J JAR must be on the classpath. For example, if you have it in `lib`:

```cmd
java -cp "target\classes;lib\mysql-connector-j-8.3.0.jar" com.atm.main.Offatm
```

If you use Maven, the existing `pom.xml` still declares MySQL Connector/J.

## What changed in Modification 2

- `localhost` is no longer hard-coded.
- Every ATM has a unique configurable ATM ID.
- The shared central MySQL host is configurable.
- Each ATM has its own persistent LVDS directory.
- Offline journal storage is local to the ATM.
- Offline login can use the ATM's cached, hashed PIN material.
- Online operations refresh central account state before processing.
- Optimistic account-version checking protects shared account updates from stale concurrent ATM data.
- Offline transaction IDs include the originating ATM ID.
