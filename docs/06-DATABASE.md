# Database System

Core: `zakum-core/.../db/SqlManager.java`

Components:
- HikariCP connection pool
- Flyway migrations

Key features:
- async reconnect loop (no main-thread blocking)
- pool stats reporting via `/zakum status`
- optional Micrometer instrumentation (if metrics enabled)

Tables are owned by each feature module, but migrations are centralized
in Zakum core for shared tables and in feature modules for their own tables.
(We will standardize migration strategy as plugins grow.)


---
*Development Note: Edit this module using IntelliJ IDEA with Gradle Sync enabled.*