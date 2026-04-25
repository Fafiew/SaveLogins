package com.savelogins;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
     *
     * @param command The full command string
     * @return true if the command was handled by this handler
     */
    public boolean handleCustomCommand(String command) {
        String trimmed = command.trim();

        if (trimmed.startsWith("///")) {
            String actualCommand = trimmed.substring(3).trim();
            return processCommand(actualCommand);
        }

        return false;
    }

    /**
     * Processes the custom command.
     *
     * @param command The command without /// prefix
     * @return true if handled
     */
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

    /**
     * Handles the ///login command - auto-login with stored password.
     *
     * @return true if handled
     */
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
        sendChatCommand("/login " + password);
        sendMessage("§aLogging in to §e" + serverId + "§a...");
        return true;
    }

    /**
     * Handles the ///register command - manually save a password.
     *
     * @param arg The password argument
     * @return true if handled
     */
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

    /**
     * Handles the ///remove command - remove stored password.
     *
     * @return true if handled
     */
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

    /**
     * Handles the ///list command - list stored servers.
     *
     * @return true if handled
     */
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

    /**
     * Handles the ///help command.
     *
     * @return true if handled
     */
    private boolean handleHelp() {
        sendMessage("§eSaveLogins Commands:");
        sendMessage("§7///login §e- Auto-login with stored password");
        sendMessage("§7///register <password> §e- Save password for current server");
        sendMessage("§7///remove §e- Remove password for current server");
        sendMessage("§7///list §e- List stored servers");
        sendMessage("§7///help §e- Show this help");
        return true;
    }

    /**
     * Sends a chat message to the server.
     *
     * @param message The message to send
     */
    private void sendChatCommand(String command) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && client.player.connection != null) {
            client.player.connection.sendChat(command);
        }
    }

    /**
     * Sends a message to the player's chat.
     *
     * @param message The message (supports color codes)
     */
    private void sendMessage(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}