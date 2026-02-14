package net.orbis.orbisgui.runtime;

import net.orbis.orbisgui.menu.MenuDef;
import net.orbis.orbisgui.menu.MenuRepository;
import net.orbis.orbisgui.prompts.ChatPromptService;
import net.orbis.zakum.api.gui.GuiService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrbisGuiService implements GuiService, Listener {

  private final Plugin plugin;
  private final MenuRepository menus;
  @SuppressWarnings("unused")
  private final ChatPromptService prompts;

  private final Map<UUID, String> openMenus;

  public OrbisGuiService(Plugin plugin, MenuRepository menus, ChatPromptService prompts) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
    this.menus = Objects.requireNonNull(menus, "menus");
    this.prompts = Objects.requireNonNull(prompts, "prompts");
    this.openMenus = new ConcurrentHashMap<>();
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
    Inventory inventory = Bukkit.createInventory(holder, size, color(def.title(), player, context));
    holder.bind(inventory);

    for (MenuDef.MenuItemDef item : def.items().values()) {
      if (item.slot() < 0 || item.slot() >= size) continue;
      inventory.setItem(item.slot(), toItem(item, player, context));
    }

    openMenus.put(player.getUniqueId(), def.id());
    player.openInventory(inventory);
    return true;
  }

  @Override
  public void close(Player player) {
    if (player == null) return;
    openMenus.remove(player.getUniqueId());
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
  }

  @EventHandler(ignoreCancelled = true)
  public void onClick(InventoryClickEvent event) {
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) return;
    if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;

    event.setCancelled(true);

    MenuDef def = menus.get(holder.menuId());
    if (def == null) return;

    MenuDef.MenuItemDef item = def.items().get(event.getRawSlot());
    if (item == null) return;

    if (item.closeInventory()) {
      close(player);
      return;
    }

    if (item.message() != null && !item.message().isBlank()) {
      player.sendMessage(color(item.message(), player, Collections.emptyMap()));
    }

    if (item.openGuiId() != null && !item.openGuiId().isBlank()) {
      open(player, item.openGuiId());
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getPlayer() instanceof Player player)) return;
    if (!(event.getInventory().getHolder() instanceof GuiHolder)) return;
    openMenus.remove(player.getUniqueId());
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
    String out = raw.replace("%player%", player.getName());
    for (Map.Entry<String, String> entry : context.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (key == null || key.isBlank() || value == null) continue;
      out = out.replace("%" + key + "%", value);
    }
    return ChatColor.translateAlternateColorCodes('&', out);
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
