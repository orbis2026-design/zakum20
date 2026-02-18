package net.orbis.zakum.crates.reward;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.orbis.zakum.crates.model.RewardDef;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;

/**
 * Handles reward notifications to players.
 * 
 * Provides various notification methods: chat, title, actionbar, sounds.
 */
public class RewardNotifier {
    
    /**
     * Notify player of a reward with all effects.
     */
    public void notifyReward(Player player, RewardDef reward) {
        notifyReward(player, reward, NotificationStyle.FULL);
    }
    
    /**
     * Notify player of a reward with specific style.
     */
    public void notifyReward(Player player, RewardDef reward, NotificationStyle style) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reward, "reward");
        
        switch (style) {
            case FULL -> notifyFull(player, reward);
            case TITLE_ONLY -> notifyTitle(player, reward);
            case CHAT_ONLY -> notifyChat(player, reward);
            case MINIMAL -> notifyMinimal(player, reward);
            case SILENT -> {}
        }
    }
    
    /**
     * Full notification with title, chat, and sound.
     */
    private void notifyFull(Player player, RewardDef reward) {
        // Title
        sendTitle(player, "§6§lREWARD!", "§e" + reward.name());
        
        // Chat message
        player.sendMessage("§8§m                                    ");
        player.sendMessage("§6§l   REWARD RECEIVED!");
        player.sendMessage("");
        player.sendMessage("  §e" + reward.name());
        player.sendMessage("");
        player.sendMessage("§8§m                                    ");
        
        // Custom messages from reward
        if (reward.messages() != null) {
            for (String message : reward.messages()) {
                player.sendMessage(message.replace('&', '§'));
            }
        }
        
        // Sound effect
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
    }
    
    /**
     * Title-only notification.
     */
    private void notifyTitle(Player player, RewardDef reward) {
        sendTitle(player, "§6Reward", "§e" + reward.name());
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.0f);
    }
    
    /**
     * Chat-only notification.
     */
    private void notifyChat(Player player, RewardDef reward) {
        player.sendMessage("§6§l[!] §eYou received: §f" + reward.name());
        
        if (reward.messages() != null) {
            for (String message : reward.messages()) {
                player.sendMessage(message.replace('&', '§'));
            }
        }
    }
    
    /**
     * Minimal notification.
     */
    private void notifyMinimal(Player player, RewardDef reward) {
        player.sendMessage("§7+ §f" + reward.name());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }
    
    /**
     * Send title to player.
     */
    private void sendTitle(Player player, String title, String subtitle) {
        // Use Paper Adventure API
        Component titleComponent = Component.text(title.replace('&', '§'));
        Component subtitleComponent = Component.text(subtitle.replace('&', '§'));
        
        Title adventureTitle = Title.title(
            titleComponent,
            subtitleComponent,
            Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(2000),
                Duration.ofMillis(500)
            )
        );
        
        player.showTitle(adventureTitle);
    }
    
    /**
     * Broadcast reward to all players.
     */
    public void broadcastReward(Player recipient, RewardDef reward) {
        String message = "§6§l[!] §e" + recipient.getName() + " §7received §f" + reward.name() + " §7from a crate!";
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }
    
    /**
     * Broadcast rare reward to all players.
     */
    public void broadcastRareReward(Player recipient, RewardDef reward) {
        String message = "§6§l§n[!!!] RARE REWARD [!!!]";
        String message2 = "§e" + recipient.getName() + " §7received §6§l" + reward.name() + "§7!";
        
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage("");
            online.sendMessage(message);
            online.sendMessage(message2);
            online.sendMessage("");
            online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
    }
    
    /**
     * Notification style options.
     */
    public enum NotificationStyle {
        FULL,        // Title + chat + sounds
        TITLE_ONLY,  // Title + sound only
        CHAT_ONLY,   // Chat only
        MINIMAL,     // Brief chat message
        SILENT       // No notification
    }
}
