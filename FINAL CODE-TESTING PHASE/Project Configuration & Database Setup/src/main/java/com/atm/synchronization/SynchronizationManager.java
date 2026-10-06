package com.atm.synchronization;

import com.atm.infrastructure.ATMConfig;
import com.atm.infrastructure.LVDS;
import com.atm.infrastructure.NetworkManager;
import com.atm.service.SynchronizationService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SynchronizationManager {
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2);
    private final SynchronizationService syncService =
            new SynchronizationService();
    private final LVDS lvds = LVDS.getInstance();
    private volatile boolean lastKnownState;

    public SynchronizationManager() {
        lastKnownState = NetworkManager.isOnline();
    }

    /**
     * Pull the shared central state into this ATM's local snapshot when
     * starting online. The snapshot is what makes later offline operation
     * possible on this particular ATM.
     */
    public void initializeLocalSnapshot() {
        if (!NetworkManager.isOnline()) {
            return;
        }

        try {
            lvds.refreshFromCentral();
        } catch (Exception e) {
            System.err.println(
                    "[LVDS] Initial central snapshot unavailable: " + e.getMessage());
        }
    }

    public void startBackgroundTasks() {
        // Periodic synchronization while the central server is reachable.
        scheduler.scheduleAtFixedRate(() -> {
            if (NetworkManager.isOnline()) {
                syncService.synchronize();
            }
        }, 10, 30, TimeUnit.SECONDS);

        // Network state monitor. This prints only when the state actually
        // changes; it does not spam the console during normal operation.
        scheduler.scheduleAtFixedRate(() -> {
            boolean currentState = NetworkManager.isOnline();

            if (lastKnownState && !currentState) {
                System.out.println(
                        "\n[NETWORK MONITOR] " + ATMConfig.getAtmId()
                                + " lost access to the central server.");
                System.out.println("[ATM MODE] Switching to OFFLINE mode.");
                System.out.println("[LVDS] Activated for this ATM.");
            } else if (!lastKnownState && currentState) {
                System.out.println(
                        "\n[NETWORK MONITOR] " + ATMConfig.getAtmId()
                                + " reconnected to the central server.");
                System.out.println("[ATM MODE] Switching to ONLINE mode.");
                System.out.println("[SYNC ENGINE] Checking offline transactions...");

                syncService.synchronize();

                try {
                    lvds.refreshFromCentral();
                } catch (Exception e) {
                    System.err.println(
                            "[LVDS] Central snapshot refresh failed: "
                                    + e.getMessage());
                }
            }

            lastKnownState = currentState;
        }, 2, 2, TimeUnit.SECONDS);
    }

    public void triggerManualSync() {
        if (NetworkManager.isOnline()) {
            syncService.synchronize();
        } else {
            System.err.println(
                    "Cannot trigger sync: Central server is unreachable.");
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
