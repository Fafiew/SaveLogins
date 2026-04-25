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
    private static SaveLogins instance;
    
    private StorageManager storageManager;
    private ServerTracker serverTracker;
    private CommandHandler commandHandler;
    
    // Pattern to match /register <password> <password>
    private static final Pattern REGISTER_PATTERN = Pattern.compile("^/register\\s+(\\S+)\\s+(\\S+)$", Pattern.CASE_INSENSITIVE);
    
    private boolean processingCommand = false;

    @Override
    public void onInitializeClient() {
        instance = this;
        
        // Initialize storage
        storageManager = new StorageManager();
        storageManager.initialize();
        
        // Initialize server tracker
        serverTracker = new ServerTracker();
        
        // Initialize command handler
        commandHandler = new CommandHandler(storageManager, serverTracker);
        
        // Register for chat message events
        registerChatHandler();
        
        LOGGER.info("SaveLogins mod initialized");
    }

    /**
     * Registers the chat message handler.
     */
    private void registerChatHandler() {
        // Register for chat message events - single String parameter
        ClientSendMessageEvents.CHAT.register((message) -> {
            if (processingCommand) {
                return;
            }
            
            processChatMessage(message);
        });
        
        // Also register for command events
        ClientSendMessageEvents.COMMAND.register((message) -> {
            if (processingCommand) {
                return;
            }
            
            processChatMessage("/" + message);
        });
    }

    /**
     * Processes an outgoing chat message.
     *
     * @param message The chat message
     */
    private void processChatMessage(String message) {
        String trimmed = message.trim();
        
        // Check for custom commands first
        if (trimmed.startsWith("///")) {
            processingCommand = true;
            boolean handled = commandHandler.handleCustomCommand(trimmed);
            processingCommand = false;
            
            if (handled) {
                return;
            }
        }
        
        // Update server tracker
        serverTracker.update();
        
        // Check for /register command - auto-save password
        Matcher registerMatcher = REGISTER_PATTERN.matcher(trimmed);
        if (registerMatcher.matches()) {
            String serverId = serverTracker.getCurrentServer();
            if (serverId != null) {
                String password = registerMatcher.group(1);
                storageManager.savePassword(serverId, password);
                // Feedback shown via command confirmation
            }
            return;
        }
    }

    /**
     * Gets the storage manager instance.
     *
     * @return The storage manager
     */
    public StorageManager getStorageManager() {
        return storageManager;
    }

    /**
     * Gets the server tracker instance.
     *
     * @return The server tracker
     */
    public ServerTracker getServerTracker() {
        return serverTracker;
    }
}