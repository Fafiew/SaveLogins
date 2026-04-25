package com.savelogins.command;

import com.savelogins.StorageManager;
import com.savelogins.ServerTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import java.lang.reflect.Method;

/**
 * Client-side commands for SaveLogins mod.
 * Uses direct network channel to send messages to server.
 */
public class SaveLoginsCommands {

    public static void register(StorageManager storage, ServerTracker serverTracker) {
        // Register network ready handler
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            System.out.println("[SaveLogins] Server connection ready");
        });
        
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            
            // /alogin - auto login
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("alogin")
                    .executes(context -> handleLogin(storage, serverTracker, context.getSource()))
            );
            
            // /al - alias
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("al")
                    .executes(context -> handleLogin(storage, serverTracker, context.getSource()))
            );
            
            // /ar - register
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ar")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("password", StringArgumentType.string())
                        .executes(context -> handleRegister(storage, serverTracker, context)))
            );
            
            // /aregister - full command
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("aregister")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("password", StringArgumentType.string())
                        .executes(context -> handleRegister(storage, serverTracker, context)))
            );
            
            // /aremove - remove stored password
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("aremove")
                    .executes(context -> handleRemove(storage, serverTracker, context.getSource()))
            );
            
            // /alist - list servers
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("alist")
                    .executes(context -> handleList(storage, context.getSource()))
            );
        });
    }
    
    private static int handleLogin(StorageManager storage, ServerTracker serverTracker, FabricClientCommandSource source) {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            source.sendFeedback(Component.literal("§cNot connected to a server!"));
            return 0;
        }
        
        var passwordOpt = storage.getPassword(serverId);
        if (passwordOpt.isEmpty()) {
            source.sendFeedback(Component.literal("§cNo password stored for §e" + serverId));
            source.sendFeedback(Component.literal("§cUse /ar <password> to save it first."));
            return 0;
        }
        
        String password = passwordOpt.get();
        
        // Send directly via network channel
        sendChatMessage("/login " + password);
        
        source.sendFeedback(Component.literal("§aLogin sent to §e" + serverId));
        return 1;
    }
    
    private static int handleRegister(StorageManager storage, ServerTracker serverTracker, 
                              com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> context) {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
            return 0;
        }
        
        String password = StringArgumentType.getString(context, "password");
        
        // Save password
        storage.savePassword(serverId, password);
        
        // Send /register directly
        sendChatMessage("/register " + password + " " + password);
        
        context.getSource().sendFeedback(Component.literal("§aPassword saved & sent to §e" + serverId));
        return 1;
    }
    
    private static int handleRemove(StorageManager storage, ServerTracker serverTracker, FabricClientCommandSource source) {
        String serverId = serverTracker.getCurrentServer();
        if (serverId == null) {
            source.sendFeedback(Component.literal("§cNot connected to a server!"));
            return 0;
        }
        
        storage.removePassword(serverId);
        source.sendFeedback(Component.literal("§aPassword removed for §e" + serverId));
        return 1;
    }
    
    private static int handleList(StorageManager storage, FabricClientCommandSource source) {
        var servers = storage.getStoredServers();
        if (servers.isEmpty()) {
            source.sendFeedback(Component.literal("§eNo passwords stored."));
            return 0;
        }
        
        source.sendFeedback(Component.literal("§eStored servers:"));
        for (String server : servers.keySet()) {
            source.sendFeedback(Component.literal("§e- " + server));
        }
        return 1;
    }
    
    /**
     * Sends a chat message directly to the server via network handler.
     * Uses ClientPlayNetworkHandler.sendChatMessage - proper 1.21.1+ API
     */
    private static void sendChatMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        
        mc.execute(() -> {
            sendDirect(mc, message);
        });
        System.out.println("[SaveLogins] Trying to send: " + message);
    }
    
    /**
     * Direct send using official API
     */
    private static void sendDirect(Minecraft mc, String message) {
        try {
            // Get network handler via reflection
            Object networkHandler = null;
            try {
                for (java.lang.reflect.Field f : mc.getClass().getDeclaredFields()) {
                    f.setAccessible(true);
                    if (f.getName().contains("network") || f.getName().contains("handler")) {
                        Object value = f.get(mc);
                        if (value != null && value.getClass().getSimpleName().contains("Play")) {
                            networkHandler = value;
                            break;
                        }
                    }
                }
            } catch (Exception e) { /* ignore */ }
            
            // Method 1: Try network handler
            if (networkHandler != null) {
                for (java.lang.reflect.Method m : networkHandler.getClass().getMethods()) {
                    if ((m.getName().equals("sendChatMessage") || m.getName().contains("method_43")) 
                        && m.getParameterCount() == 1) {
                        try {
                            m.setAccessible(true);
                            m.invoke(networkHandler, message);
                            System.out.println("[SaveLogins] Sent via handler: " + message);
                            return;
                        } catch (Exception e) { /* continue */ }
                    }
                }
            }
            
            // Method 2: Try via player
            var player = mc.player;
            if (player != null) {
                for (java.lang.reflect.Method m : player.getClass().getMethods()) {
                    if ((m.getName().equals("sendChatMessage") || m.getName().contains("method_43")) 
                        && m.getParameterCount() == 1) {
                        try {
                            m.setAccessible(true);
                            m.invoke(player, message);
                            System.out.println("[SaveLogins] Sent via player: " + message);
                            return;
                        } catch (Exception e) { /* continue */ }
                    }
                }
            }
            
            System.out.println("[SaveLogins] Could not send: " + message);
            
        } catch (Exception e) {
            System.out.println("[SaveLogins] Error: " + e.getMessage());
        }
    }
    
    /**
     * Gets network connection from Minecraft client
     */
    private static Connection getConnection(Minecraft mc) {
        try {
            // Look for field "connection" or "h"
            for (java.lang.reflect.Field f : mc.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                String name = f.getName();
                if (name.contains("connection") || name.equals("h")) {
                    Object value = f.get(mc);
                    if (value instanceof Connection) {
                        return (Connection) value;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}