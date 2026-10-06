# Hybrid Multi-ATM PIN-Secured Auto Network System

An enterprise-grade, offline-first distributed ATM network implemented in Java. The system allows multiple independent physical ATM terminals to connect to a shared central MySQL banking server with automatic network failure detection, local journal-based fail-safe operation, SHA-256 PIN verification, and optimistic concurrency reconciliation upon reconnection.

---

## 🏛️ System Architecture

```text
                           ┌───────────────────────────┐
                           │     CENTRAL SERVER        │
                           │  MySQL Database (atm_db)  │
                           └─────────────┬─────────────┘
                                         │
                         LAN / Wi-Fi / Private Network
                                         │
            ┌────────────────────────────┼────────────────────────────┐
            ▼                            ▼                            ▼
     ┌─────────────┐              ┌─────────────┐              ┌─────────────┐
     │   ATM-01    │              │   ATM-02    │              │   ATM-03    │
     ├─────────────┤              ├─────────────┤              ├─────────────┤
     │  LVDS Store │              │  LVDS Store │              │  LVDS Store │
     │ Local Cache │              │ Local Cache │              │ Local Cache │
     │ Offline Log │              │ Offline Log │              │ Offline Log │
     └─────────────┘              └─────────────┘              └─────────────┘
```

The **Central MySQL Database** serves as the authoritative source of truth. Each physical ATM instance operates autonomously with a local **LVDS (Local Volatile Data Store)** snapshot directory:
- `local-lvds/ATM-01/`
- `local-lvds/ATM-02/`
- `local-lvds/ATM-03/`

---

## 🌟 Key Features

1. **Autonomous Multi-ATM Support:** Multiple ATM instances run on distinct client machines or separate ports, uniquely identifying themselves (e.g. `ATM-01`, `ATM-02`) with isolated local caches.
2. **Offline-First Fail-Safe Operation:** When the central network goes down, the ATM automatically switches to `OFFLINE` mode without interrupting the customer transaction experience.
3. **SHA-256 PIN Cryptographic Security:** Customer PINs are never stored or cached in plain text in local LVDS files. Authentication validates against salted/hashed SHA-256 digests.
4. **Optimistic Version Locking:** Accounts track a `version` field. When multiple ATMs or offline synchronizations execute, version checks prevent stale concurrent updates from overwriting balances.
5. **Automatic Reconnection & Reconciliation:** When network connectivity is restored, the `SynchronizationManager` automatically detects the online state, flushes offline journals, checks base versions, and commits to the central database or flags conflicts into `sync_conflicts`.
6. **Polymorphic Account Support:** 
   - **Savings Accounts:** Enforces strict minimum balance constraints (`minimum_balance`).
   - **Current Accounts:** Supports overdraft limits (`overdraft_limit`).
7. **Comprehensive Dual Consoles:**
   - **Customer ATM Console (`ATMConsoleUI`):** Balance enquiry, deposit, cash withdrawal, PIN validation, and mini-statements.
   - **Admin Management Console (`AdminConsoleUI`):** System status, network connectivity toggle, manual synchronization, conflict audit logs, and account inspection.

---

## 📁 Repository Structure

```text
├── README.md                                          # Root system documentation
├── .gitignore                                         # Git ignore configuration
└── FINAL CODE-TESTING PHASE/                          # Source deliverables & configurations
    ├── MULTI_ATM_SETUP.md                             # Step-by-step multi-node LAN deployment guide
    │
    ├── Core Source Code/                              # Models, Exceptions, & Infrastructure
    │   ├── Account.java, SavingsAccount.java, CurrentAccount.java
    │   ├── Customer.java, Transaction.java, OfflineTransaction.java
    │   ├── ATMConfig.java, DatabaseConnection.java, LVDS.java, NetworkManager.java
    │   └── Custom Exceptions (AccountNotFoundException, InvalidPINException, etc.)
    │
    ├── Data Access Layer (DAOs)/                      # Persistence & Database Access
    │   ├── AccountDAO.java                            # Central DB account CRUD & version updates
    │   ├── TransactionDAO.java                        # Transaction history & ledger entries
    │   ├── OfflineTransactionDAO.java                 # Central offline staging records
    │   └── UserDAO.java                               # Customer authentication & verification
    │
    ├── Synchronization & Service Layers/              # Business Logic & Offline Engine
    │   ├── AccountService.java                        # High-level account operations
    │   ├── AuthenticationService.java                 # PIN verification & hashing
    │   ├── TransactionService.java                    # Transaction handling (online/offline)
    │   ├── SynchronizationService.java                # Sync batch orchestration
    │   ├── SynchronizationManager.java                # Background sync & thread management
    │   └── ReconciliationStrategy.java                # Conflict detection & version reconciliation
    │
    ├── UI & Entry Point/                              # Terminal Interfaces & Main Launcher
    │   ├── Offatm.java                                # Application bootstrap & lifecycle manager
    │   ├── ATMConsoleUI.java                          # Interactive ATM terminal UI
    │   └── AdminConsoleUI.java                        # System administrator control panel
    │
    ├── Project Configuration & Database Setup/        # Build system & configurations
    │   ├── pom.xml                                    # Maven project definition (Java 21)
    │   ├── databaseschema.sql                         # MySQL database initialization script
    │   ├── config/
    │   │   └── atm.properties                         # Terminal ID & central DB connection config
    │   ├── local-lvds/                                # Local cached state & offline journal directories
    │   │   └── ATM-01/
    │   └── src/main/java/com/atm/                     # Full standard package-structured Java code
    │
    └── README Documentation/                          # Additional test scripts & execution notes
        ├── Bash.sh
        ├── README.md
        └── run-javac.bat
```

---

## 🗄️ Database Schema (`databaseschema.sql`)

The central database consists of 5 core tables:

| Table | Description |
|---|---|
| `customers` | Customer identities, profile details, and authentication hashes |
| `accounts` | Account numbers, account type (`SAVINGS`/`CURRENT`), balances, and `version` numbers |
| `transactions` | Central audit ledger recording every online completed transaction |
| `offline_transaction` | Staged offline transactions synchronized from ATMs |
| `sync_conflicts` | Audit records for reconciliation conflicts detected during synchronization |

---

## ⚙️ Configuration (`atm.properties`)

Located in `FINAL CODE-TESTING PHASE/Project Configuration & Database Setup/config/atm.properties`:

```properties
# Terminal Configuration
atm.id=ATM-01
atm.location=Main Branch Terminal 01

# Central MySQL Database Connection
central.db.host=127.0.0.1
central.db.port=3306
central.db.name=atm_db
central.db.user=atm_user
central.db.password=CHANGE_ME
```

> **Note for Multi-ATM Setup:** For each machine/instance, configure a unique `atm.id` (e.g. `ATM-01`, `ATM-02`). Point `central.db.host` to the IP address of the central server.

---

## 🚀 Getting Started

### Prerequisites
- **Java Development Kit (JDK):** Version 21 or higher
- **MySQL Server:** Version 8.0+
- **MySQL Connector/J:** 8.3.0 or higher
- *(Optional)* **Apache Maven:** Version 3.8+

### 1. Database Setup
Log in to MySQL and initialize the schema:
```bash
mysql -u root -p < "FINAL CODE-TESTING PHASE/Project Configuration & Database Setup/databaseschema.sql"
```

### 2. Build & Execution

#### Option A: Using Maven
Navigate to `FINAL CODE-TESTING PHASE/Project Configuration & Database Setup/`:
```bash
mvn clean compile
mvn exec:java
```

#### Option B: Using Windows Batch Script (`run-javac.bat`)
Double-click or run:
```cmd
"FINAL CODE-TESTING PHASE\README Documentation\run-javac.bat"
```

#### Option C: Manual Compilation with `javac`
```cmd
javac -encoding UTF-8 -d target\classes ^
  src\main\java\com\atm\infrastructure\*.java ^
  src\main\java\com\atm\model\*.java ^
  src\main\java\com\atm\exception\*.java ^
  src\main\java\com\atm\dao\*.java ^
  src\main\java\com\atm\service\*.java ^
  src\main\java\com\atm\synchronization\*.java ^
  src\main\java\com\atm\ui\*.java ^
  src\main\java\com\atm\main\*.java

java -cp "target\classes;lib\mysql-connector-j-8.3.0.jar" com.atm.main.Offatm
```

---

## 🧪 Demonstration & Testing Workflow

1. **Initial Online Sync:** Start the ATM terminal while connected to the MySQL database. The application pulls initial customer records and account snapshots into `local-lvds/<ATM-ID>/`.
2. **Perform Online Transactions:** Deposit or withdraw funds; verify balances update in MySQL in real-time.
3. **Simulate Network Failure:** Disconnect network cable/Wi-Fi or stop the MySQL service. The system automatically shifts to `OFFLINE` mode.
4. **Offline Banking:** Perform withdrawals/deposits within cached limits. Transactions are recorded in `local-lvds/<ATM-ID>/offline_transactions.log` with unique prefixes (`ATM-01-TX-...`).
5. **Restore Connectivity:** Re-enable the network or restart MySQL. The ATM re-establishes connection and triggers background synchronization, reconciling account versions and posting transactions to the central ledger.
