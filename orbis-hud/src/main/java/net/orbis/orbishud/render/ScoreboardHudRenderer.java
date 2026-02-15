package net.orbis.orbishud.render;

import net.orbis.orbishud.config.HudProfile;
import net.orbis.orbishud.state.HudPlayerState;
import net.orbis.zakum.api.util.BrandingText;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ScoreboardHudRenderer {

  private static final String OBJECTIVE_ID = "orbishud";
  private static final int MAX_ENTRY_LENGTH = 40;
  private static final int MAX_LINES = 15;
  private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
  private static final ChatColor[] COLOR_POOL = {
    ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
    ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD,
    ChatColor.GRAY, ChatColor.DARK_GRAY, ChatColor.BLUE,
    ChatColor.GREEN, ChatColor.AQUA, ChatColor.RED,
    ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
  };

  public boolean render(Player player, HudProfile profile, HudPlayerState state, String serverId, boolean hideWhenInSpectator) {
    if (hideWhenInSpectator && player.getGameMode() == GameMode.SPECTATOR) {
      clear(player, state);
      return false;
    }

    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager == null) return false;

    Scoreboard scoreboard = state.scoreboard();
    if (scoreboard == null) {
      scoreboard = manager.getNewScoreboard();
    }

    Objective objective = state.objective();
    if (objective == null || objective.getScoreboard() != scoreboard) {
      objective = scoreboard.getObjective(OBJECTIVE_ID);
      if (objective == null) {
        objective = scoreboard.registerNewObjective(OBJECTIVE_ID, "dummy", color(profile.title()));
      }
      objective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }

    String title = truncate(color(profile.title()), 64);
    if (!title.equals(objective.getDisplayName())) {
      objective.setDisplayName(title);
    }

    List<String> renderedEntries = buildEntries(profile, player, serverId);
    String signature = title + "|" + String.join("|", renderedEntries);
    if (signature.equals(state.lastSignature())) {
      if (player.getScoreboard() != scoreboard) {
        player.setScoreboard(scoreboard);
      }
      return false;
    }

    Set<String> newEntries = new LinkedHashSet<>(renderedEntries);
    for (String old : state.lastEntries()) {
      if (!newEntries.contains(old)) {
        scoreboard.resetScores(old);
      }
    }

    int score = renderedEntries.size();
    for (String entry : renderedEntries) {
      objective.getScore(entry).setScore(score--);
    }

    if (player.getScoreboard() != scoreboard) {
      player.setScoreboard(scoreboard);
    }

    state.updateRenderState(scoreboard, objective, signature, newEntries);
    return true;
  }

  public void clear(Player player, HudPlayerState state) {
    Scoreboard scoreboard = state.scoreboard();
    Objective objective = state.objective();
    if (objective != null && scoreboard != null) {
      for (String old : state.lastEntries()) {
        scoreboard.resetScores(old);
      }
      try {
        objective.unregister();
      } catch (IllegalStateException ignored) {
        // Objective already unregistered by scoreboard lifecycle.
      }
    }

    ScoreboardManager manager = Bukkit.getScoreboardManager();
    if (manager != null && scoreboard != null && player.getScoreboard() == scoreboard) {
      player.setScoreboard(manager.getMainScoreboard());
    }
    state.clearRenderState();
  }

  private List<String> buildEntries(HudProfile profile, Player player, String serverId) {
    List<String> entries = new ArrayList<>();
    Set<String> used = new HashSet<>();
    int index = 0;
    for (String line : profile.lines()) {
      if (entries.size() >= MAX_LINES) break;
      String replaced = replacePlaceholders(line, player, serverId);
      String colored = truncate(color(replaced), MAX_ENTRY_LENGTH);
      String unique = makeUnique(colored, used, index++);
      entries.add(unique);
    }
    return entries;
  }

  private String replacePlaceholders(String line, Player player, String serverId) {
    String out = line;
    out = out.replace("%player%", player.getName());
    out = out.replace("%display_name%", player.getDisplayName());
    out = out.replace("%world%", player.getWorld().getName());
    out = out.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
    out = out.replace("%x%", String.valueOf(player.getLocation().getBlockX()));
    out = out.replace("%y%", String.valueOf(player.getLocation().getBlockY()));
    out = out.replace("%z%", String.valueOf(player.getLocation().getBlockZ()));
    out = out.replace("%health%", String.valueOf((int) Math.floor(player.getHealth())));
    out = out.replace("%max_health%", String.valueOf((int) Math.floor(player.getMaxHealth())));
    out = out.replace("%time%", LocalTime.now(ZoneOffset.UTC).format(TIME_FORMAT));
    out = out.replace("%server_id%", serverId == null ? "unknown" : serverId);
    out = out.replace("%rank%", readRank(player));

    String ping = "0";
    try {
      ping = String.valueOf(Math.max(0, player.getPing()));
    } catch (Throwable ignored) {
      // Keep fallback.
    }
    out = out.replace("%ping%", ping);
    return out;
  }

  private static String makeUnique(String base, Set<String> used, int saltSeed) {
    String candidate = base == null || base.isBlank() ? " " : base;
    int salt = saltSeed;
    while (used.contains(candidate)) {
      ChatColor code = COLOR_POOL[Math.abs(salt++) % COLOR_POOL.length];
      candidate = truncate(candidate + code, MAX_ENTRY_LENGTH);
    }
    used.add(candidate);
    return candidate;
  }

  private static String color(String input) {
    return BrandingText.render(input);
  }

  private static String truncate(String input, int max) {
    if (input == null) return "";
    if (input.length() <= max) return input;
    return input.substring(0, max);
  }

  private static String readRank(Player player) {
    NamespacedKey rankKey = new NamespacedKey("orbis", "cloud_rank");
    String rank = player.getPersistentDataContainer().get(rankKey, PersistentDataType.STRING);
    return rank == null || rank.isBlank() ? "Cirrus" : rank;
  }
}
