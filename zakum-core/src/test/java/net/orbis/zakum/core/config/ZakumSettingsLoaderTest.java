package net.orbis.zakum.core.config;

import net.orbis.zakum.api.config.ZakumSettings;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ZakumSettingsLoader configuration parsing.
 * 
 * Verifies:
 * - Default value loading
 * - Value clamping (ranges)
 * - Invalid config handling
 * - All config sections parse correctly
 * - Edge cases (nulls, empty strings, out of range)
 */
class ZakumSettingsLoaderTest {

    @Test
    void testLoadMinimalConfig() {
        // Given: Minimal valid config
        String yaml = """
            server:
              id: "test-server"
            database:
              enabled: true
              host: "localhost"
              port: 3306
              database: "zakum_test"
              user: "root"
              password: ""
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should load with defaults
        assertNotNull(settings);
        assertEquals("test-server", settings.server().id());
        assertTrue(settings.database().enabled());
    }

    @Test
    void testServerId_DefaultsToUnknown() {
        // Given: Config without server.id
        String yaml = """
            database:
              enabled: true
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should default to "unknown"
        assertEquals("unknown", settings.server().id());
    }

    @Test
    void testServerId_BlankDefaultsToUnknown() {
        // Given: Config with blank server.id
        String yaml = """
            server:
              id: "  "
            database:
              enabled: true
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should default to "unknown"
        assertEquals("unknown", settings.server().id());
    }

    @Test
    void testDatabasePort_Clamped() {
        // Given: Config with various port values
        String[] yamls = {
            "database:\n  port: 0",        // Too low
            "database:\n  port: 1",        // Min valid
            "database:\n  port: 65535",    // Max valid
            "database:\n  port: 65536"     // Too high
        };
        
        int[] expected = { 1, 1, 65535, 65535 };
        
        // When/Then: Each should be clamped appropriately
        for (int i = 0; i < yamls.length; i++) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yamls[i])
            );
            ZakumSettings settings = ZakumSettingsLoader.load(config);
            assertEquals(expected[i], settings.database().port(),
                "Port should be clamped for: " + yamls[i]);
        }
    }

    @Test
    void testDatabasePoolSize_Clamped() {
        // Given: Config with out-of-range pool sizes
        String yaml = """
            database:
              pool:
                maxPoolSize: 0
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should clamp to minimum of 1
        assertTrue(settings.database().pool().maxPoolSize() >= 1);
        assertTrue(settings.database().pool().maxPoolSize() <= 100);
    }

    @Test
    void testDatabaseDefaults() {
        // Given: Empty config
        YamlConfiguration config = new YamlConfiguration();
        
        // When: Load configuration
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should use sensible defaults
        assertTrue(settings.database().enabled());
        assertEquals("127.0.0.1", settings.database().host());
        assertEquals(3306, settings.database().port());
        assertEquals("zakum", settings.database().database());
    }

    @Test
    void testControlPlane_Disabled() {
        // Given: Config with ControlPlane disabled
        String yaml = """
            controlPlane:
              enabled: false
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should be disabled
        assertFalse(settings.controlPlane().enabled());
    }

    @Test
    void testControlPlane_Enabled() {
        // Given: Config with ControlPlane enabled
        String yaml = """
            controlPlane:
              enabled: true
              baseUrl: "https://api.example.com"
              apiKey: "test-key-123"
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should be enabled with values
        assertTrue(settings.controlPlane().enabled());
        assertEquals("https://api.example.com", settings.controlPlane().baseUrl());
        assertEquals("test-key-123", settings.controlPlane().apiKey());
    }

    @Test
    void testBoosters_Defaults() {
        // Given: Empty config
        YamlConfiguration config = new YamlConfiguration();
        
        // When: Load configuration
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should have sensible defaults
        assertTrue(settings.boosters().refreshSeconds() >= 10);
        assertTrue(settings.boosters().refreshSeconds() <= 3600);
        assertTrue(settings.boosters().purge().enabled());
    }

    @Test
    void testBoosters_RefreshClamped() {
        // Given: Config with extreme refresh values
        String[] yamls = {
            "boosters:\n  refreshSeconds: 5",     // Too low
            "boosters:\n  refreshSeconds: 5000"   // Too high
        };
        
        // When/Then: Should be clamped
        for (String yaml : yamls) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)
            );
            ZakumSettings settings = ZakumSettingsLoader.load(config);
            assertTrue(settings.boosters().refreshSeconds() >= 10);
            assertTrue(settings.boosters().refreshSeconds() <= 3600);
        }
    }

    @Test
    void testEntitlements_CacheSizeClamped() {
        // Given: Config with extreme cache sizes
        String[] yamls = {
            "entitlements:\n  cacheMaxSize: 10",      // Too low
            "entitlements:\n  cacheMaxSize: 10000000" // Too high
        };
        
        // When/Then: Should be clamped
        for (String yaml : yamls) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new java.io.StringReader(yaml)
            );
            ZakumSettings settings = ZakumSettingsLoader.load(config);
            assertTrue(settings.entitlements().cacheMaxSize() >= 100);
            assertTrue(settings.entitlements().cacheMaxSize() <= 1_000_000);
        }
    }

    @Test
    void testActions_DeferredBufferClamped() {
        // Given: Config with extreme buffer sizes
        String yaml = """
            actions:
              deferredBufferMaxSize: 5
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should be clamped to minimum
        assertTrue(settings.actions().deferredBufferMaxSize() >= 10);
    }

    @Test
    void testComplexConfig_AllSections() {
        // Given: Comprehensive config with all sections
        String yaml = """
            server:
              id: "prod-1"
            database:
              enabled: true
              host: "db.example.com"
              port: 3306
              database: "zakum_prod"
              user: "zakum_user"
              password: "secret"
              pool:
                maxPoolSize: 20
                minIdle: 5
            controlPlane:
              enabled: true
              baseUrl: "https://api.example.com"
              apiKey: "key-123"
            boosters:
              refreshSeconds: 120
              purge:
                enabled: true
                intervalSeconds: 300
            entitlements:
              cacheMaxSize: 50000
              cacheTtlSeconds: 600
            actions:
              deferredBufferMaxSize: 5000
              replayMaxAge: 300
            """;
        
        // When: Load configuration
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: All sections should be parsed correctly
        assertNotNull(settings);
        assertEquals("prod-1", settings.server().id());
        assertEquals("db.example.com", settings.database().host());
        assertTrue(settings.controlPlane().enabled());
        assertEquals(120, settings.boosters().refreshSeconds());
        assertEquals(50000, settings.entitlements().cacheMaxSize());
    }

    @Test
    void testEmptyConfig_UsesAllDefaults() {
        // Given: Completely empty config
        YamlConfiguration config = new YamlConfiguration();
        
        // When: Load configuration
        ZakumSettings settings = ZakumSettingsLoader.load(config);
        
        // Then: Should not throw and use all defaults
        assertNotNull(settings);
        assertNotNull(settings.server());
        assertNotNull(settings.database());
        assertNotNull(settings.controlPlane());
        assertNotNull(settings.boosters());
        assertNotNull(settings.entitlements());
        assertNotNull(settings.actions());
    }

    @Test
    void testConfigReload_CreatesNewInstance() {
        // Given: Initial config
        String yaml = """
            server:
              id: "server-1"
            """;
        
        YamlConfiguration config1 = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml)
        );
        ZakumSettings settings1 = ZakumSettingsLoader.load(config1);
        
        // When: Load again with different value
        String yaml2 = """
            server:
              id: "server-2"
            """;
        
        YamlConfiguration config2 = YamlConfiguration.loadConfiguration(
            new java.io.StringReader(yaml2)
        );
        ZakumSettings settings2 = ZakumSettingsLoader.load(config2);
        
        // Then: Should create new independent instances
        assertNotSame(settings1, settings2);
        assertEquals("server-1", settings1.server().id());
        assertEquals("server-2", settings2.server().id());
    }
}
