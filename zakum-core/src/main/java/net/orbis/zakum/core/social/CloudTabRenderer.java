package net.orbis.zakum.core.social;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.orbis.zakum.api.ZakumApi;
import net.orbis.zakum.api.asset.AssetManager;
import net.orbis.zakum.core.util.PdcKeys;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public final class CloudTabRenderer {

  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private final ZakumApi api;
  private final AssetManager assets;

  public CloudTabRenderer(ZakumApi api, AssetManager assets) {
    this.api = api;
    this.assets = assets;
  }

  public void render(Player player) {
    if (player == null || !player.isOnline()) return;

    String rank = player.getPersistentDataContainer().getOrDefault(PdcKeys.CLOUD_RANK, PersistentDataType.STRING, "DEFAULT");
    String linked = player.getPersistentDataContainer().getOrDefault(PdcKeys.CLOUD_DISCORD_LINKED, PersistentDataType.BYTE, (byte) 0) == (byte) 1
      ? "<green>Linked</green>"
      : "<red>Unlinked</red>";

    String headerRaw = assets.resolve("<gradient:#44FFCC:#33AAFF>:logo: ORBIS NETWORK</gradient>");
    String footerRaw = assets.resolve("<gray>Server: <aqua>" + api.server().serverId()
      + "</aqua>  <gray>Rank: <gold>" + rank + "</gold>  <gray>Discord: " + linked);

    player.sendPlayerListHeader(MINI.deserialize(headerRaw));
    player.sendPlayerListFooter(MINI.deserialize(footerRaw));
  }
}
