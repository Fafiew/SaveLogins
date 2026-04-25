package com.savelogins;

import java.util.Optional;

/**
 * Handles custom commands for the password manager.
 * Processes ///login and ///register commands.
 */
public class CommandHandler {
    private final StorageManager storageManager;
    private final ServerTracker serverTracker;

    public CommandHandler(StorageManager storageManager, ServerTracker serverTracker) {
        this.storageManager = storageManager;
        this.serverTracker = serverTracker;
    }

    /**
     * Handles custom commands starting with ///.
     */
    public boolean handleCustomCommand(String command) {
        String trimmed = command.trim();

        if (trimmed.startsWith("///")) {
            String actualCommand = trimmed.substring(3).trim();
            return processCommand(actualCommand);
        }

        return false;
    }

    private boolean processCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();

        switch (cmdName) {
            case "login":
                return handleLogin();
            case "register":
                return handleRegister(parts.length > 1 ? parts[1] : null);
            case "remove":
                return handleRemove();
            case "list":
                return handleList();
            case "help":
                return handleHelp();
            default:
                sendMessage("Unknown command: " + cmdName);
                return false;
        }
    }

    private boolean handleLogin() {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            sendMessage("§cNot connected to a server!");
            return true;
        }

        Optional<String> passwordOpt = storageManager.getPassword(serverId);
        if (passwordOpt.isEmpty()) {
            sendMessage("§cNo password stored for §e" + serverId);
            sendMessage("§cUse ///register <password> to save your password first.");
            return true;
        }

        String password = passwordOpt.get();
        com.savelogins.mixin.ChatInvoker.sendChat("/login " + password);
        sendMessage("§aLogging in to §e" + serverId + "§a...");
        return true;
    }

    private boolean handleRegister(String arg) {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            sendMessage("§cNot connected to a server!");
            return true;
        }

        if (arg == null || arg.isEmpty()) {
            sendMessage("§cUsage: ///register <password>");
            return true;
        }

        String password = arg.trim();
        storageManager.savePassword(serverId, password);
        sendMessage("§aPassword saved for §e" + serverId);
        return true;
    }

    private boolean handleRemove() {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            sendMessage("§cNot connected to a server!");
            return true;
        }

        storageManager.removePassword(serverId);
        sendMessage("§aPassword removed for §e" + serverId);
        return true;
    }

    private boolean handleList() {
        var servers = storageManager.getStoredServers();
        if (servers.isEmpty()) {
            sendMessage("§eNo passwords stored.");
            return true;
        }

        sendMessage("§eStored servers:");
        for (String serverId : servers.keySet()) {
            sendMessage("§7- §e" + serverId);
        }
        return true;
    }

    private boolean handleHelp() {
        sendMessage("§eSaveLogins Commands:");
        sendMessage("§7///login §e- Auto-login with stored password");
        sendMessage("§7///register <password> §e- Save password for current server");
        sendMessage("§7///remove §e- Remove password for current server");
        sendMessage("§7///list §e- List stored servers");
        sendMessage("§7///help §e- Show this help");
        return true;
    }

    private void sendMessage(String message) {
        System.out.println("[SaveLogins] " + message);
    }
}