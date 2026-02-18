package net.orbis.zakum.crates.reward;

import net.orbis.zakum.crates.model.RewardDef;

import java.time.Instant;
import java.util.UUID;

/**
 * Record of a reward grant.
 * 
 * Tracks who received what reward and when.
 */
public record RewardHistory(
    UUID playerId,
    String playerName,
    String crateId,
    String crateName,
    RewardDef reward,
    Instant timestamp,
    boolean successful
) {
    
    public RewardHistory {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId cannot be null");
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
    
    /**
     * Create a successful reward history entry.
     */
    public static RewardHistory success(UUID playerId, String playerName, 
                                       String crateId, String crateName, 
                                       RewardDef reward) {
        return new RewardHistory(playerId, playerName, crateId, crateName, 
                                reward, Instant.now(), true);
    }
    
    /**
     * Create a failed reward history entry.
     */
    public static RewardHistory failure(UUID playerId, String playerName, 
                                       String crateId, String crateName, 
                                       RewardDef reward) {
        return new RewardHistory(playerId, playerName, crateId, crateName, 
                                reward, Instant.now(), false);
    }
}
