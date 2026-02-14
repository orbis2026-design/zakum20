package net.orbis.zakum.api.capability;

import net.orbis.zakum.api.actions.ActionBus;
import net.orbis.zakum.api.actions.DeferredActionService;
import net.orbis.zakum.api.boosters.BoosterService;
import net.orbis.zakum.api.entitlements.EntitlementService;
import net.orbis.zakum.api.luckperms.LuckPermsService;
import net.orbis.zakum.api.net.ControlPlaneClient;
import net.orbis.zakum.api.packets.PacketService;
import net.orbis.zakum.api.placeholders.PlaceholderService;
import net.orbis.zakum.api.social.SocialService;
import net.orbis.zakum.api.storage.DataStore;
import net.orbis.zakum.api.vault.EconomyService;

/**
 * Standard Zakum capabilities.
 *
 * Feature plugins should prefer these keys for optional integration lookups.
 */
public final class ZakumCapabilities {

  private ZakumCapabilities() {}

  public static final Capability<ActionBus> ACTION_BUS =
    Capability.of("zakum:action_bus", ActionBus.class);
  public static final Capability<DeferredActionService> DEFERRED_ACTIONS =
    Capability.of("zakum:deferred_actions", DeferredActionService.class);
  public static final Capability<EntitlementService> ENTITLEMENTS =
    Capability.of("zakum:entitlements", EntitlementService.class);
  public static final Capability<BoosterService> BOOSTERS =
    Capability.of("zakum:boosters", BoosterService.class);
  public static final Capability<ControlPlaneClient> CONTROL_PLANE =
    Capability.of("zakum:control_plane", ControlPlaneClient.class);
  public static final Capability<DataStore> DATA_STORE =
    Capability.of("zakum:data_store", DataStore.class);
  public static final Capability<SocialService> SOCIAL =
    Capability.of("zakum:social", SocialService.class);

  public static final Capability<PacketService> PACKETS =
    Capability.of("zakum:packets", PacketService.class);
  public static final Capability<PlaceholderService> PLACEHOLDERS =
    Capability.of("zakum:placeholders", PlaceholderService.class);
  public static final Capability<EconomyService> ECONOMY =
    Capability.of("zakum:economy", EconomyService.class);
  public static final Capability<LuckPermsService> LUCKPERMS =
    Capability.of("zakum:luckperms", LuckPermsService.class);
}
