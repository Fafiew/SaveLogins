package com.savelogins;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main mod class for SaveLogins - Client-side password manager.
 * Handles chat interception and automatic password management.
 */
public class SaveLogins implements ClientModInitializer {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    private StorageManager storageManager;
    private ServerTracker serverTracker;
    private Minecraft client;
    
    // Pattern to match /register <password> <password>
    private static final Pattern REGISTER_PATTERN = Pattern.compile("^/register\\s+(\\S+)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    // Pattern to match /login <password>
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^/login\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);

    @Override
    public void onInitializeClient() {
        // Get Minecraft client instance
        client = Minecraft.getInstance();
        
        // Initialize storage
        storageManager = new StorageManager();
        storageManager.initialize();
        
        // Initialize server tracker
        serverTracker = new ServerTracker();
        
        // Register client-side commands
        com.savelogins.command.SaveLoginsCommands.register(storageManager, serverTracker);
        
        // Register chat handler for auto-capture
        registerChatHandler();
        
        // Show mod loaded message
        sendMessage("§a[SaveLogins] §eFafaHima's §bPassword Manager §aloaded! §7(1.0.4)");
        LOGGER.info("SaveLogins mod by FafaHima initialized");
    }

    /**
     * Send a message to the player's chat
     * Note: Using LOGGER for now as sendMessage API changed in 1.21.x
     */
    private void sendMessage(String text) {
        // Log to console - chat messages during init may not work
        LOGGER.info("[SaveLogins] " + text.replace("§", ""));
    }
    
    /**
     * Send a message to console/log
     */
    private void log(String text) {
        LOGGER.info(text);
    }

    /**
     * Registers the chat message handler for auto-capturing passwords.
     */
    private void registerChatHandler() {
        // Register for chat message events
        ClientSendMessageEvents.CHAT.register((message) -> {
            processChatMessage(message);
        });
        
        // Also register for command events
        ClientSendMessageEvents.COMMAND.register((message) -> {
            processChatMessage("/" + message);
        });
    }

    /**
     * Processes an outgoing chat message to auto-capture passwords.
     */
    private void processChatMessage(String message) {
        String trimmed = message.trim();
        
        // Update server tracker
        serverTracker.update();
        String serverId = serverTracker.getCurrentServer();
        
        // Check for /register command - auto-save password
        Matcher registerMatcher = REGISTER_PATTERN.matcher(trimmed);
        if (registerMatcher.matches()) {
            if (serverId != null) {
                String password = registerMatcher.group(1);
                storageManager.savePassword(serverId, password);
                sendMessage("§a[SaveLogins] §bPassword §asaved for §e" + serverId);
                log("Password saved for server: " + serverId);
            } else {
                sendMessage("§c[SaveLogins] Not connected to a server!");
            }
            return;
        }
        
        // Check for /login command - auto-save password too
        Matcher loginMatcher = LOGIN_PATTERN.matcher(trimmed);
        if (loginMatcher.matches()) {
            if (serverId != null) {
                String password = loginMatcher.group(1);
                storageManager.savePassword(serverId, password);
                sendMessage("§a[SaveLogins] §bLogin password §asaved for §e" + serverId);
                log("Login password saved for server: " + serverId);
            }
        }
    }

    /**
     * Gets the storage manager instance.
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * Gets the server tracker instance.
     */
    public ServerTracker getServerTracker() {
        return serverTracker;
    }
}