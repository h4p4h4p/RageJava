package com.kalca.ragejava.command;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;

public class CommandManager {

    public static final String PREFIX = "%";
    public static boolean rainbow = false;

    private final Minecraft mc = Minecraft.getMinecraft();
    private final List<ChatCommand> commands = new ArrayList<>();
    private Channel boundChannel;

    public CommandManager() {
        commands.add(new ColorCommand());
        commands.add(new ClickGuiCommand());
        commands.add(new ToggleCommand());
        commands.add(new BindCommand());
        commands.add(new BindListCommand());
        commands.add(new HelpCommand(this));
        MinecraftForge.EVENT_BUS.register(this);
    }

    public List<ChatCommand> getCommands() {
        return commands;
    }

    public static void sendMessage(String text) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
            if (mc.thePlayer == null || mc.theWorld == null || mc.ingameGUI == null) return;
            mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(text));
        });
    }

    public static void setAccent(int rgb) {
        com.kalca.ragejava.gui.Theme.ACCENT = 0xFF000000 | (rgb & 0xFFFFFF);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (rainbow) {
            int hue = (int) ((System.currentTimeMillis() / 5) % 360);
            setAccent(hsvToRgb(hue, 1.0f, 1.0f));
        }
        arm();
    }

    private void arm() {
        if (mc.getNetHandler() == null) return;
        NetworkManager manager = mc.getNetHandler().getNetworkManager();
        if (manager == null) return;
        Channel channel = manager.channel();
        if (channel == null || !channel.isActive()) return;

        if (boundChannel == channel) return;

        if (boundChannel != null && boundChannel.isActive()) {
            try {
                boundChannel.pipeline().remove("ragejava_chat");
            } catch (Exception ignored) {
            }
        }
        boundChannel = channel;
        if (channel.pipeline().get("ragejava_chat") != null) return;
        try {
            channel.pipeline().addBefore("packet_handler", "ragejava_chat", new MessageInterceptHandler());
        } catch (Exception ignored) {
        }
    }

    private void handleChat(String message) {
        if (!message.startsWith(PREFIX)) return;
        String body = message.substring(PREFIX.length()).trim();
        if (body.isEmpty()) return;

        String[] parts = body.split("\\s+");
        String name = parts[0].toLowerCase();
        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, parts.length - 1);

        for (ChatCommand command : commands) {
            for (String alias : command.getAliases()) {
                if (alias.equalsIgnoreCase(name) || command.getName().equalsIgnoreCase(name)) {
                    command.execute(args);
                    return;
                }
            }
        }
        sendMessage("\u00a7cUnknown command '\u00a7f" + name + "\u00a7c'. Type \u00a7f%help");
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1.0f - Math.abs((h / 60.0f) % 2 - 1.0f));
        float m = v - c;
        float r, g, b;
        int hi = (int) (h / 60) % 6;
        switch (hi) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return ((int) ((r + m) * 255) << 16) | ((int) ((g + m) * 255) << 8) | (int) ((b + m) * 255);
    }

    private class MessageInterceptHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            if (msg instanceof C01PacketChatMessage) {
                String text = ((C01PacketChatMessage) msg).getMessage();
                if (text != null && text.startsWith(PREFIX)) {
                    promise.setSuccess();
                    mc.addScheduledTask(() -> handleChat(text));
                    return;
                }
            }
            ctx.write(msg, promise);
        }
    }
}