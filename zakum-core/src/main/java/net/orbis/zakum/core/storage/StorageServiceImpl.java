package net.orbis.zakum.core.storage;

import net.orbis.zakum.api.db.DatabaseState;
import net.orbis.zakum.api.db.Jdbc;
import net.orbis.zakum.api.db.ZakumDatabase;
import net.orbis.zakum.api.storage.StorageService;

import javax.sql.DataSource;
import java.util.Objects;

public final class StorageServiceImpl implements StorageService {

  private final ZakumDatabase database;

  public StorageServiceImpl(ZakumDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  @Override
  public DatabaseState state() {
    return database.state();
  }

  @Override
  public DataSource dataSource() {
    return database.dataSource();
  }

  @Override
  public Jdbc jdbc() {
    return database.jdbc();
  }
}
