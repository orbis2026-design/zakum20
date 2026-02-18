package net.orbis.zakum.crates.anim;

import net.orbis.zakum.crates.anim.types.AnimationFactory;
import net.orbis.zakum.crates.anim.types.CrateAnimation;

import java.util.HashMap;
import java.util.Map;

/**
 * Validates animation configurations and parameters.
 * 
 * Ensures animation types are valid and parameters are within acceptable ranges.
 */
public final class AnimationValidator {
    
    private static final int MIN_DURATION = 10; // 0.5 seconds
    private static final int MAX_DURATION = 200; // 10 seconds
    
    private AnimationValidator() {}
    
    /**
     * Validate an animation type name.
     * 
     * @param type Animation type identifier
     * @return Validation result
     */
    public static ValidationResult validateType(String type) {
        if (type == null || type.isBlank()) {
            return ValidationResult.error("Animation type cannot be null or empty");
        }
        
        if (!AnimationFactory.isRegistered(type)) {
            return ValidationResult.error(
                "Unknown animation type: " + type + ". " +
                "Available types: " + String.join(", ", AnimationFactory.getRegisteredTypes())
            );
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Validate animation configuration parameters.
     * 
     * @param type Animation type
     * @param parameters Configuration parameters
     * @return Validation result
     */
    public static ValidationResult validateConfiguration(String type, Map<String, Object> parameters) {
        // Validate type first
        ValidationResult typeResult = validateType(type);
        if (!typeResult.isValid()) {
            return typeResult;
        }
        
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        
        // Validate duration if specified
        if (parameters.containsKey("duration")) {
            Object durationObj = parameters.get("duration");
            if (!(durationObj instanceof Number)) {
                return ValidationResult.error("Duration must be a number (ticks)");
            }
            
            int duration = ((Number) durationObj).intValue();
            if (duration < MIN_DURATION || duration > MAX_DURATION) {
                return ValidationResult.error(
                    "Duration must be between " + MIN_DURATION + " and " + MAX_DURATION + " ticks"
                );
            }
        }
        
        // Type-specific validation
        switch (type.toLowerCase()) {
            case "roulette", "spin" -> {
                return validateRouletteParams(parameters);
            }
            case "explosion", "firework" -> {
                return validateExplosionParams(parameters);
            }
            case "spiral", "helix" -> {
                return validateSpiralParams(parameters);
            }
            case "cascade", "waterfall" -> {
                return validateCascadeParams(parameters);
            }
            case "wheel", "fortune" -> {
                return validateWheelParams(parameters);
            }
            case "instant", "quick", "fast" -> {
                return ValidationResult.success(); // No params to validate
            }
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Test-create an animation to verify it can be instantiated.
     * 
     * @param type Animation type
     * @return Validation result
     */
    public static ValidationResult testCreate(String type) {
        try {
            CrateAnimation animation = AnimationFactory.create(type);
            if (animation == null) {
                return ValidationResult.error("Failed to create animation instance");
            }
            return ValidationResult.success();
        } catch (Exception e) {
            return ValidationResult.error("Error creating animation: " + e.getMessage());
        }
    }
    
    private static ValidationResult validateRouletteParams(Map<String, Object> params) {
        if (params.containsKey("steps")) {
            Object stepsObj = params.get("steps");
            if (!(stepsObj instanceof Number)) {
                return ValidationResult.error("Roulette 'steps' must be a number");
            }
            int steps = ((Number) stepsObj).intValue();
            if (steps < 5 || steps > 100) {
                return ValidationResult.error("Roulette 'steps' must be between 5 and 100");
            }
        }
        return ValidationResult.success();
    }
    
    private static ValidationResult validateExplosionParams(Map<String, Object> params) {
        if (params.containsKey("intensity")) {
            Object intensityObj = params.get("intensity");
            if (!(intensityObj instanceof Number)) {
                return ValidationResult.error("Explosion 'intensity' must be a number");
            }
            double intensity = ((Number) intensityObj).doubleValue();
            if (intensity < 0.1 || intensity > 5.0) {
                return ValidationResult.error("Explosion 'intensity' must be between 0.1 and 5.0");
            }
        }
        return ValidationResult.success();
    }
    
    private static ValidationResult validateSpiralParams(Map<String, Object> params) {
        if (params.containsKey("radius")) {
            Object radiusObj = params.get("radius");
            if (!(radiusObj instanceof Number)) {
                return ValidationResult.error("Spiral 'radius' must be a number");
            }
            double radius = ((Number) radiusObj).doubleValue();
            if (radius < 0.5 || radius > 5.0) {
                return ValidationResult.error("Spiral 'radius' must be between 0.5 and 5.0");
            }
        }
        return ValidationResult.success();
    }
    
    private static ValidationResult validateCascadeParams(Map<String, Object> params) {
        if (params.containsKey("height")) {
            Object heightObj = params.get("height");
            if (!(heightObj instanceof Number)) {
                return ValidationResult.error("Cascade 'height' must be a number");
            }
            double height = ((Number) heightObj).doubleValue();
            if (height < 1.0 || height > 10.0) {
                return ValidationResult.error("Cascade 'height' must be between 1.0 and 10.0");
            }
        }
        return ValidationResult.success();
    }
    
    private static ValidationResult validateWheelParams(Map<String, Object> params) {
        if (params.containsKey("segments")) {
            Object segmentsObj = params.get("segments");
            if (!(segmentsObj instanceof Number)) {
                return ValidationResult.error("Wheel 'segments' must be a number");
            }
            int segments = ((Number) segmentsObj).intValue();
            if (segments < 4 || segments > 12) {
                return ValidationResult.error("Wheel 'segments' must be between 4 and 12");
            }
        }
        return ValidationResult.success();
    }
    
    /**
     * Result of a validation check.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return valid ? "Valid" : "Invalid: " + errorMessage;
        }
    }
}
