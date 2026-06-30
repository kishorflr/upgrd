# UpGrd

![CI](https://github.com/kishorflr/upgrd/actions/workflows/ci.yml/badge.svg)

Edge-local Java modernization toolkit. Analyzes legacy applications (source, WAR, logs), plans upgrades, and produces an upgraded Maven-based codebase — without sending customer data to any AI or cloud service.

## Prerequisites

- **Java 21** (JDK)
- **Maven 3.9+**

```bash
# macOS (Homebrew)
brew install openjdk@21 maven
```

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
| `analyze` | Profile detection, fingerprint, design advisory, security scan, `app-documentation.json` + `AGENTS.md` |
| `plan upgrade --dry-run` | Profile-aware steps + security remediation steps from findings |
| `apply` | Source migration, deploy overlays, security fixes, JUnit 5 smoke tests, automation metadata |
| `verify` | Runs `mvn verify`; optional `-Psecurity-verify`, `--wildfly-smoke`, `--wildfly-deploy`, `--wildfly-http` |
| `weblogic` | `status` / `validate` — production deploy scaffold checks (no Docker) |
| `wildfly` | `start` / `stop` / `deploy` / `undeploy` / `status` — local Docker WildFly |
| `rewrite run` | OpenRewrite AST migrations via Maven plugin (`--dry-run`, `--recipe`, `--require-dry-run`, `--force`) |
| `report-failure` | Sanitized AI-safe failure export from captured logs |
| `export` | Bundle audit reports into JSON/Markdown; `--html` and `--pdf` for sign-off |
| `run --serve-ui` | Local audit dashboard with diffs, verify status, and security tab |
| `pipeline run` | Full analyze → plan → apply → verify in one command (`--rewrite`, `--serve-ui`) |
| **Recipes (implemented)** | Ant→Maven, Java 21, log4j→SLF4J, Struts→Spring (form beans, config paths, Thymeleaf views, JSP scaffolds), Spring 4→6, javax→jakarta, raw collections, security fixes |
| **Recipes (planned)** | Jakarta validation annotations on form beans, OpenRewrite SQL search recipes |

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

### One-shot pipeline

```bash
upgrd pipeline run \
  --source ./legacy-app \
  --war ./legacy-app.war \
  --output ./upgrd-out \
  --profile legacy-web

# Backend (no WAR):
upgrd pipeline run --source ./legacy-backend --output ./upgrd-out --profile legacy-backend --skip-verify

# Open audit dashboard when done:
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --skip-verify --serve-ui

# Optional OpenRewrite after apply:
upgrd pipeline run --source ./legacy-app --output ./upgrd-out --profile legacy-web --skip-verify --rewrite --rewrite-dry-run
```

Open http://127.0.0.1:8765 for the audit dashboard (profile, plan reasoning, change ledger, design advisory).

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
