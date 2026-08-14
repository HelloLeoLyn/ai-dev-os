# AI Dev OS 运行数据生命周期

## 数据边界

- **Development data**：个人开发数据，可按需清理，不得冒充正式运行记录。
- **Test data**：只能进入隔离数据库或 Testcontainers；测试结束后清理。
- **Runtime data**：Backlog、Task、Execution、Validation、Security、Quality Gate、Audit 等正式记录。只能存入 PostgreSQL external volume，应用升级和容器重建不得重置。

## Schema 与 Migration

- migration 位于 `services/orchestrator/src/main/resources/db/migration/V*.sql`。
- 已发布 migration 不允许修改、重命名或复用版本号；schema 变化只能追加新版本。
- `schema_migrations` 的真实字段为 `version`、`name`、`applied_at`。
- 应用启动会应用尚未执行的 migration。数据库含有当前应用未知的版本或同版本名称不一致时，升级前检查必须失败。
- V1 不提供自动 schema rollback。应用或数据库回退前必须保留经过恢复验证的备份。

## External Volume

正式数据卷为 `ai-dev-os_postgres-data`，由 Compose 作为 external volume 使用：

```bash
./services/orchestrator/scripts/database/ensure-postgres-volume.sh
docker compose up -d postgres
```

external volume 不会被 `docker compose down -v` 删除，但该命令仍不得作为日常停止方式。推荐：

```bash
docker compose stop postgres
```

不要执行 `docker volume rm ai-dev-os_postgres-data`。删除 Docker Desktop 数据、重装发行版或磁盘损坏仍可能丢失数据，因此 volume 不能替代备份。

## 管理员备份

备份目录必须显式指定，并应位于仓库之外：

```bash
./services/orchestrator/scripts/database/backup-postgres.sh \
  --output-dir /path/to/ai-dev-os-backups
```

每次备份生成 custom-format dump 和 metadata JSON。Metadata 包含数据库版本、应用 commit、migration、关键计数、文件大小和 SHA-256，不包含密码。

## 管理员恢复

恢复会覆盖目标数据库。必须停止目标数据库对应的 Orchestrator，并提供精确数据库名与 safety backup 目录：

```bash
./services/orchestrator/scripts/database/restore-postgres.sh \
  --backup /path/to/backup.dump \
  --confirm-database ai_dev_os \
  --safety-backup-dir /path/to/pre-restore-backups
```

脚本会验证 metadata、SHA-256 和 archive，先制作 safety backup，再执行 restore 和数据完整性检查。

## Upgrade Runbook

升级前：

```bash
./services/orchestrator/scripts/database/upgrade-preflight.sh \
  --backup-dir /path/to/pre-upgrade-backups
```

升级并启动后：

```bash
./services/orchestrator/scripts/database/post-upgrade-check.sh
```

只有 preflight、备份、migration、readiness 和 post-upgrade integrity 全部通过，升级才算完成。

## V1 边界

V1 仅支持管理员手动全量逻辑备份/恢复。自动定时、Retention、Remote Backup、PITR、自动 migration orchestration 和自动 rollback 留待后续阶段。
