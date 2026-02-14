package net.orbis.zakum.api.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Tiny JDBC helper to avoid boilerplate.
 */
public interface Jdbc {

  int update(String sql, Object... params);

  <T> List<T> query(String sql, RowMapper<T> mapper, Object... params);

  default <T> T queryOne(String sql, RowMapper<T> mapper, Object... params) {
    List<T> rows = query(sql, mapper, params);
    return rows.isEmpty() ? null : rows.get(0);
  }

  @FunctionalInterface
  interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
  }
}
