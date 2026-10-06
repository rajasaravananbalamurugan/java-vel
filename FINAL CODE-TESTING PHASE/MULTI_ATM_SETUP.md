# Modification 2 — Multiple ATMs on One Central Server

## Target architecture

```text
                 CENTRAL SERVER PC
             MySQL Server -> atm_db
                      |
            LAN / Wi-Fi / Network
          /             |              \
       ATM-01          ATM-02          ATM-03
        LVDS-01         LVDS-02         LVDS-03
```

## Central server PC

1. Install and run MySQL Server.
2. Execute `Project Configuration & Database Setup/databaseschema.sql`.
3. Find the server PC IPv4 address:

```cmd
ipconfig
```

Example:

```text
IPv4 Address . . . : 192.168.1.100
```

4. For a controlled LAN/demo, create a dedicated database user:

```sql
CREATE USER 'atm_user'@'%' IDENTIFIED BY 'CHANGE_ME';
GRANT SELECT, INSERT, UPDATE ON atm_db.* TO 'atm_user'@'%';
FLUSH PRIVILEGES;
```

5. Configure MySQL to accept connections from the private LAN and allow TCP 3306 through the Windows firewall for the private network.

## Each ATM PC

Open:

`Project Configuration & Database Setup/config/atm.properties`

ATM-01:

```properties
atm.id=ATM-01
atm.location=Lab ATM 01
central.db.host=192.168.1.100
central.db.port=3306
central.db.name=atm_db
central.db.user=atm_user
central.db.password=CHANGE_ME
```

ATM-02:

```properties
atm.id=ATM-02
atm.location=Lab ATM 02
central.db.host=192.168.1.100
central.db.port=3306
central.db.name=atm_db
central.db.user=atm_user
central.db.password=CHANGE_ME
```

Only the `atm.id` and location should differ between ATM copies. The central database host should be the same.

## First startup

Start each ATM while the network and central server are available. The application pulls a snapshot of the central accounts and customers into that ATM's local LVDS.

Each ATM gets its own folder:

```text
local-lvds/ATM-01/
local-lvds/ATM-02/
local-lvds/ATM-03/
```

## Online behavior

```text
ATM-01 -> Central Server -> Shared atm_db
ATM-02 -> Central Server -> Shared atm_db
```

When one ATM performs an online deposit, the next online enquiry from another ATM reads the shared central state.

The application loads the latest account version before online transactions and uses optimistic version checking to prevent stale concurrent updates from silently overwriting another ATM's change.

## Offline behavior

If ATM-01 loses network/central-server connectivity:

```text
ATM-01 -> LVDS-01 -> local offline journal
ATM-02 -> Central Server
```

Only ATM-01 goes offline.

When ATM-01 reconnects, its pending local transactions are synchronized to the central server and reconciled using the account version stored when each offline transaction started.

## Important note

The current implementation keeps the **central MySQL database as the authoritative source of account state**. LVDS is the ATM-local fail-safe snapshot/journal.

This extension is intended for a controlled classroom/demo LAN, not for exposing a MySQL server directly to the public Internet.
