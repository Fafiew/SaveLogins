package com.savelogins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.savelogins.StorageManager;
import com.savelogins.ServerTracker;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Registers client-side commands for SaveLogins mod.
 */
public class SaveLoginsCommands {

    public static void register(StorageManager storage, ServerTracker serverTracker) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerCommands(dispatcher, storage, serverTracker);
        });
    }
    
    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, 
                                         StorageManager storage, ServerTracker serverTracker) {
        
        // /alogin command
        dispatcher.register(
            ClientCommandManager.literal("alogin")
                .executes(context -> {
                    String serverId = serverTracker.getCurrentServer();
                    if (serverId == null) {
                        context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
                        return 0;
                    }
                    
                    Optional<String> passwordOpt = storage.getPassword(serverId);
                    if (passwordOpt.isEmpty()) {
                        context.getSource().sendFeedback(Component.literal("§cNo password stored for §e" + serverId));
                        context.getSource().sendFeedback(Component.literal("§cUse /aregister <password> to save your password first."));
                        return 0;
                    }
                    
                    String password = passwordOpt.get();
                    sendChat("/login " + password);
                    context.getSource().sendFeedback(Component.literal("§aLogging in to §e" + serverId + "§a..."));
                    return 1;
                })
        );
        
        // /al alias
        dispatcher.register(
            ClientCommandManager.literal("al")
                .executes(context -> {
                    String serverId = serverTracker.getCurrentServer();
                    if (serverId == null) {
                        context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
                        return 0;
                    }
                    
                    Optional<String> passwordOpt = storage.getPassword(serverId);
                    if (passwordOpt.isEmpty()) {
                        context.getSource().sendFeedback(Component.literal("§cNo password stored for §e" + serverId));
                        context.getSource().sendFeedback(Component.literal("§cUse /aregister <password> to save your password first."));
                        return 0;
                    }
                    
                    String password = passwordOpt.get();
                    sendChat("/login " + password);
                    context.getSource().sendFeedback(Component.literal("§aLogging in to §e" + serverId + "§a..."));
                    return 1;
                })
        );
        
        // /aregister <password>
        dispatcher.register(
            ClientCommandManager.literal("aregister")
                .then(ClientCommandManager.argument("password", StringArgumentType.word())
                    .executes(context -> {
                        String serverId = serverTracker.getCurrentServer();
                        if (serverId == null) {
                            context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
                            return 0;
                        }
                        
                        String password = StringArgumentType.getString(context, "password");
                        storage.savePassword(serverId, password);
                        context.getSource().sendFeedback(Component.literal("§aPassword saved for §e" + serverId));
                        return 1;
                    })
                )
        );
        
        // /ar alias
        dispatcher.register(
            ClientCommandManager.literal("ar")
                .then(ClientCommandManager.argument("password", StringArgumentType.word())
                    .executes(context -> {
                        String serverId = serverTracker.getCurrentServer();
                        if (serverId == null) {
                            context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
                            return 0;
                        }
                        
                        String password = StringArgumentType.getString(context, "password");
                        storage.savePassword(serverId, password);
                        context.getSource().sendFeedback(Component.literal("§aPassword saved for §e" + serverId));
                        return 1;
                    })
                )
        );
        
        // /aremove
        dispatcher.register(
            ClientCommandManager.literal("aremove")
                .executes(context -> {
                    String serverId = serverTracker.getCurrentServer();
                    if (serverId == null) {
                        context.getSource().sendFeedback(Component.literal("§cNot connected to a server!"));
                        return 0;
                    }
                    
                    storage.removePassword(serverId);
                    context.getSource().sendFeedback(Component.literal("§aPassword removed for §e" + serverId));
                    return 1;
                })
        );
        
        // /alist
        dispatcher.register(
            ClientCommandManager.literal("alist")
                .executes(context -> {
                    var servers = storage.getStoredServers();
                    if (servers.isEmpty()) {
                        context.getSource().sendFeedback(Component.literal("§eNo passwords stored."));
                        return 0;
                    }
                    
                    context.getSource().sendFeedback(Component.literal("§eStored servers:"));
                    for (String serverId : servers.keySet()) {
                        context.getSource().sendFeedback(Component.literal("§7- §e" + serverId));
                    }
                    return 1;
                })
        );
        
        // /ahelp
        dispatcher.register(
            ClientCommandManager.literal("ahelp")
                .executes(context -> {
                    context.getSource().sendFeedback(Component.literal("§eSaveLogins Commands:"));
                    context.getSource().sendFeedback(Component.literal("§7/al §e- Auto-login with stored password"));
                    context.getSource().sendFeedback(Component.literal("§7/ar <password> §e- Save password for current server"));
                    context.getSource().sendFeedback(Component.literal("§7/aremove §e- Remove password for current server"));
                    context.getSource().sendFeedback(Component.literal("§7/alist §e- List stored servers"));
                    return 1;
                })
        );
    }
    
    private static void sendChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            try {
                // Find connection field on player (field_3728 in yarn 1.21.11)
                var connField = mc.player.getClass().getDeclaredField("field_3728");
                connField.setAccessible(true);
                Object connection = connField.get(mc.player);
                if (connection != null) {
                    // Find the sendPacket or send method
                    for (var method : connection.getClass().getMethods()) {
                        if (method.getName().equals("sendPacket") || method.getName().contains("send")) {
                            Class<?>[] params = method.getParameterTypes();
                            if (params.length == 1) {
                                try {
                                    Class<?> packetClass = Class.forName("net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket");
                                    if (params[0].isAssignableFrom(packetClass)) {
                                        var constructor = packetClass.getConstructor(String.class);
                                        var packet = constructor.newInstance(message);
                                        method.invoke(connection, packet);
                                        return;
                                    }
                                } catch (ClassNotFoundException e) {
                                    // Try ChatMessageC2SPacket with different package
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("[SaveLogins] Could not send command: " + e.getMessage());
            }
        }
    }
}