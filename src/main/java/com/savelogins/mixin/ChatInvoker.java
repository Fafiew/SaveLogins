package com.savelogins.mixin;

import net.minecraft.client.Minecraft;

/**
 * Helper class to send chat messages via reflection.
 */
public class ChatInvoker {
    
    /**
     * Send a chat message to the server.
     */
    public static void sendChat(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null) {
            // Try to send using the player entity method - use full reflection to find the correct method
            try {
                var methods = client.player.getClass().getMethods();
                for (var method : methods) {
                    if (method.getName().contains("sendChat") && method.getParameterCount() == 1) {
                        method.setAccessible(true);
                        method.invoke(client.player, message);
                        return;
                    }
                }
            } catch (Exception e) {
                // Try another approach
            }
            
            // Try sending through any network handler field
            trySendViaNetworkHandler(message);
        }
    }
    
    private static void trySendViaNetworkHandler(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        
        try {
            // Find all fields on player
            var fields = client.player.getClass().getDeclaredFields();
            for (var field : fields) {
                String typeName = field.getType().getSimpleName();
                if (typeName.contains("Connection") || 
                    typeName.contains("Handler") ||
                    typeName.contains("Network")) {
                    field.setAccessible(true);
                    Object handler = field.get(client.player);
                    if (handler != null) {
                        var methods = handler.getClass().getMethods();
                        for (var method : methods) {
                            if (method.getName().contains("sendChat")) {
                                method.setAccessible(true);
                                method.invoke(handler, message);
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[SaveLogins] Could not send message: " + e.getMessage());
        }
    }
}