package net.orbis.zakum.crates.storage;

import net.orbis.zakum.api.ZakumApi;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Database-backed virtual key storage.
 * Uses optimistic locking for concurrent key operations.
 */
public class VirtualKeyStore {
    
    private final ZakumApi zakum;
    private final Executor async;
    private final String serverId;
    
    public VirtualKeyStore(ZakumApi zakum) {
        this.zakum = zakum;
        this.async = zakum.async();
        this.serverId = zakum.server().serverId();
    }
    
    public CompletableFuture<Integer> getKeyCount(UUID playerId, String crateId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT quantity FROM orbis_crates_keys " +
                        "WHERE server_id = ? AND player_uuid = ? AND crate_id = ?";
            
            try (var conn = zakum.database().jdbc().getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, serverId);
                stmt.setString(2, playerId.toString());
                stmt.setString(3, crateId);
                
                try (var rs = stmt.executeQuery()) {
                    return rs.next() ? rs.getInt("quantity") : 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get key count", e);
            }
        }, async);
    }
    
    public CompletableFuture<Void> addKeys(UUID playerId, String crateId, int amount) {
        return CompletableFuture.runAsync(() -> {
            String sql = "INSERT INTO orbis_crates_keys (server_id, player_uuid, crate_id, quantity) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE quantity = quantity + VALUES(quantity)";
            
            try (var conn = zakum.database().jdbc().getConnection();
                 var stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, serverId);
                stmt.setString(2, playerId.toString());
                stmt.setString(3, crateId);
                stmt.setInt(4, amount);
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add keys", e);
            }
        }, async);
    }
    
    public CompletableFuture<Integer> removeKeys(UUID playerId, String crateId, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try (var conn = zakum.database().jdbc().getConnection()) {
                conn.setAutoCommit(false);
                
                try {
                    // Lock row and get current quantity
                    String selectSql = "SELECT quantity FROM orbis_crates_keys " +
                                      "WHERE server_id = ? AND player_uuid = ? AND crate_id = ? FOR UPDATE";
                    
                    int currentQty = 0;
                    try (var stmt = conn.prepareStatement(selectSql)) {
                        stmt.setString(1, serverId);
                        stmt.setString(2, playerId.toString());
                        stmt.setString(3, crateId);
                        
                        try (var rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                currentQty = rs.getInt("quantity");
                            }
                        }
                    }
                    
                    // Calculate actual removal amount
                    int removed = Math.min(amount, currentQty);
                    int newQty = currentQty - removed;
                    
                    // Update or delete
                    if (newQty > 0) {
                        String updateSql = "UPDATE orbis_crates_keys SET quantity = ? " +
                                          "WHERE server_id = ? AND player_uuid = ? AND crate_id = ?";
                        try (var stmt = conn.prepareStatement(updateSql)) {
                            stmt.setInt(1, newQty);
                            stmt.setString(2, serverId);
                            stmt.setString(3, playerId.toString());
                            stmt.setString(4, crateId);
                            stmt.executeUpdate();
                        }
                    } else {
                        String deleteSql = "DELETE FROM orbis_crates_keys " +
                                          "WHERE server_id = ? AND player_uuid = ? AND crate_id = ?";
                        try (var stmt = conn.prepareStatement(deleteSql)) {
                            stmt.setString(1, serverId);
                            stmt.setString(2, playerId.toString());
                            stmt.setString(3, crateId);
                            stmt.executeUpdate();
                        }
                    }
                    
                    conn.commit();
                    return removed;
                    
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove keys", e);
            }
        }, async);
    }
}
