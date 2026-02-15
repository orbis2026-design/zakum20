package net.orbis.orbisgui.runtime;

import net.orbis.orbisgui.menu.MenuDef;
import net.orbis.orbisgui.menu.MenuRepository;
import net.orbis.orbisgui.prompts.ChatPromptService;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.action.AceEngine;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.gui.GuiService;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.api.util.BrandingText;
import net.orbis.zakum.api.vault.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrbisGuiService implements GuiService, Listener {

  private final Plugin plugin;
  private final MenuRepository menus;
  @SuppressWarnings("unused")
  private final ChatPromptService prompts;
  private final ZakumApi api;

  private final Map<UUID, String> openMenus;
  private final Map<UUID, Map<String, String>> openContexts;
  private final Map<UUID, List<Integer>> openTasks;
  private final Map<UUID, Map<Integer, MenuDef.MenuItemDef>> openSceneItems;

  public OrbisGuiService(Plugin plugin, MenuRepository menus, ChatPromptService prompts, ZakumApi api) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.menus = Objects.requireNonNull(menus, "menus");
    this.prompts = Objects.requireNonNull(prompts, "prompts");
    this.api = api;
    this.openMenus = new ConcurrentHashMap<>();
    this.openContexts = new ConcurrentHashMap<>();
    this.openTasks = new ConcurrentHashMap<>();
    this.openSceneItems = new ConcurrentHashMap<>();
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public boolean open(Player player, String guiId) {
    return open(player, guiId, Collections.emptyMap());
  }

  @Override
  public boolean open(Player player, String guiId, Map<String, String> context) {
    if (player == null || guiId == null || guiId.isBlank()) return false;
    MenuDef def = menus.get(guiId);
    if (def == null) return false;

    int size = def.rows() * 9;
    GuiHolder holder = new GuiHolder(def.id());
    Map<String, String> providedContext = context == null ? Map.of() : Map.copyOf(context);
    Map<String, String> renderContext = buildContext(player, providedContext);
    Inventory inventory = Bukkit.createInventory(holder, size, color(def.title(), player, renderContext));
    holder.bind(inventory);

    cancelTasks(player.getUniqueId());
    openContexts.put(player.getUniqueId(), providedContext);

    int initialKey = def.scenes().containsKey(0) ? 0 : def.scenes().keySet().stream().findFirst().orElse(0);
    MenuDef.MenuSceneDef scene0 = def.scenes().getOrDefault(initialKey, def.scenes().values().stream().findFirst().orElse(null));
    if (scene0 != null) {
      applyScene(player, inventory, scene0, renderContext);
    }

    openMenus.put(player.getUniqueId(), def.id());
    player.openInventory(inventory);

    scheduleScenes(player, def, initialKey);
    return true;
  }

  @Override
  public void close(Player player) {
    if (player == null) return;
    openMenus.remove(player.getUniqueId());
    openContexts.remove(player.getUniqueId());
    openSceneItems.remove(player.getUniqueId());
    cancelTasks(player.getUniqueId());
    player.closeInventory();
  }

  public void reload() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      String id = openMenus.get(player.getUniqueId());
      if (id != null) open(player, id);
    }
  }

  public void shutdown() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (openMenus.containsKey(player.getUniqueId())) {
        player.closeInventory();
      }
    }
    openMenus.clear();
    openContexts.clear();
    openTasks.clear();
    openSceneItems.clear();
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
    if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

    event.setCancelled(true);

    MenuDef def = menus.get(holder.menuId());
    if (def == null) return;

    Map<Integer, MenuDef.MenuItemDef> sceneItems = openSceneItems.get(player.getUniqueId());
    MenuDef.MenuItemDef item = sceneItems == null ? null : sceneItems.get(event.getRawSlot());
    if (item == null) return;

    Map<String, String> context = buildContext(player, openContexts.getOrDefault(player.getUniqueId(), Map.of()));

    if (item.message() != null && !item.message().isBlank()) {
      player.sendMessage(color(item.message(), player, context));
    }

    if (item.commandAction() != null && item.commandAction().command() != null) {
      dispatchCommand(player, item.commandAction(), context);
    }

    if (item.aceAction() != null) {
      executeAce(player, item.aceAction(), context);
    }

    boolean opened = false;
    if (item.openGuiId() != null && !item.openGuiId().isBlank()) {
      Map<String, String> nextContext = resolveContext(item.openContext(), player, context);
      opened = open(player, item.openGuiId(), nextContext);
    }

    if (item.closeInventory() && !opened) {
      close(player);
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
    openMenus.remove(player.getUniqueId());
    openContexts.remove(player.getUniqueId());
    openSceneItems.remove(player.getUniqueId());
    cancelTasks(player.getUniqueId());
  }

  private static ItemStack toItem(MenuDef.MenuItemDef def, Player player, Map<String, String> context) {
    ItemStack stack = new ItemStack(def.material(), def.amount());
    ItemMeta meta = stack.getItemMeta();
    if (meta == null) return stack;

    if (def.name() != null && !def.name().isBlank()) {
      meta.setDisplayName(color(def.name(), player, context));
    }
    if (def.lore() != null && !def.lore().isEmpty()) {
      meta.setLore(def.lore().stream().map(line -> color(line, player, context)).toList());
    }
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    if (def.flags() != null && !def.flags().isEmpty()) {
      meta.addItemFlags(def.flags().toArray(new ItemFlag[0]));
    }
    stack.setItemMeta(meta);
    return stack;
  }

  private static String color(String raw, Player player, Map<String, String> context) {
    if (raw == null) return "";
    String out = applyPlaceholders(raw, player, context);
    return BrandingText.render(out);
  }

  private static String applyPlaceholders(String raw, Player player, Map<String, String> context) {
    if (raw == null) return "";
    String out = raw;
    for (Map.Entry<String, String> entry : context.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isBlank() || value == null) continue;
      out = out.replace("%" + key + "%", value);
    }
    return out;
  }

  private void applyScene(Player player, Inventory inventory, MenuDef.MenuSceneDef scene, Map<String, String> context) {
    if (player == null || inventory == null || scene == null) return;
    int size = inventory.getSize();
    inventory.clear();
    for (MenuDef.MenuItemDef item : scene.items().values()) {
      if (item.slot() < 0 || item.slot() >= size) continue;
      inventory.setItem(item.slot(), toItem(item, player, context));
    }
    openSceneItems.put(player.getUniqueId(), scene.items());
  }

  private void scheduleScenes(Player player, MenuDef def, int initialKey) {
    if (player == null || def == null) return;
    UUID playerId = player.getUniqueId();
    List<Integer> tasks = new java.util.ArrayList<>();
    for (Map.Entry<Integer, MenuDef.MenuSceneDef> entry : def.scenes().entrySet()) {
      if (entry.getKey() == initialKey) continue;
      MenuDef.MenuSceneDef scene = entry.getValue();
      int delay = Math.max(0, scene.delayTicks());
      int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
        Player target = Bukkit.getPlayer(playerId);
        if (target == null || !target.isOnline()) return;
        String openId = openMenus.get(playerId);
        if (openId == null || !openId.equals(def.id())) return;
        Inventory inv = target.getOpenInventory().getTopInventory();
        if (!(inv.getHolder() instanceof GuiHolder holder) || !holder.menuId().equals(def.id())) return;
        Map<String, String> context = buildContext(target, openContexts.getOrDefault(playerId, Map.of()));
        applyScene(target, inv, scene, context);
      }, delay).getTaskId();
      tasks.add(taskId);
    }
    if (!tasks.isEmpty()) {
      openTasks.put(playerId, tasks);
    }
  }

  private void cancelTasks(UUID playerId) {
    List<Integer> tasks = openTasks.remove(playerId);
    if (tasks == null) return;
    for (Integer taskId : tasks) {
      if (taskId != null && taskId > 0) {
        Bukkit.getScheduler().cancelTask(taskId);
      }
    }
  }

  private Map<String, String> buildContext(Player player, Map<String, String> baseContext) {
    Map<String, String> context = new LinkedHashMap<>();
    context.put("player", player.getName());
    context.put("player_name", player.getName());
    context.put("player_uuid", player.getUniqueId().toString());
    context.put("world", player.getWorld().getName());
    context.put("x", String.valueOf(Math.round(player.getLocation().getX())));
    context.put("y", String.valueOf(Math.round(player.getLocation().getY())));
    context.put("z", String.valueOf(Math.round(player.getLocation().getZ())));
    context.put("ping", String.valueOf(safePing(player)));
    context.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
    context.put("tps", formatTps());

    String serverId = api != null ? api.server().serverId() : "unknown";
    context.put("server_id", serverId);

    NamespacedKey rankKey = new NamespacedKey("orbis", "cloud_rank");
    NamespacedKey linkedKey = new NamespacedKey("orbis", "cloud_discord_linked");
    NamespacedKey discordIdKey = new NamespacedKey("orbis", "cloud_discord_id");
    String rank = player.getPersistentDataContainer().getOrDefault(rankKey, PersistentDataType.STRING, "DEFAULT");
    byte linked = player.getPersistentDataContainer().getOrDefault(linkedKey, PersistentDataType.BYTE, (byte) 0);
    String discordId = player.getPersistentDataContainer().getOrDefault(discordIdKey, PersistentDataType.STRING, "");
    context.put("rank", rank == null ? "DEFAULT" : rank);
    context.put("discord_linked", linked == (byte) 1 ? "true" : "false");
    context.put("discord_id", discordId == null ? "" : discordId);

    EconomyService economy = null;
    if (api != null) {
      economy = api.capability(ZakumCapabilities.ECONOMY).orElse(null);
    }
    if (economy == null) {
      economy = Bukkit.getServicesManager().load(EconomyService.class);
    }
    if (economy != null && economy.available()) {
      context.put("balance", formatMoney(economy.balance(player.getUniqueId())));
    } else {
      context.put("balance", "0.00");
    }

    SocialService social = null;
    if (api != null) {
      social = api.capability(ZakumCapabilities.SOCIAL).orElse(null);
    }
    if (social == null) {
      social = Bukkit.getServicesManager().load(SocialService.class);
    }
    if (social != null) {
      SocialService.SocialSnapshot snapshot = social.snapshot(player.getUniqueId());
      context.put("friends", String.valueOf(snapshot.friends().size()));
      context.put("allies", String.valueOf(snapshot.allies().size()));
      context.put("rivals", String.valueOf(snapshot.rivals().size()));
    } else {
      context.put("friends", "0");
      context.put("allies", "0");
      context.put("rivals", "0");
    }

    context.put("level", String.valueOf(player.getLevel()));
    context.put("health", String.valueOf(Math.round(player.getHealth())));
    context.put("food", String.valueOf(player.getFoodLevel()));

    if (baseContext != null) {
      for (Map.Entry<String, String> entry : baseContext.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        if (key == null || key.isBlank()) continue;
        if (value == null) continue;
        context.put(key, applyPlaceholders(value, player, context));
      }
    }
    return context;
  }

  private Map<String, String> resolveContext(Map<String, String> raw, Player player, Map<String, String> context) {
    if (raw == null || raw.isEmpty()) return Map.of();
    Map<String, String> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : raw.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isBlank()) continue;
      if (value == null) continue;
      resolved.put(key, applyPlaceholders(value, player, context));
    }
    return Map.copyOf(resolved);
  }

  private void dispatchCommand(Player player, MenuDef.CommandAction action, Map<String, String> context) {
    if (action == null || action.command() == null || action.command().isBlank()) return;
    String cmd = applyPlaceholders(action.command(), player, context);
    if (cmd.startsWith("/")) cmd = cmd.substring(1);
    if (cmd.isBlank()) return;
    if (action.asConsole()) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    } else {
      player.performCommand(cmd);
    }
  }

  private void executeAce(Player player, MenuDef.AceAction action, Map<String, String> context) {
    if (action == null || action.script() == null || action.script().isEmpty()) return;
    if (api == null) return;
    List<String> script = action.script().stream()
      .map(line -> applyPlaceholders(line, player, context))
      .toList();
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (action.metadata() != null) {
      for (Map.Entry<String, String> entry : action.metadata().entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        if (key == null || key.isBlank()) continue;
        if (value == null) continue;
        metadata.put(key, applyPlaceholders(value, player, context));
      }
    }
    api.getAceEngine().executeScript(script, new AceEngine.ActionContext(player, Optional.empty(), metadata));
  }

  private static int safePing(Player player) {
    try {
      return Math.max(0, player.getPing());
    } catch (Throwable ignored) {
      return 0;
    }
  }

  private static String formatTps() {
    try {
      double[] tps = Bukkit.getTPS();
      if (tps != null && tps.length > 0 && Double.isFinite(tps[0])) {
        return String.format(java.util.Locale.ROOT, "%.2f", tps[0]);
      }
    } catch (Throwable ignored) {}
    return "20.00";
  }

  private static String formatMoney(double value) {
    if (!Double.isFinite(value)) return "0.00";
    return String.format(java.util.Locale.ROOT, "%.2f", value);
  }

  private static final class GuiHolder implements InventoryHolder {
    private final String menuId;
    private Inventory inventory;

    private GuiHolder(String menuId) {
      this.menuId = menuId;
    }

    private void bind(Inventory inventory) {
      this.inventory = inventory;
    }

    private String menuId() {
      return menuId;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }
}
