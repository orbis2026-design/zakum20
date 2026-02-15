package net.orbis.zakum.api;

import net.orbis.zakum.api.capability.Capability;
import net.orbis.zakum.api.capability.ZakumCapabilities;
import net.orbis.zakum.api.concurrent.ZakumScheduler;
import net.orbis.zakum.api.config.ZakumSettings;
import net.orbis.zakum.api.plugin.ZakumPluginBase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ApiCompatibilitySmokeTest {

  @Test
  void capabilityIdsAreNamespacedAndUnique() throws IllegalAccessException {
    Set<String> ids = new HashSet<>();
    int count = 0;
    for (Field field : ZakumCapabilities.class.getDeclaredFields()) {
      int modifiers = field.getModifiers();
      if (!Modifier.isStatic(modifiers) || !Capability.class.isAssignableFrom(field.getType())) continue;
      field.setAccessible(true);
      Capability<?> capability = (Capability<?>) field.get(null);
      assertNotNull(capability, "Capability field should not be null: " + field.getName());
      String id = capability.id();
      assertTrue(id.startsWith("zakum:"), "Capability id must be namespaced: " + id);
      assertTrue(ids.add(id), "Duplicate capability id: " + id);
      count++;
    }
    assertTrue(count >= 10, "Expected a baseline capability surface.");
  }

  @Test
  void zakumApiExposesRequiredCoreAccessors() {
    Set<String> methodNames = new HashSet<>();
    for (Method method : ZakumApi.class.getMethods()) {
      methodNames.add(method.getName());
    }
    assertTrue(methodNames.contains("plugin"));
    assertTrue(methodNames.contains("server"));
    assertTrue(methodNames.contains("clock"));
    assertTrue(methodNames.contains("async"));
    assertTrue(methodNames.contains("database"));
    assertTrue(methodNames.contains("actions"));
    assertTrue(methodNames.contains("entitlements"));
    assertTrue(methodNames.contains("boosters"));
    assertTrue(methodNames.contains("getAceEngine"));
    assertTrue(methodNames.contains("capabilities"));
    assertTrue(methodNames.contains("capability"));
    assertTrue(methodNames.contains("getCapabilityRegistry"));
    assertTrue(methodNames.contains("getScheduler"));
    assertTrue(methodNames.contains("getStorage"));
    assertTrue(methodNames.contains("getAnimations"));
    assertTrue(methodNames.contains("getBridgeManager"));
    assertTrue(methodNames.contains("getProgression"));
    assertTrue(methodNames.contains("getAssetManager"));
    assertTrue(methodNames.contains("getGui"));
    assertTrue(methodNames.contains("controlPlane"));
    assertTrue(methodNames.contains("settings"));
    assertTrue(methodNames.contains("chatBuffer"));
  }

  @Test
  void schedulerContractIncludesFoliaSafePrimitives() {
    Set<String> signatures = new HashSet<>();
    for (Method method : ZakumScheduler.class.getMethods()) {
      signatures.add(method.getName() + "#" + method.getParameterCount());
    }
    assertTrue(signatures.contains("runAsync#1"));
    assertTrue(signatures.contains("runAtLocation#2"));
    assertTrue(signatures.contains("runAtEntity#2"));
    assertTrue(signatures.contains("runGlobal#1"));
    assertTrue(signatures.contains("supplyAsync#1"));
  }

  @Test
  void settingsContainDatastoreSection() {
    RecordComponent[] root = ZakumSettings.class.getRecordComponents();
    RecordComponent[] dataStore = ZakumSettings.DataStore.class.getRecordComponents();
    assertNotNull(root);
    assertNotNull(dataStore);
    assertEquals(5, dataStore.length, "Datastore section should stay explicit and typed.");
  }

  @Test
  void pluginBootstrapBaseRemainsAvailable() {
    Set<String> methodNames = new HashSet<>();
    for (Method method : ZakumPluginBase.class.getDeclaredMethods()) {
      methodNames.add(method.getName());
    }
    assertTrue(methodNames.contains("onZakumEnable"));
    assertTrue(methodNames.contains("onZakumDisable"));
    assertTrue(methodNames.contains("zakum"));
    assertTrue(methodNames.contains("optionalService"));
    assertTrue(methodNames.contains("requiredService"));
  }
}
