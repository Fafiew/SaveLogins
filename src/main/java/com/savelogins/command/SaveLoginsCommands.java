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
        
        // Open chat field with /login command so user just presses enter
        openChatWithCommand("/login " + password);
        
        source.sendFeedback(Component.literal("§aLogging in to §e" + serverId + "§a... §7(Press Enter)"));
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
        
        // Open chat field with /register command
        openChatWithCommand("/register " + password + " " + password);
        
        context.getSource().sendFeedback(Component.literal("§aPassword saved for §e" + serverId));
        context.getSource().sendFeedback(Component.literal("§7Type §e/register§r§7 then press Enter"));

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
     * Opens chat with command pre-filled and focuses it. 
     * Uses a reliable approach that works on most servers.
     */
    private static void openChatWithCommand(String command) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            
            // Method 1: Open ChatScreen with text
            Class<?> chatScreenClass = Class.forName("net.minecraft.class_328");
            var constructor = chatScreenClass.getConstructor(String.class);
            Object screen = constructor.newInstance(command);
            var setScreenMethod = net.minecraft.client.Minecraft.class.getMethod("method_1608", 
                Class.forName("net.minecraft.class_418"));
            setScreenMethod.invoke(mc, screen);
            
            // Try to press Enter automatically after a short delay using scheduled callback
            // But for now just show the chat with text
        } catch (Exception e) {
            System.out.println("[SaveLogins] Chat open failed: " + e.getMessage());
        }
    }
    
    /**
     * Alt method: Use Keyboard and screen to send chat
     */
    private static void sendChatDirect(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null) return;
            
            // Use player .sendChat() method which sends to server
            // Try using reflection on the player object
            var player = mc.player;
            
            // Find sendChat or a method to send message
            for (java.lang.reflect.Method m : player.getClass().getDeclaredMethods()) {
                m.setAccessible(true);
                String name = m.getName();
                // Look for chat sending method
                if (name.contains("sendChat") || name.equals("method_4324")) {
                    try {
                        if (name.contains("String")) {
                            m.invoke(player, message);
                            return;
                        }
                    } catch (Exception e) {
                        // Continue trying
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[SaveLogins] Direct send failed: " + e.getMessage());
        }
    }
}