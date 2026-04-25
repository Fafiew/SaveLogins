package com.savelogins;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Handles custom commands for the password manager.
 * Processes /alogin, /aregister, /al, /ar commands.
 */
public class CommandHandler {
    private final StorageManager storageManager;
    private final ServerTracker serverTracker;
    
    // Pattern to detect custom commands: /alogin, /aregister, /al, /ar (case insensitive)
    private static final Pattern CUSTOM_CMD_PATTERN = Pattern.compile(
        "^/(?:alogin|aregister|al|ar)(?:\\s+.*)?$", Pattern.CASE_INSENSITIVE);
    // Pattern to extract command and args
    private static final Pattern CMD_ARGS_PATTERN = Pattern.compile(
        "^/((?:alogin|aregister|al|ar))(?:\\s+(.*))?$", Pattern.CASE_INSENSITIVE);

    public CommandHandler(StorageManager storageManager, ServerTracker serverTracker) {
        this.storageManager = storageManager;
        this.serverTracker = serverTracker;
    }

    /**
     * Checks if a message is a custom command.
     */
    public boolean isCustomCommand(String message) {
        return CUSTOM_CMD_PATTERN.matcher(message.trim()).matches();
    }

    /**
     * Handles custom commands like /alogin, /aregister, /al, /ar.
     */
    public boolean handleCustomCommand(String command) {
        String trimmed = command.trim();
        var matcher = CMD_ARGS_PATTERN.matcher(trimmed);
        
        if (matcher.matches()) {
            String cmdName = matcher.group(1).toLowerCase();
            String args = matcher.group(2);
            return processCommand(cmdName, args);
        }
        
        return false;
    }

    /**
     * Map short commands to full commands
     */
    private String normalizeCommand(String cmd) {
        return switch (cmd.toLowerCase()) {
            case "al" -> "alogin";
            case "ar" -> "aregister";
            default -> cmd;
        };
    }

    private boolean processCommand(String command, String args) {
        String cmdName = normalizeCommand(command);

        switch (cmdName) {
            case "alogin":
                return handleLogin();
            case "aregister":
                return handleRegister(args);
            case "aremove":
                return handleRemove();
            case "alist":
                return handleList();
            case "ahelp":
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
            sendMessage("§cUse /aregister <password> to save your password first.");
            return true;
        }

        String password = passwordOpt.get();
        com.savelogins.mixin.ChatInvoker.sendChat("/login " + password);
        sendMessage("§aLogging in to §e" + serverId + "§a...");
        return true;
    }

    private boolean handleRegister(String args) {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            sendMessage("§cNot connected to a server!");
            return true;
        }

        if (args == null || args.isEmpty()) {
            sendMessage("§cUsage: /aregister <password>");
            return true;
        }

        String password = args.trim();
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
        sendMessage("§7/al §e- Auto-login with stored password");
        sendMessage("§7/ar <password> §e- Save password for current server");
        sendMessage("§7/aremove §e- Remove password for current server");
        sendMessage("§7/alist §e- List stored servers");
        sendMessage("§7/ahelp §e- Show this help");
        return true;
    }

    private void sendMessage(String message) {
        System.out.println("[SaveLogins] " + message);
    }
}