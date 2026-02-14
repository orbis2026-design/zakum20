package net.orbis.zakum.api.storage;

import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.Jdbc;

import javax.sql.DataSource;

public interface StorageService {

  DatabaseState state();

  DataSource dataSource();

  Jdbc jdbc();
}
