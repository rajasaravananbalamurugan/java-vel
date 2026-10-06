package com.atm.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Centralized runtime configuration for one ATM installation.
 *
 * Each ATM should have its own ATM_ID, while all ATMs point to the same
 * CENTRAL_DB_HOST / database when they are part of the same banking network.
 *
 * Priority:
 * 1. JVM system property (-D...)
 * 2. environment variable
 * 3. config/atm.properties
 * 4. built-in default
 */
public final class ATMConfig {
    private static final Properties FILE_PROPERTIES = new Properties();
    private static final Path CONFIG_PATH =
            Path.of("config", "atm.properties");

    static {
        loadProperties();
    }

    private ATMConfig() {}

    private static void loadProperties() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
            FILE_PROPERTIES.load(in);
        } catch (IOException e) {
            System.err.println("[CONFIG] Could not read " + CONFIG_PATH + ": " + e.getMessage());
        }
    }

    private static String get(String key, String envKey, String defaultValue) {
        String systemValue = System.getProperty(key);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue.trim();
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String fileValue = FILE_PROPERTIES.getProperty(key);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue.trim();
        }

        return defaultValue;
    }

    public static String getAtmId() {
        return get("atm.id", "ATM_ID", "ATM-01");
    }

    public static String getAtmLocation() {
        return get("atm.location", "ATM_LOCATION", "Demo Location");
    }

    public static String getCentralDbHost() {
        return get("central.db.host", "CENTRAL_DB_HOST", "127.0.0.1");
    }

    public static int getCentralDbPort() {
        return Integer.parseInt(get("central.db.port", "CENTRAL_DB_PORT", "3306"));
    }

    public static String getCentralDbName() {
        return get("central.db.name", "CENTRAL_DB_NAME", "atm_db");
    }

    public static String getCentralDbUser() {
        return get("central.db.user", "CENTRAL_DB_USER", "root");
    }

    public static String getCentralDbPassword() {
        return get("central.db.password", "CENTRAL_DB_PASSWORD", "AtmRoot@12345");
    }

    public static String getCentralJdbcUrl() {
        return "jdbc:mysql://" + getCentralDbHost() + ":" + getCentralDbPort()
                + "/" + getCentralDbName()
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&connectTimeout=2000"
                + "&socketTimeout=2000"
                + "&serverTimezone=UTC";
    }

    /**
     * Creates the config directory and a sample configuration file when it does
     * not already exist. Existing user configuration is never overwritten.
     */
    public static void ensureConfigFile() {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            String sample = """
                    # Unique identifier for this ATM.
                    # Give every physical ATM a different value: ATM-01, ATM-02, ATM-03...
                    atm.id=ATM-01

                    # Optional display/location label.
                    atm.location=Demo Location

                    # Hostname or IP address of the central MySQL server.
                    # For a multi-ATM network, do NOT use localhost on the ATM PCs.
                    # Example: central.db.host=192.168.1.100
                    central.db.host=127.0.0.1
                    central.db.port=3306
                    central.db.name=atm_db

                    # Use a dedicated database user in a real deployment.
                    central.db.user=root
                    central.db.password=AtmRoot@12345
                    """;

            Files.writeString(CONFIG_PATH, sample,
                    StandardOpenOption.CREATE_NEW);
        } catch (IOException ignored) {
            // The application can still run with built-in defaults.
        }
    }
}
