package net.orbis.zakum.crates.reward;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks reward history for players.
 * 
 * Maintains an in-memory cache of recent reward grants.
 * Can be extended to persist to database.
 */
public class RewardHistoryTracker {
    
    private final Map<UUID, List<RewardHistory>> historyByPlayer = new ConcurrentHashMap<>();
    private final List<RewardHistory> globalHistory = Collections.synchronizedList(new ArrayList<>());
    private final int maxHistoryPerPlayer;
    private final int maxGlobalHistory;
    
    public RewardHistoryTracker() {
        this(100, 1000);
    }
    
    public RewardHistoryTracker(int maxHistoryPerPlayer, int maxGlobalHistory) {
        this.maxHistoryPerPlayer = maxHistoryPerPlayer;
        this.maxGlobalHistory = maxGlobalHistory;
    }
    
    /**
     * Record a reward grant.
     */
    public void record(RewardHistory history) {
        if (history == null) return;
        
        // Add to player history
        historyByPlayer.computeIfAbsent(history.playerId(), k -> 
            Collections.synchronizedList(new ArrayList<>())
        ).add(history);
        
        // Trim player history if needed
        List<RewardHistory> playerHist = historyByPlayer.get(history.playerId());
        if (playerHist.size() > maxHistoryPerPlayer) {
            playerHist.remove(0);
        }
        
        // Add to global history
        globalHistory.add(history);
        
        // Trim global history if needed
        if (globalHistory.size() > maxGlobalHistory) {
            globalHistory.remove(0);
        }
    }
    
    /**
     * Get reward history for a specific player.
     */
    public List<RewardHistory> getPlayerHistory(UUID playerId) {
        return new ArrayList<>(historyByPlayer.getOrDefault(playerId, List.of()));
    }
    
    /**
     * Get recent reward history for a player.
     */
    public List<RewardHistory> getRecentPlayerHistory(UUID playerId, int limit) {
        List<RewardHistory> history = historyByPlayer.getOrDefault(playerId, List.of());
        int start = Math.max(0, history.size() - limit);
        return new ArrayList<>(history.subList(start, history.size()));
    }
    
    /**
     * Get rewards from a specific crate for a player.
     */
    public List<RewardHistory> getPlayerCrateHistory(UUID playerId, String crateId) {
        return historyByPlayer.getOrDefault(playerId, List.of())
            .stream()
            .filter(h -> h.crateId() != null && h.crateId().equals(crateId))
            .collect(Collectors.toList());
    }
    
    /**
     * Get global reward history.
     */
    public List<RewardHistory> getGlobalHistory() {
        return new ArrayList<>(globalHistory);
    }
    
    /**
     * Get recent global history.
     */
    public List<RewardHistory> getRecentGlobalHistory(int limit) {
        int start = Math.max(0, globalHistory.size() - limit);
        return new ArrayList<>(globalHistory.subList(start, globalHistory.size()));
    }
    
    /**
     * Get rewards granted within a time period.
     */
    public List<RewardHistory> getHistorySince(Instant since) {
        return globalHistory.stream()
            .filter(h -> h.timestamp().isAfter(since))
            .collect(Collectors.toList());
    }
    
    /**
     * Get rewards granted in the last N hours.
     */
    public List<RewardHistory> getHistoryLastHours(int hours) {
        Instant since = Instant.now().minus(hours, ChronoUnit.HOURS);
        return getHistorySince(since);
    }
    
    /**
     * Count rewards for a player.
     */
    public int getPlayerRewardCount(UUID playerId) {
        return historyByPlayer.getOrDefault(playerId, List.of()).size();
    }
    
    /**
     * Count successful rewards for a player.
     */
    public int getPlayerSuccessCount(UUID playerId) {
        return (int) historyByPlayer.getOrDefault(playerId, List.of())
            .stream()
            .filter(RewardHistory::successful)
            .count();
    }
    
    /**
     * Get statistics for a player.
     */
    public PlayerStats getPlayerStats(UUID playerId) {
        List<RewardHistory> history = historyByPlayer.getOrDefault(playerId, List.of());
        
        int total = history.size();
        int successful = (int) history.stream().filter(RewardHistory::successful).count();
        int failed = total - successful;
        
        Map<String, Integer> crateCount = new HashMap<>();
        for (RewardHistory h : history) {
            if (h.crateId() != null) {
                crateCount.merge(h.crateId(), 1, Integer::sum);
            }
        }
        
        return new PlayerStats(playerId, total, successful, failed, crateCount);
    }
    
    /**
     * Clear history for a player.
     */
    public void clearPlayerHistory(UUID playerId) {
        historyByPlayer.remove(playerId);
    }
    
    /**
     * Clear all history.
     */
    public void clearAll() {
        historyByPlayer.clear();
        globalHistory.clear();
    }
    
    /**
     * Statistics for a player.
     */
    public record PlayerStats(
        UUID playerId,
        int totalRewards,
        int successfulRewards,
        int failedRewards,
        Map<String, Integer> crateBreakdown
    ) {}
}
