package net.orbis.zakum.bridge.luckperms;

import net.orbis.zakum.api.luckperms.LuckPermsService;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LuckPermsServiceImpl implements LuckPermsService {

  private final LuckPerms lp;

  LuckPermsServiceImpl(LuckPerms lp) {
    this.lp = Objects.requireNonNull(lp, "lp");
  }

  @Override
  public boolean available() {
    return true;
  }

  @Override
  public String prefix(Player player) {
    if (player == null) return "";
    try {
      var meta = lp.getPlayerAdapter(Player.class).getMetaData(player);
      String p = meta.getPrefix();
      return p == null ? "" : p;
    } catch (Exception ignored) {
      return "";
    }
  }

  @Override
  public String suffix(Player player) {
    if (player == null) return "";
    try {
      var meta = lp.getPlayerAdapter(Player.class).getMetaData(player);
      String s = meta.getSuffix();
      return s == null ? "" : s;
    } catch (Exception ignored) {
      return "";
    }
  }

  @Override
  public CompletableFuture<String> prefix(UUID uuid) {
    return load(uuid).thenApply(user -> {
      var meta = user.getCachedData().getMetaData(queryOptions(user));
      String p = meta.getPrefix();
      return p == null ? "" : p;
    });
  }

  @Override
  public CompletableFuture<String> suffix(UUID uuid) {
    return load(uuid).thenApply(user -> {
      var meta = user.getCachedData().getMetaData(queryOptions(user));
      String s = meta.getSuffix();
      return s == null ? "" : s;
    });
  }

  @Override
  public CompletableFuture<String> primaryGroup(UUID uuid) {
    return load(uuid).thenApply(user -> {
      String g = user.getPrimaryGroup();
      return g == null ? "" : g;
    });
  }

  @Override
  public CompletableFuture<Boolean> inGroup(UUID uuid, String groupName) {
    if (groupName == null || groupName.isBlank()) return CompletableFuture.completedFuture(false);

    final String wanted = groupName.trim().toLowerCase(Locale.ROOT);

    return load(uuid).thenApply(user -> user.getInheritedGroups(queryOptions(user)).stream()
      .anyMatch(g -> g.getName().equalsIgnoreCase(wanted))
    );
  }

  private CompletableFuture<User> load(UUID uuid) {
    Objects.requireNonNull(uuid, "uuid");

    User cached = lp.getUserManager().getUser(uuid);
    if (cached != null) return CompletableFuture.completedFuture(cached);

    return lp.getUserManager().loadUser(uuid);
  }

  private QueryOptions queryOptions(User user) {
    return lp.getContextManager().getQueryOptions(user)
      .orElseGet(() -> lp.getContextManager().getStaticQueryOptions());
  }
}
