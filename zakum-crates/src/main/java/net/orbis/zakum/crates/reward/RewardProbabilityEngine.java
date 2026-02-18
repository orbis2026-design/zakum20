package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Probability engine for selecting rewards based on weights.
 * 
 * Implements weighted random selection with fair distribution.
 */
public class RewardProbabilityEngine {
    
    private final Random random = new Random();
    
    /**
     * Select a reward from a list based on weights.
     * 
     * @param rewards List of rewards to choose from
     * @return Selected reward, or null if list is empty
     */
    public RewardDef selectReward(List<RewardDef> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return null;
        }
        
        // Calculate total weight
        double totalWeight = 0.0;
        for (RewardDef reward : rewards) {
            totalWeight += reward.weight();
        }
        
        if (totalWeight <= 0) {
            // All weights are 0 or negative, select randomly
            return rewards.get(random.nextInt(rewards.size()));
        }
        
        // Generate random value between 0 and totalWeight
        double randomValue = random.nextDouble() * totalWeight;
        
        // Find the reward corresponding to this value
        double cumulative = 0.0;
        for (RewardDef reward : rewards) {
            cumulative += reward.weight();
            if (randomValue <= cumulative) {
                return reward;
            }
        }
        
        // Fallback (should not reach here)
        return rewards.get(rewards.size() - 1);
    }
    
    /**
     * Calculate the probability of getting a specific reward.
     * 
     * @param reward The reward to calculate probability for
     * @param allRewards All available rewards
     * @return Probability as a percentage (0-100)
     */
    public double calculateProbability(RewardDef reward, List<RewardDef> allRewards) {
        if (reward == null || allRewards == null || allRewards.isEmpty()) {
            return 0.0;
        }
        
        double totalWeight = 0.0;
        for (RewardDef r : allRewards) {
            totalWeight += r.weight();
        }
        
        if (totalWeight <= 0) {
            return 100.0 / allRewards.size();
        }
        
        return (reward.weight() / totalWeight) * 100.0;
    }
    
    /**
     * Get rewards sorted by probability (highest first).
     * 
     * @param rewards List of rewards
     * @return List sorted by probability
     */
    public List<RewardDef> sortByProbability(List<RewardDef> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<RewardDef> sorted = new ArrayList<>(rewards);
        sorted.sort((r1, r2) -> Double.compare(r2.weight(), r1.weight()));
        return sorted;
    }
    
    /**
     * Validate reward weights.
     * 
     * @param rewards List of rewards to validate
     * @return Validation result with any issues
     */
    public ValidationResult validateWeights(List<RewardDef> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return ValidationResult.error("Reward list is empty");
        }
        
        double totalWeight = 0.0;
        int negativeCount = 0;
        int zeroCount = 0;
        
        for (RewardDef reward : rewards) {
            double weight = reward.weight();
            totalWeight += weight;
            
            if (weight < 0) {
                negativeCount++;
            } else if (weight == 0) {
                zeroCount++;
            }
        }
        
        if (negativeCount > 0) {
            return ValidationResult.error(
                "Found " + negativeCount + " rewards with negative weights"
            );
        }
        
        if (totalWeight <= 0) {
            return ValidationResult.error(
                "Total weight is " + totalWeight + " (must be positive)"
            );
        }
        
        if (zeroCount == rewards.size()) {
            return ValidationResult.error("All rewards have zero weight");
        }
        
        return ValidationResult.success();
    }
    
    /**
     * Normalize weights to sum to 1.0.
     * 
     * @param rewards List of rewards
     * @return List with normalized weights
     */
    public List<RewardDef> normalizeWeights(List<RewardDef> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            return new ArrayList<>();
        }
        
        double totalWeight = 0.0;
        for (RewardDef reward : rewards) {
            totalWeight += reward.weight();
        }
        
        if (totalWeight <= 0) {
            return new ArrayList<>(rewards);
        }
        
        List<RewardDef> normalized = new ArrayList<>();
        for (RewardDef reward : rewards) {
            double normalizedWeight = reward.weight() / totalWeight;
            RewardDef normalizedReward = new RewardDef(
                reward.id(),
                reward.name(),
                normalizedWeight,
                reward.items(),
                reward.commands(),
                reward.effects(),
                reward.messages()
            );
            normalized.add(normalizedReward);
        }
        
        return normalized;
    }
    
    /**
     * Result of validation check.
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
    }
}
