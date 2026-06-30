# UpGrd Operator Runbook

Edge-local workflow for upgrading legacy Java web and backend applications. All data stays on the customer machine — no cloud or LLM access to source, WAR, or logs.

## Prerequisites

| Requirement | Notes |
|-------------|-------|
| Java 21 JDK | Runtime for UpGrd and migrated apps |
| Maven 3.9+ | Build migrated output; optional for UpGrd itself |
| Docker | Optional — WildFly smoke/deploy only |

## Inputs to collect

1. **Source tree** — Ant/flat layout or partial checkout (may be out of sync with production).
2. **Production WAR** — authoritative runtime artifact (`WEB-INF/classes`, `WEB-INF/lib`).
3. **Logs** — access + server logs (stack traces and servlet paths improve usage analysis).

## Standard workflow

```bash
# 1. Analyze
upgrd analyze \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --logs ./logs/access.log,./logs/server.log \
  --output ./upgrd-out

# 2. Plan (dry-run) + preview diffs
upgrd plan upgrade --source ./legacy-app --dry-run --output ./upgrd-out
upgrd plan preview --source ./legacy-app --output ./upgrd-out

# 3. Review in UI or CLI
upgrd run --serve-ui --output ./upgrd-out --port 8765
# Or: upgrd plan approve --output ./upgrd-out --source ./legacy-app

# 4. Apply (requires non-dry-run plan + approved-plan.json)
upgrd plan upgrade --source ./legacy-app --dry-run=false --output ./upgrd-out
upgrd apply \
  --plan ./upgrd-out/upgrade-plan.json \
  --source ./legacy-app \
  --output ./upgrd-out \
  --war-policy mark-conflict

# 5. Verify
upgrd verify --output ./upgrd-out
```

## Pipeline shortcut

```bash
# Stops after preview (safe default)
upgrd pipeline run \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --logs ./logs/access.log,./logs/server.log \
  --output ./upgrd-out \
  --serve-ui

# Apply after review (mandatory steps only in CI)
upgrd pipeline run \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --logs ./logs/access.log,./logs/server.log \
  --output ./upgrd-out \
  --confirm \
  --auto-approve-mandatory \
  --skip-verify
```

## Key reports (`--output` directory)

| File | When written | Use |
|------|--------------|-----|
| `analysis-report.json` | analyze | Profile, fingerprint, summary |
| `sync-report.json` | analyze | WAR vs source drift + severity |
| `usage-report.json` | analyze | Log hot paths |
| `feature-usage-report.json` | analyze | HEALTHY / BROKEN / UNOBSERVED features for regression planning |
| `log-source-manifest.json` | analyze | Staged log files after .zip/.gz expansion with unique names |
| `api-compatibility-report.json` | analyze | Unsupported APIs → plan steps |
| `war-context.json` | analyze | WAR path for apply |
| `upgrade-preview-report.json` | plan preview | Before/after file diffs |
| `approved-plan.json` | plan approve / UI | Apply gate |
| `war-merge-report.json` | apply | WAR authoritative merge results |
| `change-ledger.json` | apply | Audit trail of changes |

## WAR conflict policies

| Policy | Behavior |
|--------|----------|
| `war-wins` | Production WAR overwrites migrated files on conflict |
| `source-wins` | Keep migrated source; skip conflicting WAR entries |
| `mark-conflict` | Copy both; record conflicts in `war-conflicts.json` |

WAR-only classes are copied as bytecode under `WEB-INF/classes/` with Java stubs in `.upgrd/war-stubs/` for manual porting — full decompilation is not automated.

## Sign-off export

```bash
upgrd export --output ./upgrd-out --html --pdf
```

Bundle includes sync, API compatibility, security, and change ledger for stakeholder review.

## Log archives (`.zip` / `.gz`)

```bash
upgrd analyze --source ./legacy-app --war ./legacy-app.war --logs-dir ./log-archives --output ./upgrd-out
```

Archives expand into `.upgrd/log-staging/` with unique names (`access__week1__001.log`, …) so repeated `access.log` / `server.log` basenames do not overwrite each other. Access, server, out, and application logs are analyzed together.

**UI workflow:**

```bash
upgrd run --serve-ui --source ./legacy-app --war ./legacy-app.war --output ./upgrd-out
```

Open **Coverage** → set source, WAR, logs directory → **Run log analysis**.

| Health | Meaning |
|--------|---------|
| HEALTHY | Seen in logs, no errors |
| BROKEN | Accessed with HTTP 4xx/5xx or server/application errors |
| UNOBSERVED | Not in log window — still migrated by default |

## Troubleshooting

| Symptom | Action |
|---------|--------|
| Apply skips steps with `NOT_APPROVED` | Run `plan approve` or save approvals in UI Review tab |
| `Cannot apply a dry-run plan` | Re-run `plan upgrade --dry-run=false` |
| WAR merge skipped | Ensure `--war` on analyze; check `sync-report.json` severity |
| `mvn verify` fails | Run `upgrd report-failure`; share sanitized export with external AI |
| WildFly smoke fails | Confirm Docker running; use `--no-wildfly-http` to skip HTTP probe |

## E2E fixture (developers)

`upgrd-core/src/test/resources/fixtures/legacy-e2e-web/` — Ant/Struts sample with logs and WAR drift. Run:

```bash
mvn -pl upgrd-core test -Dtest=FullUpgradeWorkflowE2ETest
```

See [ARCHITECTURE.md](../ARCHITECTURE.md) for milestone history (M15–M20).
