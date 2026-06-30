# UpGrd

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
```

## M1 status (v1.0.0)

| Command | Status |
|---------|--------|
| `analyze` | WAR/source sync, log usage heatmap, discovery |
| `plan upgrade --dry-run` | Recipe step list (OpenRewrite-oriented) |
| `apply`, `verify`, `run` | Not yet implemented |

## M2 in progress (1.1.0-SNAPSHOT)

| Command | Status |
|---------|--------|
| `apply` | Scaffold: loads plan, creates `migrated/` layout, writes `apply-report.json` |
| `upgrd-recipes` | OpenRewrite recipe catalog (execution pending) |
| Maven conversion + Java 21 rewrite | Planned |

```bash
java -jar upgrd-cli/target/upgrd.jar apply \
  --plan ./upgrd-out/upgrade-plan.json \
  --source ./legacy-app \
  --output ./upgrd-out
```

Reports are written locally to `--output` (default `./upgrd-out`).

## Security

**Runtime rule:** UpGrd runs fully on the customer edge. No Cursor, LLM, or external API access to source trees, WAR files, or logs.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for design principles.

## Modules

- `upgrd-core` — discovery, analysis, planning, apply orchestration
- `upgrd-recipes` — OpenRewrite recipe catalog (M2)
- `upgrd-cli` — command-line interface (shaded JAR)
