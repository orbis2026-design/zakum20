package net.orbis.zakum.api.packets;

@FunctionalInterface
public interface PacketHookHandler {
  void handle(PacketContext ctx) throws Exception;
}
