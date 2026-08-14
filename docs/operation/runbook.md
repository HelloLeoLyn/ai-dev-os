# AI Dev OS 运维 Runbook

版本： v1.0（Phase 8-F）

适用服务：`services/orchestrator`（Spring Boot 4 / Java 21 / Maven / Docker）

---

# 1. 启动顺序与配置

## 1.1 生产环境变量

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `AI_DEV_OS_PERSISTENCE_TYPE` | `in-memory` | 生产必须设为 `postgresql` |
| `AI_DEV_OS_POSTGRES_URL` | `jdbc:postgresql://localhost:5432/ai_dev_os` | JDBC 连接串 |
| `AI_DEV_OS_POSTGRES_USER` | `ai_dev_os` | 数据库用户 |
| `AI_DEV_OS_POSTGRES_PASSWORD` | 空 | 数据库密码，生产必须提供 |
| `AI_DEV_OS_WORKER_ID` | 随机 | 建议每个实例显式设置稳定 ID，便于区分 owner |
| `AI_DEV_OS_WORKER_LEASE_DURATION` | `30m` | Job lease 时长；执行超时长的任务建议调大 |
| `AI_DEV_OS_WORKER_HEARTBEAT_INTERVAL` | lease/3 | heartbeat 间隔，必须小于 lease |
| `AI_DEV_OS_WORKER_SHUTDOWN_TIMEOUT` | `30s` | 优雅停机等待时长 |
| `AI_DEV_OS_LEASE_REAPER_INTERVAL` | `30s` | lease 过期扫描周期 |
| `AI_DEV_OS_LEASE_REAPER_BATCH_SIZE` | `100` | 每轮扫描批量上限 |
| `AI_DEV_OS_OUTBOX_RELAY_INTERVAL` | `1s` | outbox relay 轮询周期 |
| `AI_DEV_OS_OUTBOX_RELAY_MAX_ATTEMPTS` | `8` | 单条消息最大重试次数，超出进死信 |
| `AI_DEV_OS_OUTBOX_RELAY_BACKOFF_BASE` | `1s` | 失败退避基数（指数） |
| `AI_DEV_OS_OUTBOX_RELAY_BACKOFF_MAX` | `60s` | 退避上限 |

## 1.2 启动顺序

1. 确认 PostgreSQL 可达，且 `AI_DEV_OS_PERSISTENCE_TYPE=postgresql`。
2. 启动应用。启动时 `PostgresDocumentStore` 会自动按版本顺序应用
   `classpath:/db/migration/V*.sql` 中的全部版本，并记录到 `schema_migrations`；
   已应用的版本会跳过，重复启动幂等。
3. 等待 `GET /api/health/readiness` 返回 `200` 与 `{"status":"READY"}`，
   再接入流量。迁移未完成或应用未完成启动时返回 `503 NOT_READY`。
4. 启动第二个实例做双实例部署时，两个实例共享同一 PostgreSQL；
   Job claim 与 PlanRun coordinator 由数据库原子性保证，不会双执行。

---

# 2. 健康检查

| 端点 | 含义 | 预期 |
| --- | --- | --- |
| `GET /api/health` | 存活探针 | 200，`{"status":"UP"}` |
| `GET /api/health/readiness` | 就绪探针 | 就绪 200 `READY`；未就绪 503 `NOT_READY`，`details` 说明原因 |

readiness 门禁：

- `startupComplete=false`：应用尚未完成启动（`ApplicationReadyEvent` 未触发）。
- `migrations=pending`：PostgreSQL 模式下 `schema_migrations` 未包含应用内置的
  全部 migration 版本。

建议 Kubernetes / Docker Compose 使用：

```yaml
livenessProbe:
  httpGet: { path: /api/health, port: 8080 }
readinessProbe:
  httpGet: { path: /api/health/readiness, port: 8080 }
```

---

# 3. 双实例部署

两个（或多个）实例共享同一个 PostgreSQL 时的并发保证：

- **Job claim**：`claimNext` 使用 `FOR UPDATE SKIP LOCKED` + owner/token 写回，
  同一 Job 只会被一个 worker claim；未抢到的实例继续等下一个候选。
- **fencing**：`renewLease` / `complete` 必须携带 `job_id + lease_owner +
  lease_token`；lease 被接管后旧实例的写入影响行数为 0，直接被拒绝，
  防止旧实例覆盖新实例。
- **PlanRun coordinator**：`claimCoordinator` 基于版本 CAS，同一时刻只有一个
  scheduler 能推进一个 run；step Job 使用确定性 ID（`job-{attemptId}`），
  并发/重启提交天然幂等。
- **Outbox relay**：`claimPending` 同样 `FOR UPDATE SKIP LOCKED`；发布与
  `published_at` 标记同事务提交，多实例不会重复发布。

滚动部署建议：先启动新实例，待 readiness 通过后再停止旧实例；实例间无本地
队列依赖，数据库是状态真相源。

---

# 4. 故障恢复流程

## 4.1 Worker 被 kill -9（lease 丢失）

现象：Job 保持 `RUNNING` 且 `lease_expires_at` 过期。

流程：

1. `LeaseReaper` 周期性调用 `findStale` 找出过期 lease。
2. 命中后原子更新为 `RECOVERY_REQUIRED`，`last_failure_code=LEASE_EXPIRED`，
   `recovery_count+1`，并清除 lease owner/token。
3. 旧实例的任何 `renew` / `complete` 均被 fencing 拒绝。
4. `RECOVERY_REQUIRED` 不会被自动重新 claim，等待人工裁决：
   - 若确认外部副作用未发生或可安全重放：恢复 Job 到 `QUEUED`。
   - 若副作用不确定：人工核对执行结果后决定重跑或终止。

查询：

```sql
SELECT id, status, lease_owner, lease_expires_at, recovery_count, last_failure_code
FROM jobs
WHERE status IN ('RUNNING','RECOVERY_REQUIRED')
ORDER BY lease_expires_at;
```

## 4.2 Outbox relay 停止 / 消息积压

现象：`audit_events` 未增长，`audit_outbox` 存在 `published_at IS NULL` 的行。

流程：

1. relay 为后台调度线程；进程存活时自动恢复，无需人工干预。
2. 单条消息失败按指数退避重试（`next_attempt_at`），达到 `max_attempts`
   后标记 `dead_lettered_at`，不会回滚已提交的业务事务。
3. 进程崩溃后，新实例 relay 继续从 pending 行恢复，发布恰好一次。

查询：

```sql
-- 积压量与最早积压时间
SELECT COUNT(*) AS pending,
       MIN(created_at) AS oldest
FROM audit_outbox
WHERE published_at IS NULL AND dead_lettered_at IS NULL;

-- 死信明细
SELECT idempotency_key, topic, attempts, last_error, dead_lettered_at
FROM audit_outbox
WHERE dead_lettered_at IS NOT NULL
ORDER BY dead_lettered_at DESC;
```

告警建议：`pending > 0` 持续超过 5 分钟，或 `dead_lettered_at IS NOT NULL`
出现新行时告警。

## 4.3 Scheduler 重复推进

多 scheduler 实例同时 reconcile 同一 PlanRun 时，只有一个持有 coordinator
lease；step Job 使用确定性 ID + `createIfAbsent` 幂等，因此不会创建重复 Job。
若发现重复 Job 行，检查是否出现 `version` CAS 竞态之外的直接写库行为。

---

# 5. 升级与回滚

## 5.1 升级

1. 执行 `scripts/database/upgrade-preflight.sh`，完成兼容性检查和升级前备份。
2. 部署新版本镜像；启动时自动应用新增 migration，`schema_migrations`
   保证只应用一次且幂等。
3. 等待新实例 readiness 通过后滚动切换。
4. 执行 `scripts/database/post-upgrade-check.sh`，确认 migration、readiness 与数据完整性。

## 5.2 回滚

1. 回滚为旧版本镜像，保持数据库不变。
2. 所有 migration 均为增量添加（`IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`），
   旧版本代码可继续读写，无需回滚数据库 schema。
3. 若已产生新版本写数据（如新列），回滚后新列保持默认值即可，不影响读取。
4. 回滚后检查 readiness 与 `schema_migrations`，确认无部分迁移状态。

---

# 6. 已知边界

- 恢复策略默认保守：lease 过期一律进入 `RECOVERY_REQUIRED`，需要人工/恢复器
  裁决后才可重跑，不会自动重放不确定副作用的任务。
- readiness 门禁覆盖“启动完成 + 迁移完成”；恢复扫描由 `LeaseReaper` 周期性
  执行（每 `AI_DEV_OS_LEASE_REAPER_INTERVAL`），不是一次性启动扫描。
- in-memory 模式仅用于开发；生产必须使用 `postgresql` 模式。
- 完整备份、恢复、external volume 与升级门禁见 [运行数据生命周期](data-lifecycle.md)。
