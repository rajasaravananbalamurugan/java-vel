package com.atm.infrastructure;

import com.atm.dao.AccountDAO;
import com.atm.dao.UserDAO;
import com.atm.model.Account;
import com.atm.model.CurrentAccount;
import com.atm.model.Customer;
import com.atm.model.OfflineTransaction;
import com.atm.model.SavingsAccount;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local Virtual Data Server (LVDS).
 *
 * Each ATM has its own local persistent directory:
 *   local-lvds/<ATM-ID>/
 *
 * The LVDS stores account snapshots and customer authentication material
 * needed for fail-safe offline operation. Offline transaction records are
 * stored by OfflineTransactionDAO in the same ATM-specific directory.
 */
public final class LVDS {
    private static final LVDS INSTANCE = new LVDS();

    private final Map<String, Account> localCache = new ConcurrentHashMap<>();
    private final Path rootDirectory;

    private LVDS() {
        rootDirectory = Path.of("local-lvds", sanitizePathPart(ATMConfig.getAtmId()));
        try {
            Files.createDirectories(rootDirectory.resolve("accounts"));
        } catch (IOException e) {
            System.err.println("[LVDS] Could not create local storage: " + e.getMessage());
        }
    }

    public static LVDS getInstance() {
        return INSTANCE;
    }

    public Path getRootDirectory() {
        return rootDirectory;
    }

    public synchronized Account getAccount(String accountNumber) throws IOException {
        Account cached = localCache.get(accountNumber);
        if (cached != null) {
            return cached;
        }

        Path file = accountFile(accountNumber);
        if (!Files.exists(file)) {
            return null;
        }

        Properties p = load(file);
        Account account = mapPropertiesToAccount(p);
        if (account != null) {
            localCache.put(account.getAccountNumber(), account);
        }
        return account;
    }

    public synchronized void updateLocalAccount(Account account) {
        if (account == null) return;

        localCache.put(account.getAccountNumber(), account);

        Properties p = new Properties();
        p.setProperty("accountNumber", account.getAccountNumber());
        p.setProperty("customerId", Integer.toString(account.getCustomerId()));
        p.setProperty("balance", Double.toString(account.getBalance()));
        p.setProperty("version", Integer.toString(account.getVersion()));
        p.setProperty("dailyLimit", Double.toString(account.getDailyLimit()));

        if (account instanceof SavingsAccount savings) {
            p.setProperty("accountType", "SAVINGS");
            p.setProperty("minimumBalance", Double.toString(savings.getMinimumBalance()));
        } else if (account instanceof CurrentAccount current) {
            p.setProperty("accountType", "CURRENT");
            p.setProperty("overdraftLimit", Double.toString(current.getOverdraftLimit()));
        }

        try {
            store(accountFile(account.getAccountNumber()), p);
        } catch (IOException e) {
            throw new IllegalStateException("LVDS local account write failed: " + e.getMessage(), e);
        }
    }

    /**
     * Refreshes the ATM's local snapshot from the central database.
     * This should run while the ATM is online, before relying on the LVDS
     * during a later outage.
     */
    public synchronized void refreshFromCentral() throws Exception {
        AccountDAO accountDAO = new AccountDAO();
        UserDAO userDAO = new UserDAO();

        List<Account> accounts = accountDAO.getAllAccounts();
        List<Customer> customers = userDAO.getAllCustomers();

        for (Account account : accounts) {
            updateLocalAccount(account);
        }

        for (Customer customer : customers) {
            cacheCustomer(customer);
        }

        System.out.println("[LVDS] Local snapshot refreshed for " + accounts.size()
                + " account(s) and " + customers.size() + " customer(s).");
    }

    /**
     * Caches only a SHA-256 PIN digest, never the plaintext PIN.
     */
    public synchronized void cacheCustomer(Customer customer) {
        if (customer == null) return;

        Properties customers = loadCustomers();
        String prefix = "customer." + customer.getCustomerId() + ".";
        customers.setProperty(prefix + "name", customer.getName());
        customers.setProperty(prefix + "pinHash", sha256(customer.getPin()));
        try {
            store(customerFile(), customers);
        } catch (IOException e) {
            throw new IllegalStateException("LVDS customer cache write failed: " + e.getMessage(), e);
        }
    }

    public synchronized boolean verifyCachedPIN(int customerId, String pin) {
        Properties customers = loadCustomers();
        String expected = customers.getProperty("customer." + customerId + ".pinHash");
        return expected != null && expected.equals(sha256(pin));
    }

    public synchronized String getCachedCustomerName(int customerId) {
        return loadCustomers().getProperty("customer." + customerId + ".name");
    }

    private Properties loadCustomers() {
        if (!Files.exists(customerFile())) {
            return new Properties();
        }
        try {
            return load(customerFile());
        } catch (IOException e) {
            return new Properties();
        }
    }

    private Path accountFile(String accountNumber) {
        return rootDirectory.resolve("accounts")
                .resolve(sanitizePathPart(accountNumber) + ".properties");
    }

    private Path customerFile() {
        return rootDirectory.resolve("customers.properties");
    }

    private static Properties load(Path path) throws IOException {
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        }
        return p;
    }

    private static void store(Path path, Properties p) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(
                temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            p.store(out, "ATM LVDS local data");
        }
        Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Account mapPropertiesToAccount(Properties p) {
        String number = p.getProperty("accountNumber");
        if (number == null) return null;

        int customerId = Integer.parseInt(p.getProperty("customerId", "0"));
        double balance = Double.parseDouble(p.getProperty("balance", "0"));
        int version = Integer.parseInt(p.getProperty("version", "1"));
        double dailyLimit = Double.parseDouble(p.getProperty("dailyLimit", "0"));
        String type = p.getProperty("accountType", "SAVINGS");

        if ("CURRENT".equalsIgnoreCase(type)) {
            double overdraft = Double.parseDouble(p.getProperty("overdraftLimit", "0"));
            return new CurrentAccount(number, customerId, balance, version, dailyLimit, overdraft);
        }

        double minimum = Double.parseDouble(p.getProperty("minimumBalance", "0"));
        return new SavingsAccount(number, customerId, balance, version, dailyLimit, minimum);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format(Locale.ROOT, "%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String sanitizePathPart(String value) {
        if (value == null || value.isBlank()) return "ATM-UNKNOWN";
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
