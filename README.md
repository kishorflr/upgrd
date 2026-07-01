# UpGrd

![CI](https://github.com/kishorflr/upgrd/actions/workflows/ci.yml/badge.svg)

Edge-local Java modernization toolkit. Analyzes legacy applications (source, WAR, logs), plans upgrades, and produces an upgraded Maven-based codebase — without sending customer data to any AI or cloud service.

## Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+** on your **PATH** (or set **`MAVEN_HOME`**). UpGrd runs `mvn` as a subprocess during apply/verify and in some tests; IntelliJ’s bundled Maven is used only for the IDE build, not for those subprocess calls.

```bash
# macOS (Homebrew)
brew install openjdk@21 maven
```

### Windows / IntelliJ IDEA

1. Install [Maven](https://maven.apache.org/download.cgi) and add `%MAVEN_HOME%\bin` to your user **PATH**, **or** set a system **`MAVEN_HOME`** variable pointing at the Maven install.
2. In IntelliJ: **Settings → Build, Execution, Deployment → Build Tools → Maven** — set **Maven home path** to the same install.
3. Use **JDK 21** for the project SDK.
4. Open the root **`pom.xml`** as a Maven project; build with **Maven → Lifecycle → verify** (or a terminal where `mvn -version` works).
5. WildFly smoke tests need **Docker Desktop**; they skip automatically when Docker is unavailable.

If Maven is not on PATH, `mvn verify` still compiles but Maven-dependent tests are **skipped** (not failed).

## Build

```bash
mvn verify
```

## Run

After packaging:

```bash
java -jar upgrd-cli/target/upgrd.jar analyze \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --logs ./logs/access.log,./logs/server.log \
  --output ./upgrd-out

java -jar upgrd-cli/target/upgrd.jar plan upgrade \
  --source ./legacy-app \
  --target java21 \
  --server weblogic-14c \
  --dry-run \
  --output ./upgrd-out

java -jar upgrd-cli/target/upgrd.jar apply \
  --plan ./upgrd-out/upgrade-plan.json \
  --source ./legacy-app \
  --output ./upgrd-out

java -jar upgrd-cli/target/upgrd.jar run --serve-ui \
  --output ./upgrd-out \
  --port 8765
```

Reports are written locally to `--output` (default `./upgrd-out`).

## Commands

| Command | Status |
|---------|--------|
| `analyze` | Profile detection, fingerprint, WAR/sync severity, API compatibility catalog, security scan |
| `plan upgrade --dry-run` | Profile-aware steps + security remediation steps from findings |
| `plan preview` | Dry-run file diffs (before/after) without applying — `change-ledger-preview.json` |
| `plan approve` | Create `approved-plan.json` (mandatory-only by default; override with flags) |
| `apply` | Source migration; WAR merge with `--war` / `war-context.json`; `--war-policy war-wins|source-wins|mark-conflict` |
| `verify` | Runs `mvn verify`; optional `-Psecurity-verify`, `--wildfly-smoke`, `--wildfly-deploy`, `--wildfly-http` |
| `weblogic` | `status` / `validate` — production deploy scaffold checks (no Docker) |
| `wildfly` | `start` / `stop` / `deploy` / `undeploy` / `status` — local Docker WildFly |
| `rewrite run` | OpenRewrite AST migrations via Maven plugin (`--dry-run`, `--recipe`, `--require-dry-run`, `--force`) |
| `report-failure` | Sanitized AI-safe failure export from captured logs |
| `export` | Bundle audit reports into JSON/Markdown; `--html` and `--pdf` for sign-off |
| `run --serve-ui` | Local audit dashboard with diffs, verify status, and security tab |
| `pipeline run` | analyze → plan → preview; apply/verify with `--confirm` + `approved-plan.json` (or `--auto-approve-mandatory`) |
| **Recipes (implemented)** | Ant→Maven, Java 21, log4j→SLF4J, Struts→Spring (validated form beans with `@Size`, `@ControllerAdvice` binding, GET/POST controllers), Spring 4→6, javax→jakarta, security fixes |
| **Recipes (planned)** | Struts mask/creditCard validation rules, OpenRewrite SQL search recipes |

### WildFly (local smoke deploy)

After `apply`, use Docker-backed WildFly under `migrated/deploy/wildfly/`:

```bash
upgrd wildfly start --output ./upgrd-out
upgrd wildfly deploy --output ./upgrd-out --build
upgrd wildfly status --output ./upgrd-out
upgrd wildfly stop --output ./upgrd-out
```

WARs are hot-deployed via the `deployments/` volume mount (no `docker cp` required).

### OpenRewrite (AST migrations)

`apply` scaffolds `.upgrd/openrewrite.yml`. Run deeper refactors on demand:

```bash
upgrd rewrite run --output ./upgrd-out --dry-run
upgrd rewrite run --output ./upgrd-out
upgrd rewrite run --output ./upgrd-out --recipe com.upgrd.migrated.SqlConcatenationScan --dry-run
```

POM plugin/BOM injection happens at `rewrite run` time so normal `mvn verify` stays lightweight.

`apply` also runs an optional **OpenRewrite dry-run** gate (`.upgrd/rewrite/dry-run-passed`); full AST apply remains advisory via `upgrd rewrite run`.

### WebLogic (production scaffold)

```bash
upgrd weblogic status --output ./upgrd-out
upgrd weblogic validate --output ./upgrd-out
```

Validates `deploy/weblogic/` overlays, `wldeploy.sh` / `wldeploy.properties`, and `deploy.sh` without requiring a local WebLogic instance.

### CI (GitHub Actions)

`.github/workflows/ci.yml` runs `mvn verify` on every push/PR and uploads `upgrd.jar` as a workflow artifact when green. Tag `v*` pushes create a GitHub Release with the shaded JAR (`.github/workflows/release.yml`).

### Review-first workflow (M15)

```bash
upgrd analyze --source ./legacy-app --war ./legacy-app.war --output ./upgrd-out
upgrd plan upgrade --source ./legacy-app --dry-run --output ./upgrd-out
upgrd plan preview --source ./legacy-app --output ./upgrd-out
upgrd plan approve --output ./upgrd-out --source ./legacy-app
upgrd run --serve-ui --output ./upgrd-out   # Review tab: diffs + approve steps → Save approval
upgrd plan upgrade --source ./legacy-app --dry-run=false --output ./upgrd-out
upgrd apply --plan ./upgrd-out/upgrade-plan.json --source ./legacy-app --output ./upgrd-out
```

Or use the pipeline with explicit confirmation and approval:

```bash
upgrd pipeline run --source ./legacy-app --war ./legacy-app.war --output ./upgrd-out --serve-ui
# Review + save approval in UI (or: upgrd plan approve --output ./upgrd-out --source ./legacy-app)
upgrd pipeline run --source ./legacy-app --war ./legacy-app.war --output ./upgrd-out --confirm --skip-verify
```

CI shortcut (mandatory steps only, no UI):

```bash
upgrd pipeline run ... --confirm --auto-approve-mandatory --skip-verify
```

See [ARCHITECTURE.md](ARCHITECTURE.md) for the M15–M20 roadmap to WAR-authoritative, approval-gated upgrades.

### Operator runbook

Full step-by-step guidance for production upgrades: [docs/OPERATOR-RUNBOOK.md](docs/OPERATOR-RUNBOOK.md).

### E2E fixture (M20)

Integration test `FullUpgradeWorkflowE2ETest` exercises the full workflow against `upgrd-core/src/test/resources/fixtures/legacy-e2e-web/` (source + logs; WAR with drift built in test):

```bash
mvn -pl upgrd-core test -Dtest=FullUpgradeWorkflowE2ETest
```

### One-shot pipeline

```bash
upgrd pipeline run \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --output ./upgrd-out \
  --profile legacy-web

# Apply only after reviewing preview:
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --confirm --skip-verify

# Backend (no WAR):
upgrd pipeline run --source ./legacy-backend --output ./upgrd-out --profile legacy-backend --skip-verify

# Open audit dashboard when done:
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --skip-verify --serve-ui

# Optional OpenRewrite after apply:
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --skip-verify --rewrite --rewrite-dry-run

# OpenRewrite after verify (when build must pass first):
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --rewrite --rewrite-after-verify

# SQL concatenation scan preset (dry-run after verify):
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --rewrite-sql-scan

# legacy-web runs WildFly HTTP probe during verify by default; opt out with --no-wildfly-http
```

Open http://127.0.0.1:8765 for the audit dashboard (profile, plan reasoning, **Review** preview diffs, change ledger, WAR sync, design advisory).

## Use case profiles

| Profile | Typical input | UpGrd behavior |
|---------|---------------|----------------|
| `legacy-web` | Ant WAR, Spring MVC 4 + Struts, log4j 1.x | Automated framework/logging migrations; reasoning in plan + UI |
| `legacy-backend` | JDK 7 flat/Ant, minimal frameworks | Java upgrade automated; design changes **advisory only** |

Profiles are auto-detected or set with `--profile legacy-web` / `--profile legacy-backend`.

## Agent documentation & security

During **analyze**, UpGrd writes:

- `app-documentation.json` — structured knowledge base (stack, inventory, hot paths, agent guide)
- `AGENTS.md` — Markdown summary for human and AI agent onboarding
- `security-report.json` — CVE/CWE findings with remediation status
- `anti-pattern-report.json` — M5 rule pack findings (god class, SQL concat, unsafe deserialization, etc.)

During **apply**, security fixes run automatically where safe (log4j, weak crypto, hardcoded secrets). Documentation and security reports are updated with post-upgrade status.

**Migrated application** (under `migrated/`) includes:

- `AGENTS.md` and `upgrd-analysis.json` — embedded guide for automation/AI tools
- `app-web/src/test/java/com/upgrd/smoke/` — JUnit 5 smoke tests (hot paths from logs prioritized)
- Run tests: `mvn -f migrated/pom.xml test` or `upgrd verify --output ./upgrd-out`
- On failure: share `migrated/.upgrd/failure-report/anonymous-failure-report.md` with external AI (paths, secrets, and app types are redacted)

## Security

**Runtime rule:** UpGrd runs fully on the customer edge. No Cursor, LLM, or external API access to source trees, WAR files, or logs.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for design principles.

## Modules

- `upgrd-core` — discovery, analysis, planning, apply orchestration, report server
- `upgrd-recipes` — OpenRewrite recipe catalog (M2)
- `upgrd-ui` — local audit dashboard static assets
- `upgrd-cli` — command-line interface (shaded JAR)
