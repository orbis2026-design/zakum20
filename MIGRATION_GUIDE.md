# Migration Guide

**Version:** 0.1.0-SNAPSHOT  
**Last Updated:** February 18, 2026  
**Scope:** Upgrade paths and data migration

---

## Overview

This guide helps administrators migrate between Zakum versions safely, preserving data and configuration.

---

## Table of Contents

1. [Version Compatibility](#version-compatibility)
2. [Pre-Migration Checklist](#pre-migration-checklist)
3. [Backup Procedures](#backup-procedures)
4. [Migration Paths](#migration-paths)
5. [Configuration Migration](#configuration-migration)
6. [Database Migration](#database-migration)
7. [Rollback Procedures](#rollback-procedures)
8. [Troubleshooting](#troubleshooting)

---

## Version Compatibility

### Supported Upgrade Paths

| From Version | To Version | Direct Upgrade | Notes |
|--------------|------------|----------------|-------|
| N/A | 0.1.0 | ✅ Fresh Install | Initial release |

**Note:** This is the initial release. Future versions will document upgrade paths here.

---

## Pre-Migration Checklist

### Before Upgrading

- [ ] **Backup everything** (database, config files, plugin data)
- [ ] **Review changelog** for breaking changes
- [ ] **Test in staging** environment first
- [ ] **Notify players** of planned maintenance
- [ ] **Check dependencies** are up to date
- [ ] **Verify disk space** for backups and migration
- [ ] **Schedule maintenance window** during low traffic

### System Requirements

**Before upgrading, verify:**
- Java 21 or higher installed
- Paper 1.21.11 or compatible version
- MySQL/MariaDB 8.0+ (if using database)
- Sufficient RAM (2GB+ recommended)
- All dependencies updated

---

## Backup Procedures

### 1. Database Backup

```bash
# MySQL/MariaDB backup
mysqldump -u zakum_user -p zakum_database > zakum_backup_$(date +%Y%m%d).sql

# Verify backup
ls -lh zakum_backup_*.sql
```

### 2. Configuration Backup

```bash
# Backup entire plugins folder
cp -r plugins/Zakum plugins/Zakum_backup_$(date +%Y%m%d)

# Or just config
cp plugins/Zakum/config.yml plugins/Zakum/config.yml.backup
```

### 3. Player Data Backup

```bash
# Backup all module data folders
tar -czf zakum_data_$(date +%Y%m%d).tar.gz \
  plugins/Zakum/data \
  plugins/ZakumBattlePass/data \
  plugins/ZakumCrates/data \
  plugins/ZakumPets/data
```

---

## Migration Paths

### Fresh Installation (0.1.0)

**This is the initial release. No migration needed.**

1. Install Paper 1.21.11
2. Place Zakum-0.1.0-SNAPSHOT.jar in plugins/
3. Start server (generates default config)
4. Stop server
5. Configure plugins/Zakum/config.yml
6. Configure database settings
7. Restart server
8. Verify startup in logs

### Future Migrations

Future releases will document specific migration steps here.

---

## Configuration Migration

### Configuration Format Changes

**0.1.0 (Initial Release)**

No changes - initial configuration format established.

### Automatic Migration

Zakum automatically migrates configuration when possible:
- Missing keys are added with defaults
- Deprecated keys are preserved (with warnings)
- Invalid values are clamped to safe ranges

### Manual Migration Required

Some changes require manual intervention:
- Structural changes to config sections
- Removed features
- Changed permission nodes

**Future versions will document required changes here.**

---

## Database Migration

### Flyway Integration

Zakum uses Flyway for database migrations:
- Migrations run automatically on startup
- Each migration is versioned (V1, V2, etc.)
- Applied migrations are tracked in `flyway_schema_history`
- Failed migrations prevent startup (safe-fail design)

### Migration Process

1. **Automatic Detection**
   ```
   [Zakum] Checking database schema...
   [Zakum] Found 3 pending migrations
   [Zakum] Applying V1__initial_schema.sql
   [Zakum] Applying V2__add_teams.sql
   [Zakum] Applying V3__add_indexes.sql
   [Zakum] Database schema up to date
   ```

2. **Safe Execution**
   - Migrations run in transaction (when supported)
   - Checksums verify migration integrity
   - Rollback on failure (when possible)

3. **Verification**
   ```sql
   SELECT version, description, success 
   FROM flyway_schema_history 
   ORDER BY installed_rank;
   ```

### Manual Migration

If automatic migration fails:

```bash
# 1. Stop server
# 2. Backup database
mysqldump -u user -p database > pre_migration.sql

# 3. Review pending migrations
ls plugins/Zakum/db/migration/

# 4. Apply manually if needed
mysql -u user -p database < plugins/Zakum/db/migration/V3__fix.sql

# 5. Update Flyway history
INSERT INTO flyway_schema_history 
(version, description, type, script, success) 
VALUES ('3', 'Manual fix', 'SQL', 'V3__fix.sql', 1);

# 6. Restart server
```

---

## Rollback Procedures

### When to Rollback

Rollback if you encounter:
- Server crashes on startup
- Database migration failures
- Data corruption
- Performance degradation
- Critical bugs

### Rollback Steps

#### 1. Stop Server

```bash
screen -r minecraft
# Press Ctrl+C or type: stop
```

#### 2. Restore Plugin

```bash
# Remove new version
rm plugins/Zakum*.jar

# Restore old version
cp plugins_backup/Zakum-OLD.jar plugins/
```

#### 3. Restore Configuration

```bash
# Restore config backup
cp plugins/Zakum/config.yml.backup plugins/Zakum/config.yml
```

#### 4. Restore Database

```bash
# Drop current database
mysql -u user -p -e "DROP DATABASE zakum_database;"

# Recreate database
mysql -u user -p -e "CREATE DATABASE zakum_database;"

# Restore backup
mysql -u user -p zakum_database < zakum_backup.sql
```

#### 5. Restart Server

```bash
# Start server with old version
./start.sh
```

#### 6. Verify Functionality

- Check server logs for errors
- Test player login
- Verify commands work
- Check data integrity

---

## Configuration Migration

### config.yml Changes

**Version 0.1.0 (Initial Release)**

Initial configuration structure established. No migrations needed.

**Future versions will document changes here.**

### Adding New Keys

New configuration keys are added automatically with defaults:

```yaml
# Example: New feature added in future version
newFeature:
  enabled: false  # Auto-added with safe default
  setting: "default"
```

### Removing Deprecated Keys

Deprecated keys are preserved but ignored:

```yaml
# Example: Deprecated in future version
oldFeature:
  enabled: true  # Still in config, but ignored
  # Warning logged: "oldFeature is deprecated and will be removed in v2.0"
```

### Renaming Keys

Key renames are handled automatically:

```yaml
# Old name (still works with warning)
oldName: "value"

# New name (preferred)
newName: "value"
```

---

## Data Migration

### Player Data

Player data is stored in the database and migrates automatically via Flyway.

**No manual intervention required** for database-stored data.

### File-Based Data

Some modules may store data in files:
- Pet configurations
- Crate layouts
- Custom items

**Migration:** Copy entire data folders when upgrading.

### Cache Data

Cache data (Redis, Caffeine) is ephemeral:
- No migration needed
- Cache rebuilds automatically

---

## Post-Migration Validation

### Verification Checklist

After migration, verify:

- [ ] Server starts without errors
- [ ] All plugins load successfully
- [ ] Database connections work
- [ ] Player data is accessible
- [ ] Commands execute correctly
- [ ] Permissions apply correctly
- [ ] Economy balances preserved
- [ ] Battle pass progress retained
- [ ] Crate keys counted correctly
- [ ] Pet data intact

### Testing Procedure

1. **Startup Test**
   ```bash
   # Watch logs during startup
   tail -f logs/latest.log
   ```

2. **Database Test**
   ```sql
   -- Verify table structure
   SHOW TABLES;
   
   -- Check migration history
   SELECT * FROM flyway_schema_history;
   
   -- Verify data counts
   SELECT COUNT(*) FROM zakum_players;
   SELECT COUNT(*) FROM zakum_entitlements;
   ```

3. **Functionality Test**
   - Login as test player
   - Check balance: `/bal`
   - View battle pass: `/bp`
   - List pets: `/pets list`
   - Open GUI: `/gui`

4. **Performance Test**
   - Monitor TPS: `/tps`
   - Check memory: `/mem`
   - Review timings: `/timings report`

---

## Troubleshooting

### Common Migration Issues

#### Issue: Server Won't Start After Upgrade

**Symptoms:**
- Server crashes on startup
- "Failed to load plugin" errors

**Solutions:**
1. Check Java version (must be 21+)
2. Verify Paper version compatibility
3. Review server logs for specific errors
4. Check file permissions
5. Verify dependencies are installed

#### Issue: Database Migration Failed

**Symptoms:**
- "Flyway migration failed" in logs
- Server refuses to start
- Database connection errors

**Solutions:**
1. Check database connectivity
2. Verify database user permissions
3. Review migration script for syntax errors
4. Check database version compatibility
5. Manually inspect flyway_schema_history table
6. Consider manual migration (see above)

#### Issue: Configuration Not Loading

**Symptoms:**
- "Invalid configuration" warnings
- Features not working as expected
- Using default values unexpectedly

**Solutions:**
1. Validate YAML syntax (use online validator)
2. Check for tabs (use spaces only)
3. Verify key names match documentation
4. Review changelog for renamed keys
5. Delete config and regenerate (backup first!)

#### Issue: Player Data Missing

**Symptoms:**
- Players reset to default state
- Balances zero
- Battle pass progress lost

**Solutions:**
1. Check database connection
2. Verify correct database selected
3. Check player UUID consistency
4. Review backup and restore if needed
5. Check for data migration errors in logs

#### Issue: Performance Degradation

**Symptoms:**
- Lower TPS after upgrade
- Increased memory usage
- Slower command execution

**Solutions:**
1. Review new configuration options
2. Adjust cache sizes
3. Check for resource-intensive features
4. Review database query performance
5. Consider hardware upgrade if needed

---

## Getting Help

### Before Requesting Support

1. **Check logs** - Most issues are logged with details
2. **Review changelog** - Your issue may be documented
3. **Search existing issues** - Someone may have solved it
4. **Test in isolation** - Try with only Zakum installed

### Support Channels

- **Discord:** [Zakum Discord](https://discord.gg/example)
- **GitHub Issues:** [Report Bug](https://github.com/example/zakum/issues)
- **Wiki:** [Documentation](https://wiki.example.com)

### Information to Provide

When requesting help, include:
- Zakum version (from `/zakum version`)
- Paper version
- Java version
- Full server logs (use pastebin)
- Config file (redact passwords!)
- Database migration history query results
- Steps to reproduce issue

---

## Best Practices

### Upgrade Strategy

1. **Never upgrade in production first**
   - Test in staging/development environment
   - Verify everything works before production

2. **Schedule maintenance windows**
   - Announce to players in advance
   - Choose low-traffic times
   - Allow extra time for issues

3. **Keep backups**
   - Multiple backup copies
   - Store offsite/different disk
   - Test restore procedures regularly

4. **Update dependencies**
   - Update Paper first
   - Update other plugins
   - Then update Zakum

5. **Monitor after upgrade**
   - Watch TPS closely
   - Check error logs frequently
   - Be ready to rollback

### Maintenance Schedule

Recommended update frequency:
- **Security updates:** Apply immediately
- **Bug fixes:** Within 1 week
- **Feature updates:** Plan for next maintenance window
- **Major versions:** Extended testing + planning

---

## Version History

### 0.1.0-SNAPSHOT (Current)

**Initial Release**
- No migration paths (fresh installation only)
- Database schema V1 established
- Configuration format finalized

**Future versions will be documented here.**

---

## Related Documentation

- [CONFIG.md](CONFIG.md) - Configuration reference
- [COMMANDS.md](COMMANDS.md) - Commands reference
- [SECURITY.md](SECURITY.md) - Security information

---

**Last Updated:** February 18, 2026  
**Module Version:** 0.1.0-SNAPSHOT

