package net.orbis.zakum.battlepass.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.orbis.zakum.battlepass.BattlePassRuntime;
import net.orbis.zakum.battlepass.model.QuestDef;
import net.orbis.zakum.battlepass.model.QuestStep;
import net.orbis.zakum.battlepass.state.PlayerBpState;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * PlaceholderAPI expansion for OrbisBattlePass.
 *
 * Identifier: %orbisbp_<param>%
 */
public final class OrbisBattlePassExpansion extends PlaceholderExpansion {

  private final BattlePassRuntime runtime;

  public OrbisBattlePassExpansion(BattlePassRuntime runtime) {
    this.runtime = runtime;
  }

  @Override
  public @NotNull String getIdentifier() {
    return "orbisbp";
  }

  @Override
  public @NotNull String getAuthor() {
    return "Orbis Network";
  }

  @Override
  public @NotNull String getVersion() {
    return "1";
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public boolean canRegister() {
    return true;
  }

  @Override
  public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
    if (player == null) return "";

    String p = params.toLowerCase(Locale.ROOT);

    PlayerBpState st = runtime.state(player.getUniqueId());

    if (p.equals("tier")) return st == null ? "0" : String.valueOf(st.tier());
    if (p.equals("points")) return st == null ? "0" : String.valueOf(st.points());
    if (p.equals("premium")) return st != null && st.premium ? "yes" : "no";
    if (p.equals("season")) return String.valueOf(runtime.season());
    if (p.equals("server")) return runtime.progressServerId();

    // quest_<id>_<field>
    if (p.startsWith("quest_")) {
      String rest = params.substring("quest_".length());
      int idx = rest.lastIndexOf('_');
      if (idx <= 0 || idx >= rest.length() - 1) return "";
      String questId = rest.substring(0, idx);
      String field = rest.substring(idx + 1).toLowerCase(Locale.ROOT);

      Optional<QuestDef> q = runtime.quest(questId);
      if (q.isEmpty()) return "";

      PlayerBpState.StepStateSnap ss = st == null ? new PlayerBpState.StepStateSnap(0, 0) : st.getQuest(questId);

      int stepIdx = ss.stepIdx();
      long progress = ss.progress();

      if (field.equals("step")) return String.valueOf(stepIdx + 1);
      if (field.equals("progress")) return String.valueOf(progress);

      if (field.equals("required")) {
        int steps = q.get().steps().size();
        if (steps == 0) return "0";
        int s = Math.min(stepIdx, steps - 1);
        QuestStep step = q.get().steps().get(s);
        return String.valueOf(step.required());
      }
    }

    return "";
  }
}
