package net.orbis.zakum.crates.command;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.crates.anim.AnimationValidator;
import net.orbis.zakum.crates.anim.types.AnimationFactory;
import net.orbis.zakum.crates.anim.types.CrateAnimation;
import net.orbis.zakum.crates.model.CrateDef;
import net.orbis.zakum.crates.model.RewardDef;
import net.orbis.zakum.crates.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command to preview crate animations.
 * 
 * Usage: /cratepreview <animation_type>
 * 
 * Allows admins and players to preview different animation types
 * without consuming a crate or key.
 */
public class CratePreviewCommand implements CommandExecutor, TabCompleter {
    
    private final Plugin plugin;
    
    public CratePreviewCommand(Plugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        
        // Check permission
        if (!player.hasPermission("zakum.crates.preview")) {
            player.sendMessage("§cYou don't have permission to preview animations.");
            return true;
        }
        
        // Validate arguments
        if (args.length == 0) {
            player.sendMessage("§cUsage: /cratepreview <animation_type>");
            player.sendMessage("§7Available types: §f" + String.join(", ", AnimationFactory.getRegisteredTypes()));
            return true;
        }
        
        String animationType = args[0];
        
        // Validate animation type
        AnimationValidator.ValidationResult validation = AnimationValidator.validateType(animationType);
        if (!validation.isValid()) {
            player.sendMessage("§c" + validation.getErrorMessage());
            return true;
        }
        
        // Start preview
        startPreview(player, animationType);
        player.sendMessage("§aStarting preview of §e" + animationType + "§a animation...");
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            // Complete animation type
            return filterMatches(args[0], Arrays.asList(AnimationFactory.getRegisteredTypes()));
        }
        return new ArrayList<>();
    }
    
    /**
     * Start an animation preview for the player.
     */
    private void startPreview(Player player, String animationType) {
        // Create dummy crate and reward for preview
        CrateDef dummyCrate = createDummyCrate();
        RewardDef dummyReward = createDummyReward();
        
        // Create animation instance
        CrateAnimation animation = AnimationFactory.create(animationType);
        animation.initialize(player, dummyCrate, dummyReward);
        
        // Create and open preview GUI
        Inventory previewInventory = Bukkit.createInventory(
            null,
            27,
            ItemBuilder.color("&6Preview: &e" + animationType)
        );
        
        player.openInventory(previewInventory);
        
        // Run animation
        runPreviewAnimation(player, animation, previewInventory);
    }
    
    /**
     * Run the preview animation with tick updates.
     */
    private void runPreviewAnimation(Player player, CrateAnimation animation, Inventory inventory) {
        final int[] tickNumber = {0};
        
        int taskId = ZakumApi.get().getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            // Check if player closed inventory
            if (!player.getOpenInventory().getTopInventory().equals(inventory)) {
                animation.cleanup();
                return;
            }
            
            // Tick animation
            boolean shouldContinue = animation.tick(tickNumber[0]);
            
            // Update GUI
            animation.updateGui(inventory, tickNumber[0]);
            
            // Check completion
            if (!shouldContinue || animation.isComplete()) {
                animation.cleanup();
                
                // Auto-close after short delay
                ZakumApi.get().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().equals(inventory)) {
                        player.closeInventory();
                        player.sendMessage("§aPreview complete!");
                    }
                }, 40L); // 2 second delay
            }
            
            tickNumber[0]++;
        }, 1L, 1L);
        
        // Safety timeout - cancel after max duration
        ZakumApi.get().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            ZakumApi.get().getScheduler().cancelTask(taskId);
        }, 300L); // 15 second timeout
    }
    
    /**
     * Create a dummy crate definition for preview.
     */
    private CrateDef createDummyCrate() {
        // Create simple weighted table with dummy rewards
        var rewardTable = new net.orbis.zakum.api.util.WeightedTable<RewardDef>();
        
        for (int i = 0; i < 5; i++) {
            RewardDef reward = createDummyReward();
            rewardTable.add(reward, 1.0);
        }
        
        ItemStack keyItem = new ItemStack(Material.TRIPWIRE_HOOK);
        
        return new CrateDef(
            "preview",
            "Preview Crate",
            false,
            0,
            keyItem,
            rewardTable
        );
    }
    
    /**
     * Create a dummy reward for preview.
     */
    private RewardDef createDummyReward() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        return new RewardDef(
            "preview_reward",
            "Preview Reward",
            1.0,
            List.of(item),
            null,
            null,
            null
        );
    }
    
    /**
     * Filter string matches for tab completion.
     */
    private List<String> filterMatches(String input, List<String> options) {
        List<String> matches = new ArrayList<>();
        String lowerInput = input.toLowerCase();
        
        for (String option : options) {
            if (option.toLowerCase().startsWith(lowerInput)) {
                matches.add(option);
            }
        }
        
        return matches;
    }
}
