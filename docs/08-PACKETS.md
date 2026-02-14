# Packets (ZakumPackets + PacketEvents)

ZakumPackets provides a **stable packet hook API** (`PacketService`) so feature plugins
can observe/cancel packets without directly depending on PacketEvents.

## Components

- `zakum-api`:
  - `net.orbis.zakum.api.packets.*` (PacketService, PacketHook, PacketContext, etc.)

- `zakum-packets`:
  - Bukkit plugin `ZakumPackets`
  - Depends on `Zakum` + `PacketEvents` plugins
  - Registers `PacketService` via ServicesManager

## Why a packet abstraction?

Packet libraries are:
- version-sensitive
- security sensitive
- easy to misuse (main-thread calls from Netty thread)

By centralizing through Zakum:
- feature plugins get a small safe surface
- we can swap backends later (PacketEvents / ProtocolLib / custom)

## Threading rules

Packet hooks run on PacketEvents' packet thread.

DO NOT:
- call Bukkit world APIs
- load chunks
- send messages
- modify inventories directly

DO:
- flip a boolean in memory
- schedule sync tasks for any Bukkit interaction
- perform cheap filtering early (exact packet allowlist)

## Config

In `plugins/Zakum/config.yml`:

```yml
packets:
  enabled: false
  backend: "PACKETEVENTS"
  inbound: true
  outbound: true
  maxHooksPerPlugin: 64
```

Operational posture:
- keep disabled unless you actively need packets
- cap hooks per plugin to prevent runaway registrations

## Example

```java
PacketService ps = Bukkit.getServicesManager().load(PacketService.class);
if (ps != null) {
  ps.registerHook(this, new PacketHook(
    PacketDirection.INBOUND,
    PacketHookPriority.NORMAL,
    java.util.Set.of("CHAT"),
    ctx -> {
      // Packet thread: compute only
      // Bukkit.getScheduler().runTask(plugin, () -> ...);
    }
  ));
}
```


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*