# AI Dev OS Development Run Guide

## Environment requirements

- Java 21
- Node.js 20 or newer
- npm
- Bash 4.3 or newer (`start-all.sh` uses `wait -n`)
- Network access for the first Maven and npm dependency installation
- Optional OpenClaw gateway listening on `127.0.0.1:18789`

Development ports are fixed:

| Service | Address |
| --- | --- |
| Frontend | `http://127.0.0.1:15174` |
| Backend | `http://127.0.0.1:18080` |
| OpenClaw | `ws://127.0.0.1:18789` |

The Vite development server proxies `/api` requests to the backend on port
`18080`.

## First-time setup

From the orchestrator directory, install frontend dependencies:

```bash
cd frontend
npm install
cd ..
```

The backend uses the checked-in Maven Wrapper and does not require a global
Maven installation.

## Starting the environment

Create `src/main/resources/application-local.properties` for local OpenClaw
settings. This file is ignored by Git so that the gateway token is not
committed:

```properties
openclaw.gateway.url=ws://127.0.0.1:18789
openclaw.token=${OPENCLAW_GATEWAY_TOKEN:your-local-gateway-token}
```

Activate the `local` Spring profile when starting the backend. The environment
variable remains the highest-priority token source because the local property
uses it as its first choice:

```bash
SPRING_PROFILES_ACTIVE=local ./scripts/start-backend.sh
```

On startup, the backend automatically loads JSON task definitions from
`src/main/resources/tasks/`. The bundled `openclaw-test` task is therefore
available immediately for job submission.

Start both services in one terminal:

```bash
SPRING_PROFILES_ACTIVE=local ./scripts/start-all.sh
```

Open `http://127.0.0.1:15174` after both services report that they are ready.

To run the services in separate terminals instead:

```bash
./scripts/start-backend.sh
```

```bash
./scripts/start-frontend.sh
```

`OPENCLAW_GATEWAY_URL` can override the OpenClaw endpoint while keeping
`ws://127.0.0.1:18789` as the development default:

```bash
OPENCLAW_GATEWAY_URL=ws://127.0.0.1:18789 ./scripts/start-backend.sh
```

For production deployments, do not use the local properties file. Continue to
provide the token through `OPENCLAW_GATEWAY_TOKEN`; an unset token defaults to
an empty value.

## PostgreSQL persistence mode

The default persistence mode is in-memory. To run against PostgreSQL, start a
database and set the persistence environment variables:

```bash
export AI_DEV_OS_PERSISTENCE_TYPE=postgresql
export AI_DEV_OS_POSTGRES_URL=jdbc:postgresql://localhost:5432/ai_dev_os
export AI_DEV_OS_POSTGRES_USER=ai_dev_os
export AI_DEV_OS_POSTGRES_PASSWORD=your-password
SPRING_PROFILES_ACTIVE=local ./scripts/start-backend.sh
```

On startup the application applies the versioned schema migrations
(`src/main/resources/db/migration/V1__..V7__`) automatically. Applied versions
are recorded in `schema_migrations`; repeated starts are idempotent. Wait for
readiness before sending traffic:

```bash
curl -s http://127.0.0.1:18080/api/health/readiness
# {"status":"READY","details":{"startupComplete":true,"migrations":"complete"}}
```

`GET /api/health` is the liveness probe and always returns `UP` once the
process is running. `GET /api/health/readiness` returns `200` only after
startup completes and (in PostgreSQL mode) all migrations are applied;
otherwise it returns `503` with a `details` explanation.

In a dual-instance deployment both instances share the same PostgreSQL. Job
claims, plan-run coordinator claims and outbox claims are guarded by database
locking and fencing tokens, so a job is executed by exactly one instance. See
`docs/operation/runbook.md` for the operational procedures.

## Stopping the environment

Press `Ctrl+C` in the terminal running `start-all.sh`; it forwards shutdown to
both services and waits for them to stop.

When services were started separately, press `Ctrl+C` in each terminal.

## Common problems

### A port is already in use

The development ports are intentionally fixed. Stop the process using `15174`
or `18080`, then run the script again. Vite uses strict port mode and will not
silently choose another port.

### Frontend dependencies are missing

Run `npm install` inside `frontend/`, then restart the frontend script.

### The backend reports an incompatible Java version

Confirm that `java -version` resolves to Java 21 and that `JAVA_HOME` points to
the same installation.

### OpenClaw is unavailable

The web application and read-only APIs can start without an active OpenClaw
connection. Operations that select the OpenClaw executor require a gateway on
port `18789`, or a valid `OPENCLAW_GATEWAY_URL` override.

### API requests fail from the frontend

Confirm that the backend is listening on `18080`. The browser should access the
frontend through `15174`; Vite then forwards `/api` calls to the backend.
