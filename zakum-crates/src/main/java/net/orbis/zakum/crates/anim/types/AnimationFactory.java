package net.orbis.zakum.crates.anim.types;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Factory for creating crate animation instances.
 * 
 * Supports multiple animation types that can be configured
 * in crate definitions.
 */
public final class AnimationFactory {
    
    private static final Map<String, Supplier<CrateAnimation>> REGISTRY = new HashMap<>();
    
    static {
        // Register all animation types
        register("roulette", RouletteAnimation::new);
        register("explosion", ExplosionAnimation::new);
        register("spiral", SpiralAnimation::new);
        register("cascade", CascadeAnimation::new);
        register("instant", InstantAnimation::new);
        register("wheel", WheelAnimation::new);
        
        // Aliases
        register("spin", RouletteAnimation::new);
        register("firework", ExplosionAnimation::new);
        register("helix", SpiralAnimation::new);
        register("waterfall", CascadeAnimation::new);
        register("quick", InstantAnimation::new);
        register("fast", InstantAnimation::new);
        register("fortune", WheelAnimation::new);
    }
    
    private AnimationFactory() {}
    
    /**
     * Create a new animation instance by type name.
     * 
     * @param type Animation type (case-insensitive)
     * @return New animation instance, or InstantAnimation if type unknown
     */
    public static CrateAnimation create(String type) {
        if (type == null || type.isBlank()) {
            return new RouletteAnimation(); // Default
        }
        
        String normalized = type.toLowerCase().trim();
        Supplier<CrateAnimation> supplier = REGISTRY.get(normalized);
        
        if (supplier == null) {
            // Unknown type - fallback to instant
            return new InstantAnimation();
        }
        
        return supplier.get();
    }
    
    /**
     * Register a new animation type.
     * 
     * @param type Type identifier
     * @param supplier Supplier that creates new instances
     */
    public static void register(String type, Supplier<CrateAnimation> supplier) {
        if (type != null && !type.isBlank() && supplier != null) {
            REGISTRY.put(type.toLowerCase().trim(), supplier);
        }
    }
    
    /**
     * Check if an animation type is registered.
     * 
     * @param type Type identifier
     * @return true if type is registered
     */
    public static boolean isRegistered(String type) {
        if (type == null || type.isBlank()) return false;
        return REGISTRY.containsKey(type.toLowerCase().trim());
    }
    
    /**
     * Get all registered animation type names.
     * 
     * @return Array of type names
     */
    public static String[] getRegisteredTypes() {
        return REGISTRY.keySet().toArray(new String[0]);
    }
}
