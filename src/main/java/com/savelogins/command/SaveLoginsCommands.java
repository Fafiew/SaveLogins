package com.savelogins.command;

import com.savelogins.StorageManager;
import com.savelogins.ServerTracker;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Client-side commands for SaveLogins mod.
 */
public class SaveLoginsCommands {

    public static void register(StorageManager storage, ServerTracker serverTracker) {
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
            
            // /aregister <password> - using string() for password
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("aregister")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("password", StringArgumentType.string())
                        .executes(context -> handleRegister(storage, serverTracker, context))
                    )
            );
            
            // /ar - alias
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ar")
                    .then(net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument("password", StringArgumentType.string())
                        .executes(context -> handleRegister(storage, serverTracker, context))
                    )
            );
            
            // /aremove
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("aremove")
                    .executes(context -> handleRemove(storage, serverTracker, context.getSource()))
            );
            
            // /alist
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("alist")
                    .executes(context -> handleList(storage, context.getSource()))
            );
            
            // /ahelp
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal("ahelp")
                    .executes(context -> handleHelp(context.getSource()))
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
        
        // Send /login command directly to server
        sendCommandToServer("/login " + password);
        
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
        
        String password = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "password");
        
        // Save password
        storage.savePassword(serverId, password);
        
        // Send /register command directly to server
        sendCommandToServer("/register " + password + " " + password);
        
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
        for (String serverId : servers.keySet()) {
            source.sendFeedback(Component.literal("§7- §e" + serverId));
        }
        return 1;
    }
    
    private static int handleHelp(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("§e§lSaveLogins Commands:"));
        source.sendFeedback(Component.literal("§7/al §e- Auto-login (opens chat)"));
        source.sendFeedback(Component.literal("§7/ar <pass> §e- Save password"));
        source.sendFeedback(Component.literal("§7/aremove §e- Delete password"));
        source.sendFeedback(Component.literal("§7/alist §e- List servers"));
        return 1;
    }
    
    /**
     * Sends a chat message directly to the server as the player.
     * Uses the network handler to send the message.
     */
    private static void sendCommandToServer(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            
            // Method 1: Get connection from minecraft client
            Object connection = getConnection(mc);
            
            if (connection != null) {
                // Try to send chat message through connection
                if (sendViaConnection(connection, message)) {
                    return;
                }
            }
            
            // Method 2: Try using player object
            if (mc.player != null) {
                if (sendViaPlayer(mc.player, message)) {
                    return;
                }
            }
            
            System.out.println("[SaveLogins] All send methods failed, using chat screen");
            openChatWithCommand(message);
            
        } catch (Exception e) {
            System.out.println("[SaveLogins] Send failed: " + e.getMessage());
            openChatWithCommand(message);
        }
    }
    
    /**
     * Gets the network connection from Minecraft client
     */
    private static Object getConnection(Minecraft mc) {
        // Look for the connection field
        for (java.lang.reflect.Field f : mc.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object value = f.get(mc);
                String name = f.getName();
                // Check for play handler / connection
                if (name.contains("connection") || name.contains("handler")) {
                    if (value != null) {
                        String className = value.getClass().getSimpleName();
                        if (className.contains("Connection") || className.contains("Handler")) {
                            return value;
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        return null;
    }
    
    /**
     * Tries to send chat via the network connection
     */
    private static boolean sendViaConnection(Object conn, String message) {
        // Get all methods and try them
        for (java.lang.reflect.Method m : conn.getClass().getDeclaredMethods()) {
            m.setAccessible(true);
            String name = m.getName();
            
            // Look for chat sending methods
            if ((name.contains("sendChat") || name.contains("sendMessage") || name.equals("a")) && m.getParameterCount() == 1) {
                try {
                    // Try calling with String parameter
                    m.invoke(conn, message);
                    return true;
                } catch (Exception e) {
                    // Try next
                }
            }
            
            // Try methods with 2 parameters
            if (name.contains("send") && m.getParameterCount() == 2) {
                try {
                    m.invoke(conn, message, null);
                    return true;
                } catch (Exception e) {
                    // Try next
                }
            }
        }
        return false;
    }
    
    /**
     * Tries to send chat via the player object
     */
    private static boolean sendViaPlayer(Object playerObj, String message) {
        // Player has methods to send chat
        for (java.lang.reflect.Method m : playerObj.getClass().getDeclaredMethods()) {
            m.setAccessible(true);
            String name = m.getName();
            
            // Send chat method
            if ((name.contains("sendChat") || name.contains("method_4324") || name.equals("a")) && m.getParameterCount() == 1) {
                try {
                    m.invoke(playerObj, message);
                    return true;
                } catch (Exception e) {
                    // Try next
                }
            }
            
            // Try method with 2 params
            if (name.contains("send") && m.getParameterCount() == 2) {
                try {
                    m.invoke(playerObj, message, null);
                    return true;
                } catch (Exception e) {
                    // Try next
                }
            }
        }
        return false;
    }
    
    /**
     * Opens chat with command pre-filled
     */
    private static void openChatWithCommand(String command) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            
            Class<?> chatScreenClass = Class.forName("net.minecraft.class_328");
            var constructor = chatScreenClass.getConstructor(String.class);
            Object screen = constructor.newInstance(command);
            var setScreenMethod = net.minecraft.client.Minecraft.class.getMethod("method_1608", 
                Class.forName("net.minecraft.class_418"));
            setScreenMethod.invoke(mc, screen);
        } catch (Exception e) {
            System.out.println("[SaveLogins] Chat open failed: " + e.getMessage());
        }
    }
}