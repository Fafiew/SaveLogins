package com.savelogins;

import net.minecraft.client.Minecraft;

/**
 * Tracks the current server connection information.
 */
public class ServerTracker {
    private String currentServer;

    public ServerTracker() {
    }

    /**
     * Updates the current server from the client state.
     * Should be called regularly to track server changes.
     */
    public void update() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                net.minecraft.client.multiplayer.ServerData serverData = client.getCurrentServer();
                if (serverData != null) {
                    this.currentServer = serverData.ip;
                }
            }
        } catch (Exception e) {
            // In multiplayer, the server info should be available
            this.currentServer = null;
        }
    }

    /**
     * Gets the current server identifier.
     *
     * @return The server IP/domain, or null if not connected
     */
    public String getCurrentServer() {
        return currentServer;
    }

    /**
     * Sets the current server explicitly.
     *
     * @param server The server identifier
     */
    public void setCurrentServer(String server) {
        this.currentServer = server;
    }

    /**
     * Clears the current server (disconnected).
     */
    public void clearServer() {
        this.currentServer = null;
    }
}