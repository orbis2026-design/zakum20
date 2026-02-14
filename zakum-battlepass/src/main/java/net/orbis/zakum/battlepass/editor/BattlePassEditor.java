package net.orbis.zakum.battlepass.editor;

import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.battlepass.BattlePassPlugin;
import net.orbis.zakum.battlepass.QuestLoader;
import net.orbis.zakum.battlepass.rewards.RewardLoader;
import net.orbis.zakum.battlepass.yaml.BattlePassYamlStore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal, safe, event-driven in-game editors for quests.yml and rewards.yml.
 *
 * Design posture:
 * - no refresh loops
 * - all writes are atomic + backed up (BattlePassYamlStore)
 * - write operations are async + bounded; runtime reload occurs sync
 */
public final class BattlePassEditor {

  private final BattlePassPlugin plugin;
  private final ZakumApi zakum;
  private final BattlePassYamlStore store;
  private final AtomicBoolean adminJobRunning;
  private final EditorSessionManager sessions;

  public BattlePassEditor(BattlePassPlugin plugin, ZakumApi zakum, BattlePassYamlStore store, AtomicBoolean adminJobRunning, EditorSessionManager sessions) {
    this.plugin = plugin;
    this.zakum = zakum;
    this.store = store;
    this.adminJobRunning = adminJobRunning;
    this.sessions = sessions;
  }

  public EditorSessionManager sessions() { return sessions; }

  // -----------------------------
  // Open menus
  // -----------------------------

  public void openHome(Player p) {
    if (p == null) return;
    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.ADMIN_HOME, 1, null, null);
    Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_AQUA + "BattlePass Admin");
    holder.bind(inv);

    inv.setItem(11, EditorItems.item(Material.BOOK, ChatColor.YELLOW + "Quest Editor",
      EditorItems.lore(
        ChatColor.GRAY + "Edit quests.yml safely.",
        ChatColor.DARK_GRAY + "No refresh loops"
      )));

    inv.setItem(15, EditorItems.item(Material.CHEST, ChatColor.AQUA + "Rewards Editor",
      EditorItems.lore(
        ChatColor.GRAY + "Edit rewards.yml safely.",
        ChatColor.DARK_GRAY + "Atomic writes"
      )));

    var qv = store.validateQuests();
    var rv = store.validateRewards();
    inv.setItem(13, EditorItems.item(Material.PAPER, ChatColor.GREEN + "YAML Status",
      EditorItems.lore(
        ChatColor.GRAY + "quests.yml: " + (qv.ok() ? ChatColor.GREEN + "OK" : ChatColor.RED + "BAD"),
        ChatColor.GRAY + "rewards.yml: " + (rv.ok() ? ChatColor.GREEN + "OK" : ChatColor.RED + "BAD"),
        " ",
        ChatColor.DARK_GRAY + "Click: validate + backup"
      )));

    inv.setItem(22, EditorItems.navClose());
    inv.setItem(26, EditorItems.item(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Player Menu",
      EditorItems.lore(ChatColor.DARK_GRAY + "Back to /battlepass menu")));
    p.openInventory(inv);
  }

  public void openQuestList(Player p, int page) {
    if (p == null) return;
    int pg = Math.max(1, page);

    YamlConfiguration yaml = loadQuestsYaml();
    ConfigurationSection root = yaml.getConfigurationSection("quests");

    List<String> ids = new ArrayList<>();
    if (root != null) ids.addAll(root.getKeys(false));
    ids.sort(String.CASE_INSENSITIVE_ORDER);

    int perPage = 45;
    int maxPage = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
    pg = Math.min(pg, maxPage);

    int start = (pg - 1) * perPage;
    int end = Math.min(ids.size(), start + perPage);

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.QUEST_LIST, pg, null, null);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.YELLOW + "Quest Editor");
    holder.bind(inv);

    int slot = 0;
    for (int i = start; i < end; i++, slot++) {
      String id = ids.get(i);
      ConfigurationSection q = root == null ? null : root.getConfigurationSection(id);
      if (q == null) continue;

      boolean enabled = q.getBoolean("enabled", true);
      String name = q.getString("name", id);
      long points = q.getLong("points", 0);
      boolean premiumOnly = q.getBoolean("premiumOnly", false);
      String cadence = q.getString("cadence", "SEASON");
      List<?> steps = q.getList("steps");

      Material mat = enabled ? Material.WRITABLE_BOOK : Material.GRAY_DYE;

      inv.setItem(slot, EditorItems.item(mat,
        (enabled ? ChatColor.GREEN : ChatColor.RED) + id,
        EditorItems.lore(
          ChatColor.GRAY + name,
          ChatColor.GRAY + "Points: " + ChatColor.AQUA + points,
          ChatColor.GRAY + "Cadence: " + ChatColor.AQUA + cadence,
          ChatColor.GRAY + "PremiumOnly: " + (premiumOnly ? ChatColor.YELLOW + "Yes" : ChatColor.GRAY + "No"),
          ChatColor.GRAY + "Steps: " + ChatColor.AQUA + (steps == null ? 0 : steps.size()),
          " ",
          ChatColor.DARK_GRAY + "Left: details",
          ChatColor.DARK_GRAY + "Right: toggle enabled"
        )));
    }

    inv.setItem(45, EditorItems.navPrev());
    inv.setItem(49, EditorItems.navBack());
    inv.setItem(53, EditorItems.navNext());
    p.openInventory(inv);
  }

  public void openQuestDetail(Player p, String questId) {
    if (p == null || questId == null) return;

    YamlConfiguration yaml = loadQuestsYaml();
    String base = "quests." + questId;
    if (!yaml.isConfigurationSection(base)) {
      p.sendMessage(ChatColor.RED + "Quest not found: " + questId);
      openQuestList(p, 1);
      return;
    }

    boolean enabled = yaml.getBoolean(base + ".enabled", true);
    String name = yaml.getString(base + ".name", questId);
    long points = yaml.getLong(base + ".points", 0);
    boolean premiumOnly = yaml.getBoolean(base + ".premiumOnly", false);
    long premiumBonus = yaml.getLong(base + ".premiumBonusPoints", 0);
    String cadence = yaml.getString(base + ".cadence", "SEASON");
    List<Integer> weeks = yaml.getIntegerList(base + ".availableWeeks");
    List<Map<?, ?>> steps = yaml.getMapList(base + ".steps");

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.QUEST_DETAIL, 1, questId, null);
    Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.YELLOW + "Quest: " + questId);
    holder.bind(inv);

    inv.setItem(10, EditorItems.item(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
      ChatColor.GREEN + "Enabled: " + (enabled ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO"),
      EditorItems.lore(ChatColor.DARK_GRAY + "Click to toggle")));

    inv.setItem(11, EditorItems.item(Material.NAME_TAG, ChatColor.AQUA + "Name",
      EditorItems.lore(ChatColor.GRAY + name, " ", ChatColor.DARK_GRAY + "Click to edit in chat")));

    inv.setItem(12, EditorItems.item(Material.EMERALD, ChatColor.AQUA + "Points",
      EditorItems.lore(ChatColor.GRAY + String.valueOf(points), " ", ChatColor.DARK_GRAY + "Click to edit in chat")));

    inv.setItem(13, EditorItems.item(Material.GOLD_INGOT, ChatColor.AQUA + "Premium Only",
      EditorItems.lore(ChatColor.GRAY + (premiumOnly ? "YES" : "NO"), " ", ChatColor.DARK_GRAY + "Click to toggle")));

    inv.setItem(14, EditorItems.item(Material.DIAMOND, ChatColor.AQUA + "Premium Bonus Points",
      EditorItems.lore(ChatColor.GRAY + String.valueOf(premiumBonus), " ", ChatColor.DARK_GRAY + "Click to edit in chat")));

    inv.setItem(15, EditorItems.item(Material.CLOCK, ChatColor.AQUA + "Cadence",
      EditorItems.lore(ChatColor.GRAY + cadence, " ", ChatColor.DARK_GRAY + "Click to cycle")));

    inv.setItem(16, EditorItems.item(Material.PAPER, ChatColor.AQUA + "Available Weeks",
      EditorItems.lore(
        ChatColor.GRAY + (weeks == null || weeks.isEmpty() ? "(none)" : weeks.toString()),
        " ",
        ChatColor.DARK_GRAY + "Click to edit in chat",
        ChatColor.DARK_GRAY + "Example: 1,2,3  (or 'none')"
      )));

    inv.setItem(22, EditorItems.item(Material.BOOK, ChatColor.YELLOW + "Steps (" + (steps == null ? 0 : steps.size()) + ")",
      EditorItems.lore(ChatColor.DARK_GRAY + "Click to edit steps")));

    inv.setItem(26, EditorItems.navBack());
    p.openInventory(inv);
  }

  public void openQuestSteps(Player p, String questId, int page) {
    if (p == null || questId == null) return;
    int pg = Math.max(1, page);

    YamlConfiguration yaml = loadQuestsYaml();
    String base = "quests." + questId;
    List<Map<?, ?>> steps = yaml.getMapList(base + ".steps");
    if (steps == null) steps = List.of();

    int perPage = 45;
    int maxPage = Math.max(1, (int) Math.ceil(steps.size() / (double) perPage));
    pg = Math.min(pg, maxPage);

    int start = (pg - 1) * perPage;
    int end = Math.min(steps.size(), start + perPage);

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.QUEST_STEPS, pg, questId, null);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.YELLOW + "Steps: " + questId);
    holder.bind(inv);

    int slot = 0;
    for (int i = start; i < end; i++, slot++) {
      Map<?, ?> s = steps.get(i);
      String type = Objects.toString(s.getOrDefault("type", ""), "");
      String key = Objects.toString(s.getOrDefault("key", ""), "");
      String value = Objects.toString(s.getOrDefault("value", ""), "");
      long required = parseLong(s.get("required"), 1);

      inv.setItem(slot, EditorItems.item(Material.MAP,
        ChatColor.GOLD + "Step #" + (i + 1) + ChatColor.GRAY + " (" + type + ")",
        EditorItems.lore(
          ChatColor.GRAY + "key: " + ChatColor.AQUA + key,
          ChatColor.GRAY + "value: " + ChatColor.AQUA + value,
          ChatColor.GRAY + "required: " + ChatColor.AQUA + required,
          " ",
          ChatColor.DARK_GRAY + "Left: edit required",
          ChatColor.DARK_GRAY + "Shift+Left: edit value",
          ChatColor.DARK_GRAY + "Shift+Right: delete step"
        )));
    }

    inv.setItem(45, EditorItems.navPrev());
    inv.setItem(49, EditorItems.navBack());
    inv.setItem(53, EditorItems.item(Material.ANVIL, ChatColor.GREEN + "Add Step",
      EditorItems.lore(
        ChatColor.DARK_GRAY + "Click then type:",
        ChatColor.GRAY + "TYPE key value required",
        ChatColor.DARK_GRAY + "Example: BLOCK_BREAK material STONE 10"
      )));
    p.openInventory(inv);
  }

  public void openTierList(Player p, int page) {
    if (p == null) return;
    int pg = Math.max(1, page);

    YamlConfiguration yaml = loadRewardsYaml();
    ConfigurationSection tiers = yaml.getConfigurationSection("tiers");

    List<Integer> tierIds = new ArrayList<>();
    if (tiers != null) {
      for (String k : tiers.getKeys(false)) {
        try { tierIds.add(Integer.parseInt(k)); } catch (NumberFormatException ignored) {}
      }
    }
    tierIds.sort(Integer::compareTo);

    int perPage = 45;
    int maxPage = Math.max(1, (int) Math.ceil(tierIds.size() / (double) perPage));
    pg = Math.min(pg, maxPage);

    int start = (pg - 1) * perPage;
    int end = Math.min(tierIds.size(), start + perPage);

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.REWARD_TIER_LIST, pg, null, null);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Rewards Editor");
    holder.bind(inv);

    int slot = 0;
    for (int i = start; i < end; i++, slot++) {
      int tier = tierIds.get(i);
      String base = "tiers." + tier;
      long req = yaml.getLong(base + ".pointsRequired", tier * 100L);

      int freeCount = countKeys(yaml.getConfigurationSection(base + ".free"));
      int premCount = countKeys(yaml.getConfigurationSection(base + ".premium"));

      inv.setItem(slot, EditorItems.item(Material.CHEST,
        ChatColor.GREEN + "Tier " + tier,
        EditorItems.lore(
          ChatColor.GRAY + "Required: " + ChatColor.AQUA + req,
          ChatColor.GRAY + "Free rewards: " + ChatColor.AQUA + freeCount,
          ChatColor.GRAY + "Premium rewards: " + ChatColor.AQUA + premCount,
          " ",
          ChatColor.DARK_GRAY + "Left: details"
        )));
    }

    inv.setItem(45, EditorItems.navPrev());
    inv.setItem(49, EditorItems.navBack());
    inv.setItem(53, EditorItems.item(Material.ANVIL, ChatColor.GREEN + "Add Tier",
      EditorItems.lore(
        ChatColor.DARK_GRAY + "Click then type:",
        ChatColor.GRAY + "<tier> <pointsRequired>",
        ChatColor.DARK_GRAY + "Example: 12 2200"
      )));
    p.openInventory(inv);
  }

  public void openTierDetail(Player p, int tier) {
    if (p == null) return;
    tier = Math.max(1, tier);

    YamlConfiguration yaml = loadRewardsYaml();
    String base = "tiers." + tier;
    if (!yaml.isConfigurationSection(base)) {
      p.sendMessage(ChatColor.RED + "Tier not found: " + tier);
      openTierList(p, 1);
      return;
    }

    long req = yaml.getLong(base + ".pointsRequired", tier * 100L);
    int freeCount = countKeys(yaml.getConfigurationSection(base + ".free"));
    int premCount = countKeys(yaml.getConfigurationSection(base + ".premium"));

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.REWARD_TIER_DETAIL, 1, String.valueOf(tier), null);
    Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.AQUA + "Tier " + tier);
    holder.bind(inv);

    inv.setItem(11, EditorItems.item(Material.EMERALD, ChatColor.AQUA + "Points Required",
      EditorItems.lore(ChatColor.GRAY + String.valueOf(req), " ", ChatColor.DARK_GRAY + "Click to edit in chat")));

    inv.setItem(13, EditorItems.item(Material.CHEST_MINECART, ChatColor.YELLOW + "Free Rewards (" + freeCount + ")",
      EditorItems.lore(ChatColor.DARK_GRAY + "Click to view/edit")));

    inv.setItem(15, EditorItems.item(Material.MINECART, ChatColor.LIGHT_PURPLE + "Premium Rewards (" + premCount + ")",
      EditorItems.lore(ChatColor.DARK_GRAY + "Click to view/edit")));

    inv.setItem(26, EditorItems.navBack());
    p.openInventory(inv);
  }

  public void openRewardList(Player p, int tier, String track, int page) {
    if (p == null) return;
    int pg = Math.max(1, page);
    tier = Math.max(1, tier);
    track = normalizeTrack(track);

    YamlConfiguration yaml = loadRewardsYaml();
    String base = "tiers." + tier + "." + track;
    ConfigurationSection sec = yaml.getConfigurationSection(base);

    List<String> ids = new ArrayList<>();
    if (sec != null) ids.addAll(sec.getKeys(false));
    ids.sort(String.CASE_INSENSITIVE_ORDER);

    int perPage = 45;
    int maxPage = Math.max(1, (int) Math.ceil(ids.size() / (double) perPage));
    pg = Math.min(pg, maxPage);

    int start = (pg - 1) * perPage;
    int end = Math.min(ids.size(), start + perPage);

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.REWARD_LIST, pg, String.valueOf(tier), track);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Tier " + tier + " " + track);
    holder.bind(inv);

    int slot = 0;
    for (int i = start; i < end; i++, slot++) {
      String id = ids.get(i);
      ConfigurationSection r = sec == null ? null : sec.getConfigurationSection(id);
      if (r == null) continue;

      String type = r.getString("type", "COMMAND");
      int cmdCount = r.getStringList("commands").size();

      inv.setItem(slot, EditorItems.item(Material.PAPER,
        ChatColor.GREEN + id + ChatColor.GRAY + " (" + type + ")",
        EditorItems.lore(
          ChatColor.GRAY + "commands: " + ChatColor.AQUA + cmdCount,
          " ",
          ChatColor.DARK_GRAY + "Left: edit commands",
          ChatColor.DARK_GRAY + "Shift+Right: delete reward"
        )));
    }

    inv.setItem(45, EditorItems.navPrev());
    inv.setItem(49, EditorItems.navBack());
    inv.setItem(53, EditorItems.item(Material.ANVIL, ChatColor.GREEN + "Add Reward",
      EditorItems.lore(
        ChatColor.DARK_GRAY + "Click then type a console command.",
        ChatColor.DARK_GRAY + "A new reward id (rN) will be created."
      )));
    p.openInventory(inv);
  }

  public void openRewardCommands(Player p, int tier, String track, String rewardId, int page) {
    if (p == null || rewardId == null) return;
    int pg = Math.max(1, page);
    tier = Math.max(1, tier);
    track = normalizeTrack(track);

    YamlConfiguration yaml = loadRewardsYaml();
    String base = "tiers." + tier + "." + track + "." + rewardId;
    if (!yaml.isConfigurationSection(base)) {
      p.sendMessage(ChatColor.RED + "Reward not found: " + rewardId);
      openRewardList(p, tier, track, 1);
      return;
    }

    List<String> cmds = yaml.getStringList(base + ".commands");
    if (cmds == null) cmds = List.of();

    int perPage = 45;
    int maxPage = Math.max(1, (int) Math.ceil(cmds.size() / (double) perPage));
    pg = Math.min(pg, maxPage);

    int start = (pg - 1) * perPage;
    int end = Math.min(cmds.size(), start + perPage);

    BpEditorHolder holder = new BpEditorHolder(p.getUniqueId(), EditorMenuType.REWARD_COMMANDS, pg, String.valueOf(tier), track + ":" + rewardId);
    Inventory inv = Bukkit.createInventory(holder, 54, ChatColor.AQUA + "Commands: " + rewardId);
    holder.bind(inv);

    int slot = 0;
    for (int i = start; i < end; i++, slot++) {
      String cmd = cmds.get(i);
      String display = cmd.length() > 45 ? cmd.substring(0, 45) + "..." : cmd;

      inv.setItem(slot, EditorItems.item(Material.COMMAND_BLOCK,
        ChatColor.GRAY + display,
        EditorItems.lore(
          ChatColor.DARK_GRAY + cmd,
          " ",
          ChatColor.DARK_GRAY + "Shift+Right: remove"
        )));
    }

    inv.setItem(45, EditorItems.navPrev());
    inv.setItem(49, EditorItems.navBack());
    inv.setItem(53, EditorItems.item(Material.ANVIL, ChatColor.GREEN + "Add Command",
      EditorItems.lore(ChatColor.DARK_GRAY + "Click then type a console command.")));
    p.openInventory(inv);
  }

  // -----------------------------
  // Prompts
  // -----------------------------

  public void prompt(Player p, EditKind kind, String a, String b, int index, String instruction) {
    if (p == null) return;
    sessions.begin(p, kind, a, b, index);
    p.closeInventory();
    p.sendMessage(ChatColor.YELLOW + "Editor: " + ChatColor.GRAY + instruction);
    p.sendMessage(ChatColor.DARK_GRAY + "Type 'cancel' to abort.");
  }

  // -----------------------------
  // Apply chat input edits
  // -----------------------------

  public boolean handleChatInput(Player p, String msg) {
    if (p == null) return false;

    EditSession s = sessions.get(p.getUniqueId());
    if (s == null) return false;

    sessions.end(p.getUniqueId(), true);

    String in = msg == null ? "" : msg.trim();
    if (in.equalsIgnoreCase("cancel")) {
      reopenAfterCancel(p, s);
      return true;
    }

    // Only one destructive admin job at a time.
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopenAfterCancel(p, s);
      return true;
    }

    switch (s.kind()) {
      case QUEST_NAME -> editQuestStringAsync(p, s.a(), "name", in, () -> openQuestDetail(p, s.a()));
      case QUEST_POINTS -> editQuestLongAsync(p, s.a(), "points", in, 0, Long.MAX_VALUE, () -> openQuestDetail(p, s.a()));
      case QUEST_PREMIUM_BONUS -> editQuestLongAsync(p, s.a(), "premiumBonusPoints", in, 0, Long.MAX_VALUE, () -> openQuestDetail(p, s.a()));
      case QUEST_WEEKS -> editQuestWeeksAsync(p, s.a(), in, () -> openQuestDetail(p, s.a()));
      case STEP_REQUIRED -> editStepRequiredAsync(p, s.a(), s.index(), in, () -> openQuestSteps(p, s.a(), 1));
      case STEP_VALUE -> editStepValueAsync(p, s.a(), s.index(), in, () -> openQuestSteps(p, s.a(), 1));
      case STEP_ADD -> addStepAsync(p, s.a(), in, () -> openQuestSteps(p, s.a(), 1));

      case TIER_ADD -> addTierAsync(p, in, () -> openTierList(p, 1));
      case TIER_POINTS_REQUIRED -> editTierReqAsync(p, Integer.parseInt(s.a()), in, () -> openTierDetail(p, Integer.parseInt(s.a())));

      case REWARD_ADD -> addRewardAsync(p, Integer.parseInt(s.a()), s.b(), in, () -> openRewardList(p, Integer.parseInt(s.a()), s.b(), 1));
      case REWARD_ADD_COMMAND -> addRewardCommandAsync(p, Integer.parseInt(s.a()), s.b(), in, () -> {
        String[] parts = splitTrackReward(s.b());
        openRewardCommands(p, Integer.parseInt(s.a()), parts[0], parts[1], 1);
      });
    }

    return true;
  }

  private void reopenAfterCancel(Player p, EditSession s) {
    try {
      switch (s.kind()) {
        case QUEST_NAME, QUEST_POINTS, QUEST_PREMIUM_BONUS, QUEST_WEEKS -> openQuestDetail(p, s.a());
        case STEP_REQUIRED, STEP_VALUE, STEP_ADD -> openQuestSteps(p, s.a(), 1);
        case TIER_ADD -> openTierList(p, 1);
        case TIER_POINTS_REQUIRED -> openTierDetail(p, Integer.parseInt(s.a()));
        case REWARD_ADD -> openRewardList(p, Integer.parseInt(s.a()), s.b(), 1);
        case REWARD_ADD_COMMAND -> {
          String[] parts = splitTrackReward(s.b());
          openRewardCommands(p, Integer.parseInt(s.a()), parts[0], parts[1], 1);
        }
      }
    } catch (Exception ignored) {
      openHome(p);
    }
  }

  // -----------------------------
  // Write operations (async save, sync restart)
  // -----------------------------

  private void editQuestStringAsync(Player p, String questId, String field, String value, Runnable reopen) {
    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String base = "quests." + questId + "." + field;
        yaml.set(base, value);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved quest " + questId + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void editQuestLongAsync(Player p, String questId, String field, String input, long min, long max, Runnable reopen) {
    long v;
    try { v = Long.parseLong(input); }
    catch (NumberFormatException ex) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Invalid number.");
      reopen.run();
      return;
    }
    if (v < min) v = min;
    if (v > max) v = max;

    final long val = v;
    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String base = "quests." + questId + "." + field;
        yaml.set(base, val);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved quest " + questId + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void editQuestWeeksAsync(Player p, String questId, String input, Runnable reopen) {
    List<Integer> weeks = new ArrayList<>();
    String in = input.trim();
    if (!in.equalsIgnoreCase("none") && !in.equalsIgnoreCase("null") && !in.isBlank()) {
      String[] parts = in.split("[, ]+");
      for (String s : parts) {
        if (s.isBlank()) continue;
        try {
          int w = Integer.parseInt(s.trim());
          if (w >= 1) weeks.add(w);
        } catch (NumberFormatException ignored) {}
      }
      // unique + stable order
      LinkedHashSet<Integer> set = new LinkedHashSet<>(weeks);
      weeks = new ArrayList<>(set);
    } else {
      weeks = List.of();
    }

    final List<Integer> out = weeks;
    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        yaml.set("quests." + questId + ".availableWeeks", out);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved quest " + questId + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void editStepRequiredAsync(Player p, String questId, int stepIndex, String input, Runnable reopen) {
    long v;
    try { v = Long.parseLong(input); }
    catch (NumberFormatException ex) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Invalid number.");
      reopen.run();
      return;
    }
    if (v < 1) v = 1;

    final long val = v;
    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".steps";
        List<Map<?, ?>> steps = yaml.getMapList(path);
        if (stepIndex < 0 || stepIndex >= steps.size()) throw new IllegalArgumentException("step index out of range");

        Map<Object, Object> m = new LinkedHashMap<>();
        m.putAll((Map<?, ?>) steps.get(stepIndex));
        m.put("required", val);

        List<Object> out = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) out.add(i == stepIndex ? m : steps.get(i));

        yaml.set(path, out);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved step.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void editStepValueAsync(Player p, String questId, int stepIndex, String input, Runnable reopen) {
    String val = input.trim();
    if (val.isBlank()) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Value cannot be empty.");
      reopen.run();
      return;
    }

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".steps";
        List<Map<?, ?>> steps = yaml.getMapList(path);
        if (stepIndex < 0 || stepIndex >= steps.size()) throw new IllegalArgumentException("step index out of range");

        Map<Object, Object> m = new LinkedHashMap<>();
        m.putAll((Map<?, ?>) steps.get(stepIndex));
        m.put("value", val);

        List<Object> out = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) out.add(i == stepIndex ? m : steps.get(i));

        yaml.set(path, out);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved step.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  private void addStepAsync(Player p, String questId, String input, Runnable reopen) {
    String[] parts;
    if (input.contains("|")) parts = input.split("\\|");
    else parts = input.split("\\s+");

    if (parts.length < 4) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Format: TYPE key value required");
      reopen.run();
      return;
    }

    String type = parts[0].trim();
    String key = parts[1].trim();
    String value = parts[2].trim();
    long required;
    try { required = Long.parseLong(parts[3].trim()); }
    catch (NumberFormatException ex) { required = 1; }
    if (required < 1) required = 1;

    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type);
    m.put("key", key);
    m.put("value", value);
    m.put("required", required);

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".steps";
        List<Object> out = new ArrayList<>(yaml.getMapList(path));
        out.add(m);
        yaml.set(path, out);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Added step.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void addTierAsync(Player p, String input, Runnable reopen) {
    String[] parts = input.split("\\s+");
    if (parts.length < 2) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Format: <tier> <pointsRequired>");
      reopen.run();
      return;
    }

    int tier;
    long req;
    try { tier = Integer.parseInt(parts[0]); } catch (NumberFormatException ex) { tier = -1; }
    try { req = Long.parseLong(parts[1]); } catch (NumberFormatException ex) { req = -1; }

    if (tier < 1 || req < 0) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Invalid values.");
      reopen.run();
      return;
    }

    final int t = tier;
    final long r = req;

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        String base = "tiers." + t;
        if (yaml.isConfigurationSection(base)) throw new IllegalArgumentException("tier already exists");

        yaml.set(base + ".pointsRequired", r);
        // create empty sections (optional)
        yaml.createSection(base + ".free");
        yaml.createSection(base + ".premium");

        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Added tier " + t + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void editTierReqAsync(Player p, int tier, String input, Runnable reopen) {
    long req;
    try { req = Long.parseLong(input); }
    catch (NumberFormatException ex) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Invalid number.");
      reopen.run();
      return;
    }
    if (req < 0) req = 0;
    final long r = req;
    final int t = Math.max(1, tier);

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        yaml.set("tiers." + t + ".pointsRequired", r);
        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved tier " + t + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void addRewardAsync(Player p, int tier, String track, String command, Runnable reopen) {
    track = normalizeTrack(track);
    String cmd = command.trim();
    if (cmd.isBlank()) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Command cannot be empty.");
      reopen.run();
      return;
    }

    final String t = track;
    final int tr = tier;

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        String base = "tiers." + tr + "." + t;
        ConfigurationSection sec = yaml.getConfigurationSection(base);
        if (sec == null) sec = yaml.createSection(base);

        String id = nextRewardId(sec);
        yaml.set(base + "." + id + ".type", "COMMAND");
        yaml.set(base + "." + id + ".commands", List.of(cmd));

        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Added reward " + id + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  private void addRewardCommandAsync(Player p, int tier, String trackReward, String command, Runnable reopen) {
    String cmd = command.trim();
    if (cmd.isBlank()) {
      adminJobRunning.set(false);
      p.sendMessage(ChatColor.RED + "Command cannot be empty.");
      reopen.run();
      return;
    }

    String[] parts = splitTrackReward(trackReward);
    String track = parts[0];
    String rewardId = parts[1];

    final int tr = tier;
    final String tk = track;
    final String rid = rewardId;

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        String base = "tiers." + tr + "." + tk + "." + rid;
        List<String> cmds = yaml.getStringList(base + ".commands");
        if (cmds == null) cmds = new ArrayList<>();
        List<String> out = new ArrayList<>(cmds);
        out.add(cmd);

        yaml.set(base + ".type", "COMMAND");
        yaml.set(base + ".commands", out);

        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Added command.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  // -----------------------------
  // Click helpers (called by listener)
  // -----------------------------

  public void toggleQuestEnabled(Player p, String questId, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".enabled";
        boolean cur = yaml.getBoolean(path, true);
        yaml.set(path, !cur);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Quest " + questId + " enabled=" + (!cur));
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  public void toggleQuestBool(Player p, String questId, String field, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + "." + field;
        boolean cur = yaml.getBoolean(path, false);
        yaml.set(path, !cur);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Saved.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  public void cycleQuestCadence(Player p, String questId, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".cadence";
        String raw = yaml.getString(path, "SEASON").trim().toUpperCase(Locale.ROOT);

        List<String> values = List.of("SEASON", "DAILY", "WEEKLY");
        int idx = values.indexOf(raw);
        String next = values.get((idx + 1) < 0 ? 0 : (idx + 1) % values.size());

        yaml.set(path, next);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Cadence set to " + next + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Save failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  @SuppressWarnings("unchecked")
  public void deleteStep(Player p, String questId, int stepIndex, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadQuestsYaml();
        String path = "quests." + questId + ".steps";
        List<Map<?, ?>> steps = yaml.getMapList(path);
        if (stepIndex < 0 || stepIndex >= steps.size()) throw new IllegalArgumentException("step index out of range");
        if (steps.size() <= 1) throw new IllegalStateException("cannot delete last step");

        List<Object> out = new ArrayList<>(steps.size() - 1);
        for (int i = 0; i < steps.size(); i++) if (i != stepIndex) out.add(steps.get(i));

        yaml.set(path, out);
        store.saveQuestsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Deleted step.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Delete failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  public void deleteReward(Player p, int tier, String track, String rewardId, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    int t = Math.max(1, tier);
    String tr = normalizeTrack(track);

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        String base = "tiers." + t + "." + tr + "." + rewardId;
        if (!yaml.isConfigurationSection(base)) throw new IllegalArgumentException("reward not found");
        yaml.set(base, null);

        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Deleted reward " + rewardId + ".");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Delete failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  public void deleteCommand(Player p, int tier, String track, String rewardId, int cmdIndex, Runnable reopen) {
    if (!adminJobRunning.compareAndSet(false, true)) {
      p.sendMessage(ChatColor.RED + "Another BattlePass admin job is running.");
      reopen.run();
      return;
    }

    int t = Math.max(1, tier);
    String tr = normalizeTrack(track);

    zakum.async().execute(() -> {
      try {
        YamlConfiguration yaml = loadRewardsYaml();
        String base = "tiers." + t + "." + tr + "." + rewardId;
        List<String> cmds = yaml.getStringList(base + ".commands");
        if (cmds == null || cmds.isEmpty()) throw new IllegalStateException("no commands");
        if (cmdIndex < 0 || cmdIndex >= cmds.size()) throw new IllegalArgumentException("index out of range");
        if (cmds.size() <= 1) throw new IllegalStateException("cannot delete last command");

        List<String> out = new ArrayList<>(cmds.size() - 1);
        for (int i = 0; i < cmds.size(); i++) if (i != cmdIndex) out.add(cmds.get(i));
        yaml.set(base + ".commands", out);

        store.saveRewardsAtomic(yaml);

        Bukkit.getScheduler().runTask(plugin, () -> {
          plugin.restartRuntimeForYamlChange(null);
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.GREEN + "Removed command.");
          reopen.run();
        });
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          adminJobRunning.set(false);
          p.sendMessage(ChatColor.RED + "Remove failed: " + ex.getMessage());
          reopen.run();
        });
      }
    });
  }

  // -----------------------------
  // YAML load helpers
  // -----------------------------

  private YamlConfiguration loadQuestsYaml() {
    File f = store.questsFile().toFile();
    return YamlConfiguration.loadConfiguration(f);
  }

  private YamlConfiguration loadRewardsYaml() {
    File f = store.rewardsFile().toFile();
    return YamlConfiguration.loadConfiguration(f);
  }

  // -----------------------------
  // Utils
  // -----------------------------

  private static long parseLong(Object v, long def) {
    if (v == null) return def;
    if (v instanceof Number n) return n.longValue();
    try { return Long.parseLong(String.valueOf(v)); }
    catch (NumberFormatException ignored) { return def; }
  }

  private static int countKeys(ConfigurationSection sec) {
    if (sec == null) return 0;
    try { return sec.getKeys(false).size(); }
    catch (Throwable t) { return 0; }
  }

  private static String normalizeTrack(String t) {
    if (t == null) return "free";
    String s = t.trim().toLowerCase(Locale.ROOT);
    if (s.startsWith("p")) return "premium";
    return "free";
  }

  private static String nextRewardId(ConfigurationSection sec) {
    int max = 0;
    for (String k : sec.getKeys(false)) {
      String raw = k.toLowerCase(Locale.ROOT);
      if (!raw.startsWith("r")) continue;
      try {
        int n = Integer.parseInt(raw.substring(1));
        if (n > max) max = n;
      } catch (NumberFormatException ignored) {}
    }
    return "r" + (max + 1);
  }

  private static String[] splitTrackReward(String trackReward) {
    if (trackReward == null) return new String[]{"free", "r1"};
    int idx = trackReward.indexOf(':');
    if (idx <= 0 || idx >= trackReward.length() - 1) return new String[]{normalizeTrack(trackReward), "r1"};
    String track = normalizeTrack(trackReward.substring(0, idx));
    String rid = trackReward.substring(idx + 1);
    return new String[]{track, rid};
  }
}
