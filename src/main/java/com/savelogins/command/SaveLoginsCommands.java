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
        
        // Send /login directly to server
        sendCommandToServer("/login " + password);
        
        source.sendFeedback(Component.literal("§aLogging in to §e" + serverId + "§a..."));
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
        
        // Send /register directly to server
        sendCommandToServer("/register " + password + " " + password);
        
        context.getSource().sendFeedback(Component.literal("§aPassword saved and sent to §e" + serverId));
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
     * Sends a chat message directly to the server. Works on ANY server.
     */
    private static void sendCommandToServer(String command) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            
            // Get the connection handler
            Object connection = null;
            java.lang.reflect.Field[] fields = mc.getClass().getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                field.setAccessible(true);
                Object value = field.get(mc);
                // Check for play handler
                String name = field.getName();
                if (name.contains("connection") || name.contains("handler")) {
                    if (value != null && value.getClass().getSimpleName().contains("ClientPlayNetHandler")) {
                        connection = value;
                        break;
                    }
                }
            }
            
            if (connection == null) {
                // Fallback - use chat screen
                openChatWithCommand(command);
                return;
            }
            
            // Find the sendChatMessage method
            java.lang.reflect.Method[] methods = connection.getClass().getDeclaredMethods();
            for (java.lang.reflect.Method method : methods) {
                method.setAccessible(true);
                if (method.getName().contains("sendChatMessage") || method.getName().equals("a")) {
                    // Try to call it
                    try {
                        method.invoke(connection, command);
                        return;
                    } catch (Exception e) {
                        // Try next method
                    }
                }
            }
            
            // Fallback to chat screen
            openChatWithCommand(command);
        } catch (Exception e) {
            // Fallback to chat screen
            openChatWithCommand(command);
        }
    }
    
    /**
     * Opens chat screen with command pre-filled. Works on ANY server.
     */
    private static void openChatWithCommand(String command) {
        try {
            // Find the ChatScreen class (obfuscated in 1.21.1)
            Class<?> chatScreenClass = Class.forName("net.minecraft.class_328");
            // Get constructor that takes String (initial message)
            var constructor = chatScreenClass.getConstructor(String.class);
            Object screen = constructor.newInstance(command);
            // Get Minecraft.setScreen(Screen) method (obfuscated)
            var setScreenMethod = net.minecraft.client.Minecraft.class.getMethod("method_1608", Class.forName("net.minecraft.class_418"));
            setScreenMethod.invoke(net.minecraft.client.Minecraft.getInstance(), screen);
        } catch (Exception e) {
            System.out.println("[SaveLogins] Chat open failed: " + e.getMessage());
        }
    }
}