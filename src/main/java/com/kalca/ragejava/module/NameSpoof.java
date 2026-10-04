package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.Setting;
import com.kalca.ragejava.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.regex.Pattern;

public class NameSpoof extends Module {

    private final BooleanSetting selfSpoofSetting = new BooleanSetting("Self Spoof", false);
    private final StringSetting selfNameSetting = new StringSetting("Self Name", "Player");
    private final BooleanSetting chatSpoofSetting = new BooleanSetting("Chat Spoof", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private String originalName = "";
    private boolean wasSelfSpoofing = false;

    public NameSpoof() {
        super("NameSpoof", Category.RENDER);
        settings.add(selfSpoofSetting);
        settings.add(selfNameSetting);
        settings.add(chatSpoofSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        if (setting == selfNameSetting) return selfSpoofSetting.getValue();
        if (setting == chatSpoofSetting) return selfSpoofSetting.getValue();
        return true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.thePlayer == null) return;

        boolean selfSpoof = selfSpoofSetting.getValue();
        if (selfSpoof && !wasSelfSpoofing) {
            originalName = mc.thePlayer.getName();
            wasSelfSpoofing = true;
        } else if (!selfSpoof && wasSelfSpoofing) {
            wasSelfSpoofing = false;
        }
    }

    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Specials.Pre event) {
        if (!isEnabled()) return;
        if (!(event.entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.entity;
        String original = player.getName();
        String spoofed = getSpoofedName(original);

        if (!original.equals(spoofed)) {
            player.setCustomNameTag(spoofed);
            player.setAlwaysRenderNameTag(true);
        }
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Specials.Post event) {
        if (!isEnabled()) return;
        if (!(event.entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.entity;
        String original = player.getName();
        String spoofed = getSpoofedName(original);

        if (!original.equals(spoofed)) {
            player.setCustomNameTag(original);
            player.setAlwaysRenderNameTag(false);
        }
    }

    @SubscribeEvent
    public void onRenderTabList(RenderGameOverlayEvent event) {
        if (!isEnabled()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) return;
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;

        if (!selfSpoofSetting.getValue()) return;

        String fakeSelfName = selfNameSetting.getValue();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info == null) continue;
            String original = info.getGameProfile().getName();
            if (original.equals(mc.thePlayer.getName())) {
                info.setDisplayName(new ChatComponentText(fakeSelfName));
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!isEnabled()) return;
        if (!chatSpoofSetting.getValue()) return;
        if (mc.thePlayer == null) return;

        String fakeName = selfNameSetting.getValue();
        String message = event.message.getUnformattedText();
        String originalName = mc.thePlayer.getName();

        if (message.contains(originalName)) {
            String newMessage = message.replace(originalName, fakeName);
            event.message = new ChatComponentText(newMessage);
        }
    }

    public String getSpoofedName(String original) {
        if (mc.thePlayer != null && original.equals(mc.thePlayer.getName()) && selfSpoofSetting.getValue()) {
            return selfNameSetting.getValue();
        }
        return original;
    }

    public String getOriginalName() {
        return originalName;
    }

    public boolean isSelfSpoofing() {
        return selfSpoofSetting.getValue() && wasSelfSpoofing;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        wasSelfSpoofing = false;
    }
}