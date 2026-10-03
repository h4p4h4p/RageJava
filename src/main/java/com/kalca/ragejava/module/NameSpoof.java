package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.Setting;
import com.kalca.ragejava.settings.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class NameSpoof extends Module {

    private final BooleanSetting selfSpoofSetting = new BooleanSetting("Self Spoof", false);
    private final StringSetting selfNameSetting = new StringSetting("Self Name", "Player");
    private final BooleanSetting otherSpoofSetting = new BooleanSetting("Other Spoof", false);
    private final StringSetting otherRealNameSetting = new StringSetting("Other Real Name", "");
    private final StringSetting otherFakeNameSetting = new StringSetting("Other Fake Name", "");

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<String, String> nameMap = new HashMap<>();
    private String originalName = "";
    private boolean wasSelfSpoofing = false;

    public NameSpoof() {
        super("NameSpoof", Category.RENDER);
        settings.add(selfSpoofSetting);
        settings.add(selfNameSetting);
        settings.add(otherSpoofSetting);
        settings.add(otherRealNameSetting);
        settings.add(otherFakeNameSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        if (setting == selfNameSetting) return selfSpoofSetting.getValue();
        if (setting == otherRealNameSetting) return otherSpoofSetting.getValue();
        if (setting == otherFakeNameSetting) return otherSpoofSetting.getValue();
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

        if (otherSpoofSetting.getValue() && !otherRealNameSetting.getValue().isEmpty() && !otherFakeNameSetting.getValue().isEmpty()) {
            nameMap.put(otherRealNameSetting.getValue(), otherFakeNameSetting.getValue());
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

        boolean selfSpoof = selfSpoofSetting.getValue();
        boolean otherSpoof = otherSpoofSetting.getValue();
        if (!selfSpoof && !otherSpoof) return;

        String fakeSelfName = selfNameSetting.getValue();
        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info == null) continue;
            String original = info.getGameProfile().getName();
            String spoofed = getSpoofedName(original);
            if (!original.equals(spoofed)) {
                info.setDisplayName(new ChatComponentText(spoofed));
            }
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!isEnabled()) return;
    }

    public String getSpoofedName(String original) {
        if (mc.thePlayer != null && original.equals(mc.thePlayer.getName()) && selfSpoofSetting.getValue()) {
            return selfNameSetting.getValue();
        }
        if (otherSpoofSetting.getValue()) {
            String mapped = nameMap.get(original);
            if (mapped != null) return mapped;
        }
        return original;
    }

    public void addNameMapping(String realName, String fakeName) {
        if (realName != null && !realName.isEmpty() && fakeName != null && !fakeName.isEmpty()) {
            nameMap.put(realName, fakeName);
        }
    }

    public void removeNameMapping(String realName) {
        nameMap.remove(realName);
    }

    public void clearNameMappings() {
        nameMap.clear();
    }

    public Map<String, String> getNameMap() {
        return nameMap;
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
        nameMap.clear();
        wasSelfSpoofing = false;
    }
}