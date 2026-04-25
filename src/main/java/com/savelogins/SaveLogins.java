package com.savelogins;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
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
    
    // Pattern to match /register <password> <password>
    private static final Pattern REGISTER_PATTERN = Pattern.compile("^/register\\s+(\\S+)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    // Pattern to match /login <password>
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^/login\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);

    @Override
    public void onInitializeClient() {
        // Initialize storage
        storageManager = new StorageManager();
        storageManager.initialize();
        
        // Initialize server tracker
        serverTracker = new ServerTracker();
        
        // Register client-side commands
        com.savelogins.command.SaveLoginsCommands.register(storageManager, serverTracker);
        
        // Register chat handler for auto-capture
        registerChatHandler();
        
        LOGGER.info("SaveLogins mod initialized");
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
        
        // Check for /register command - auto-save password
        Matcher registerMatcher = REGISTER_PATTERN.matcher(trimmed);
        if (registerMatcher.matches()) {
            String serverId = serverTracker.getCurrentServer();
            if (serverId != null) {
                String password = registerMatcher.group(1);
                storageManager.savePassword(serverId, password);
            }
            return;
        }
        
        // Check for /login command - auto-save password too
        Matcher loginMatcher = LOGIN_PATTERN.matcher(trimmed);
        if (loginMatcher.matches()) {
            String serverId = serverTracker.getCurrentServer();
            if (serverId != null) {
                String password = loginMatcher.group(1);
                storageManager.savePassword(serverId, password);
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