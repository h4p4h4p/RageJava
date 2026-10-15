package com.kalca.ragejava.module;

import com.kalca.ragejava.settings.BooleanSetting;
import com.kalca.ragejava.settings.ModeSetting;
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

public class NameHider extends Module {

    public static final String MODE_HIDE = "Hide";
    public static final String MODE_SPOOF = "Spoof";
    public static final String MODE_RANDOM = "Random";

    private final ModeSetting modeSetting = new ModeSetting("Mode", new String[]{MODE_HIDE, MODE_SPOOF, MODE_RANDOM}, 0);
    private final StringSetting customNameSetting = new StringSetting("Custom Name", "Player");
    private final BooleanSetting hideSelfSetting = new BooleanSetting("Hide Self", true);
    private final BooleanSetting hideOthersSetting = new BooleanSetting("Hide Others", false);
    private final BooleanSetting tablistSetting = new BooleanSetting("Tablist", true);
    private final BooleanSetting nameTagSetting = new BooleanSetting("Name Tags", true);
    private final BooleanSetting chatSetting = new BooleanSetting("Chat Messages", true);
    private final BooleanSetting deathMessagesSetting = new BooleanSetting("Death Messages", false);
    private final BooleanSetting useRandomNamesSetting = new BooleanSetting("Use Random Names", false);

    private final Minecraft mc = Minecraft.getMinecraft();
    private String originalName = "";
    private boolean wasHiding = false;

    public NameHider() {
        super("NameHider", Category.RENDER);
        settings.add(modeSetting);
        settings.add(customNameSetting);
        settings.add(hideSelfSetting);
        settings.add(hideOthersSetting);
        settings.add(tablistSetting);
        settings.add(nameTagSetting);
        settings.add(chatSetting);
        settings.add(deathMessagesSetting);
        settings.add(useRandomNamesSetting);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean isSettingVisible(Setting setting) {
        String mode = modeSetting.getValue();
        if (setting == customNameSetting) return MODE_SPOOF.equals(mode);
        if (setting == useRandomNamesSetting) return MODE_RANDOM.equals(mode);
        if (setting == tablistSetting) return true;
        if (setting == nameTagSetting) return true;
        if (setting == chatSetting) return true;
        if (setting == deathMessagesSetting) return true;
        return true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (mc.thePlayer == null) return;

        boolean shouldHide = isEnabled() && hideSelfSetting.getValue();
        if (shouldHide && !wasHiding) {
            originalName = mc.thePlayer.getName();
            wasHiding = true;
        } else if (!shouldHide && wasHiding) {
            wasHiding = false;
        }
    }

    @SubscribeEvent
    public void onRenderName(RenderLivingEvent.Specials.Pre event) {
        if (!isEnabled()) return;
        if (!nameTagSetting.getValue()) return;
        if (!(event.entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.entity;
        String original = player.getName();
        String hidden = getHiddenName(original);

        if (!original.equals(hidden)) {
            player.setCustomNameTag(hidden);
            player.setAlwaysRenderNameTag(true);
        }
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Specials.Post event) {
        if (!isEnabled()) return;
        if (!nameTagSetting.getValue()) return;
        if (!(event.entity instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.entity;
        String original = player.getName();
        String hidden = getHiddenName(original);

        if (!original.equals(hidden)) {
            player.setCustomNameTag(original);
            player.setAlwaysRenderNameTag(false);
        }
    }

    @SubscribeEvent
    public void onRenderTabList(RenderGameOverlayEvent event) {
        if (!isEnabled()) return;
        if (!tablistSetting.getValue()) return;
        if (event.type != RenderGameOverlayEvent.ElementType.PLAYER_LIST) return;
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;

        for (NetworkPlayerInfo info : mc.getNetHandler().getPlayerInfoMap()) {
            if (info == null) continue;
            String original = info.getGameProfile().getName();
            String hidden = getHiddenName(original);
            if (!original.equals(hidden)) {
                info.setDisplayName(new ChatComponentText(hidden));
            }
        }
    }

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (!isEnabled()) return;
        if (!chatSetting.getValue() && !deathMessagesSetting.getValue()) return;
        if (mc.thePlayer == null) return;

        String message = event.message.getUnformattedText();
        String originalSelfName = originalName.isEmpty() ? mc.thePlayer.getName() : originalName;

        // Check if it's a death message
        boolean isDeath = message.contains("was slain") || message.contains("was killed")
                || message.contains("died") || message.contains("fell")
                || message.contains("drowned") || message.contains("burned")
                || message.contains("shot") || message.contains("blew up");

        if (isDeath && !deathMessagesSetting.getValue()) return;
        if (!isDeath && !chatSetting.getValue()) return;

        if (message.contains(originalSelfName)) {
            String replacement = getReplacementName();
            String newMessage = message.replace(originalSelfName, replacement);
            event.message = new ChatComponentText(newMessage);
        }
    }

    private String getHiddenName(String original) {
        if (original == null) return original;

        boolean isSelf = mc.thePlayer != null && original.equals(originalName.isEmpty() ? mc.thePlayer.getName() : originalName);

        if (isSelf && !hideSelfSetting.getValue()) return original;
        if (!isSelf && !hideOthersSetting.getValue()) return original;

        String mode = modeSetting.getValue();

        if (MODE_HIDE.equals(mode)) {
            return "";
        } else if (MODE_SPOOF.equals(mode)) {
            return customNameSetting.getValue();
        } else if (MODE_RANDOM.equals(mode) || useRandomNamesSetting.getValue()) {
            return getRandomName();
        }
        return original;
    }

    private String getReplacementName() {
        String mode = modeSetting.getValue();
        if (MODE_HIDE.equals(mode)) return "";
        if (MODE_SPOOF.equals(mode)) return customNameSetting.getValue();
        if (MODE_RANDOM.equals(mode) || useRandomNamesSetting.getValue()) return getRandomName();
        return customNameSetting.getValue();
    }

    private String getRandomName() {
        String[] names = {"Steve", "Alex", "Player", "Notch", "Herobrine", "Dream", "Technoblade",
                "DanTDM", "Stampy", "PopularMMOs", "GamingWithJen", "SSundee", "CaptainSparklez",
                "AntVenom", "BajanCanadian", "JeromeASF", "Vikkstar123", "PrestonPlayz",
                "UnspeakableGaming", "MooseCraft"};
        return names[(int) (Math.random() * names.length)];
    }

    public String getOriginalName() {
        return originalName;
    }

    public boolean isHiding() {
        return wasHiding;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        wasHiding = false;
        // Restore original names on disable
        if (mc.theWorld != null) {
            for (EntityPlayer player : mc.theWorld.playerEntities) {
                if (player != null) {
                    player.setCustomNameTag(player.getName());
                    player.setAlwaysRenderNameTag(false);
                }
            }
        }
    }
}