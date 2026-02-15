package net.orbis.orbisgui;

import net.orbis.orbisgui.menu.MenuRepository;
import net.orbis.orbisgui.prompts.ChatPromptService;
import net.orbis.orbisgui.runtime.OrbisGuiService;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.gui.GuiIds;
import net.orbis.zakum.api.gui.GuiService;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * OrbisGUI: an InfiniteGUI-inspired declarative GUI runtime.
 *
 * Constraints:
 * - event-driven (no refresh loops)
 * - bounded per-player state
 * - reload safe (cleans listeners/tasks/prompts)
 */
public final class OrbisGuiPlugin extends JavaPlugin implements CommandExecutor {

  private MenuRepository menus;
  private ChatPromptService prompts;
  private OrbisGuiService gui;
  private ZakumApi api;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    // Ensure menu folders exist + seed minimal defaults.
    ensureMenuFolders();
    saveDefaultMenu("SystemMenus/SystemRoot.yml");
    saveDefaultMenu("SystemMenus/CratesMain.yml");
    saveDefaultMenu("SystemMenus/BattlePassMain.yml");
    saveDefaultMenu("SystemMenus/BattlePassRewards.yml");
    saveDefaultMenu("SystemMenus/CratesPreview.yml");
    saveDefaultMenu("SystemMenus/ProfileMain.yml");
    saveDefaultMenu("SystemMenus/SocialMain.yml");
    saveDefaultMenu("SystemMenus/EconomyMain.yml");
    saveDefaultMenu("SystemMenus/SettingsMain.yml");
    saveDefaultMenu("SystemMenus/NetworkStatus.yml");
    saveDefaultMenu("SystemMenus/AceLab.yml");
    saveDefaultMenu("SystemMenus/CosmeticsMain.yml");
    saveDefaultMenu("SystemMenus/PetsMain.yml");
    saveDefaultMenu("SystemMenus/PetsMini.yml");
    saveDefaultMenu("CustomGuis/example.yml");

    this.menus = new MenuRepository(this);
    this.menus.reload();

    this.prompts = new ChatPromptService(this);

    this.api = Bukkit.getServicesManager().load(ZakumApi.class);
    this.gui = new OrbisGuiService(this, menus, prompts, api);
    Bukkit.getServicesManager().register(GuiService.class, gui, this, ServicePriority.Normal);

    if (getCommand("gui") != null) {
      getCommand("gui").setExecutor(this);
    }

    Bukkit.getPluginManager().registerEvents(gui, this);
    Bukkit.getPluginManager().registerEvents(prompts, this);

    getLogger().info("OrbisGUI enabled. Loaded " + menus.ids().size() + " menu(s). Use /gui to open.");
  }

  @Override
  public void onDisable() {
    try {
      if (gui != null) gui.shutdown();
      if (prompts != null) prompts.shutdown();
    } finally {
      Bukkit.getServicesManager().unregisterAll(this);
    }
  }

  private void ensureMenuFolders() {
    File data = getDataFolder();
    if (!data.exists() && !data.mkdirs()) {
      getLogger().warning("Failed to create plugin data folder: " + data.getAbsolutePath());
      return;
    }
    String custom = getConfig().getString("menus.custom-path", "CustomGuis");
    String system = getConfig().getString("menus.system-path", "SystemMenus");
    new File(data, custom).mkdirs();
    new File(data, system).mkdirs();
  }

  private void saveDefaultMenu(String resourcePath) {
    File out = new File(getDataFolder(), resourcePath);
    if (out.exists()) return;
    File parent = out.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
    saveResource(resourcePath, false);
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender,
                           @NotNull Command command,
                           @NotNull String label,
                           @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(color("&cPlayers only."));
      return true;
    }

    if (args.length == 0) {
      gui.open(player, GuiIds.SYSTEM_ROOT);
      return true;
    }

    String sub = args[0].toLowerCase();
    switch (sub) {
      case "open" -> {
        if (args.length < 2) {
          player.sendMessage(color("&cUsage: /" + label + " open <id>"));
          return true;
        }
        String id = args[1];
        if (!gui.open(player, id)) {
          player.sendMessage(color("&cUnknown GUI id: " + id));
        }
        return true;
      }
      case "list" -> {
        List<String> ids = menus.ids().stream().sorted().toList();
        player.sendMessage(color("&8[<gradient:#38bdf8:#60a5fa>Orbis</gradient>&8] &bGUI Menus &7(" + ids.size() + ")"));
        for (String id : ids) player.sendMessage(color("&7- &f" + id));
        return true;
      }
      case "reload" -> {
        if (!player.hasPermission("orbis.gui.admin")) {
          player.sendMessage(color("&cNo permission."));
          return true;
        }
        reloadConfig();
        ensureMenuFolders();
        menus.reload();
        prompts.reloadFromConfig();
        gui.reload();
        player.sendMessage(color("&aOrbis GUI reloaded. &7loaded=&f" + menus.ids().size()));
        return true;
      }
      default -> {
        player.sendMessage(color("&cUnknown subcommand. &7Try: &f/" + label + " open|list|reload"));
        return true;
      }
    }
  }

  private static String color(String message) {
    return BrandingText.render(message);
  }
}
