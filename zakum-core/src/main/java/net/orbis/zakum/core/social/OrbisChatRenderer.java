package net.orbis.zakum.core.social;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.orbis.zakum.api.asset.AssetManager;
import org.bukkit.entity.Player;

public final class OrbisChatRenderer {

  private static final String PRIMARY = "#44FFCC";
  private static final String SECONDARY = "#33AAFF";
  private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
  private static final MiniMessage MINI = MiniMessage.miniMessage();

  private final AssetManager assets;
  private final ChatBufferCache bufferCache;

  public OrbisChatRenderer(AssetManager assets, ChatBufferCache bufferCache) {
    this.assets = assets;
    this.bufferCache = bufferCache;
  }

  public Component resolveMessage(Component original) {
    String plain = PLAIN.serialize(original);
    if (plain == null || plain.isBlank()) return original;

    String resolved = themed(assets.resolve(plain));
    if (resolved == null || resolved.isBlank()) return original;
    return bufferCache.parse(resolved);
  }

  public Component renderLine(Player source, Component message) {
    Component name = renderName(source);
    Component separator = bufferCache.parse("<gray> » </gray>");
    return Component.empty().append(name).append(separator).append(message);
  }

  private Component renderName(Player source) {
    String escaped = MINI.escapeTags(source.getName());
    String format = themed("<gradient:<primary>:<secondary>>" + escaped + "</gradient>");
    return bufferCache.parse(format);
  }

  private static String themed(String input) {
    if (input == null) return null;
    return input
      .replace("<primary>", PRIMARY)
      .replace("<secondary>", SECONDARY);
  }
}
