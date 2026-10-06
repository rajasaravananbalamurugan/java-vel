package com.atm.infrastructure;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Detects the real network state of the ATM.
 *
 * ONLINE  = a usable physical network interface exists AND the central
 *           database server can be reached.
 * OFFLINE = no usable network interface exists OR the central database
 *           server cannot be reached.
 *
 * No ping command or packet-loss test is used.
 */
public final class NetworkManager {
    private NetworkManager() {
        // Utility class.
    }

    public static boolean isOnline() {
        if (!isNetworkAvailable()) {
            return false;
        }
        return isCentralServerReachable();
    }

    /**
     * Checks whether the operating system currently has a usable
     * non-loopback, non-virtual network interface (Wi-Fi/Ethernet/etc.).
     */
    public static boolean isNetworkAvailable() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return false;
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                if (!networkInterface.isUp()
                        || networkInterface.isLoopback()
                        || networkInterface.isVirtual()
                        || isLikelyVirtualAdapter(networkInterface.getName(), networkInterface.getDisplayName())) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (!address.isLoopbackAddress() && !address.isAnyLocalAddress()) {
                        return true;
                    }
                }
            }
        } catch (SocketException e) {
            return false;
        }

        return false;
    }

    /**
     * Checks the actual central database connection.
     * This is a JDBC connection check, not a ping test.
     */
    public static boolean isCentralServerReachable() {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns the complete status block displayed by the ATM.
     */
    public static String getStatusReport() {
        boolean networkAvailable = isNetworkAvailable();

        if (!networkAvailable) {
            return buildStatusReport(
                    "OFFLINE",
                    "Unavailable",
                    "Disconnected",
                    "OFFLINE",
                    "ACTIVE",
                    "Offline Transactions Enabled");
        }

        boolean serverReachable = isCentralServerReachable();

        if (serverReachable) {
            return buildStatusReport(
                    "ONLINE",
                    "Available",
                    "Connected",
                    "ONLINE",
                    "NOT REQUIRED",
                    "Real-Time Transactions Enabled");
        }

        return buildStatusReport(
                "OFFLINE",
                "Available",
                "Disconnected",
                "OFFLINE",
                "ACTIVE",
                "Offline Transactions Enabled");
    }

    private static String buildStatusReport(
            String status,
            String connection,
            String server,
            String atmMode,
            String lvds,
            String operationMode) {

        StringBuilder report = new StringBuilder();
        report.append("\n========================================\n");
        report.append("          NETWORK STATUS\n");
        report.append("========================================\n\n");
        report.append("📶 Status        : ").append(status).append('\n');
        report.append("   Connection    : ").append(connection).append('\n');
        report.append("   Server        : ").append(server).append('\n');
        report.append("   ATM Mode      : ").append(atmMode).append('\n');

        if ("ACTIVE".equals(lvds)) {
            report.append("   LVDS          : ACTIVE\n");
        }

        report.append("   Mode          : ").append(operationMode).append('\n');
        report.append("========================================\n");
        return report.toString();
    }

    private static boolean isLikelyVirtualAdapter(String name, String displayName) {
        String text = ((name == null ? "" : name) + " "
                + (displayName == null ? "" : displayName)).toLowerCase(Locale.ROOT);

        String[] virtualKeywords = {
                "virtual", "vmware", "hyper-v", "hyperv", "veth",
                "docker", "wsl", "loopback", "tailscale", "zerotier"
        };

        for (String keyword : virtualKeywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
