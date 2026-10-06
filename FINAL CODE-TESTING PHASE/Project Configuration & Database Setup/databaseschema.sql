CREATE DATABASE IF NOT EXISTS atm_db;
USE atm_db;

DROP TABLE IF EXISTS sync_conflicts;
DROP TABLE IF EXISTS offline_transaction;
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS customers;

CREATE TABLE customers (
    customer_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    pin VARCHAR(100) NOT NULL
);

CREATE TABLE accounts (
    account_number VARCHAR(20) PRIMARY KEY,
    customer_id INT NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    balance DOUBLE NOT NULL,
    version INT NOT NULL DEFAULT 1,
    daily_limit DOUBLE NOT NULL,
    minimum_balance DOUBLE DEFAULT 0.0,
    overdraft_limit DOUBLE DEFAULT 0.0,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE TABLE transactions (
    transaction_id VARCHAR(50) PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DOUBLE NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    previous_balance DOUBLE NOT NULL,
    resulting_balance DOUBLE NOT NULL,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (account_number) REFERENCES accounts(account_number)
);

CREATE TABLE offline_transaction (
    transaction_id VARCHAR(50) PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DOUBLE NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    previous_balance DOUBLE NOT NULL,
    resulting_balance DOUBLE NOT NULL,
    base_version INT NOT NULL,
    sync_status VARCHAR(20) NOT NULL
);

CREATE TABLE sync_conflicts (
    conflict_id INT PRIMARY KEY AUTO_INCREMENT,
    transaction_id VARCHAR(50) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    expected_version INT NOT NULL,
    actual_version INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Seed Initial Test Data
INSERT INTO customers (customer_id, name, pin) VALUES (101, 'John Doe', '1234');
INSERT INTO customers (customer_id, name, pin) VALUES (102, 'Jane Smith', '5678');

INSERT INTO accounts (account_number, customer_id, account_type, balance, version, daily_limit, minimum_balance, overdraft_limit) 
VALUES ('ACC1001', 101, 'SAVINGS', 10000.0, 1, 5000.0, 500.0, 0.0);

INSERT INTO accounts (account_number, customer_id, account_type, balance, version, daily_limit, minimum_balance, overdraft_limit) 
VALUES ('ACC1002', 102, 'CURRENT', 20000.0, 1, 10000.0, 0.0, 2000.0);