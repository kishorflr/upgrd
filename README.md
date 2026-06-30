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

## M1 status (current)

| Command | Status |
|---------|--------|
| `analyze` | WAR/source sync, log usage heatmap, discovery |
| `plan upgrade --dry-run` | Recipe step list (OpenRewrite-oriented) |
| `apply`, `verify`, `run` | Not yet implemented |

Reports are written locally to `--output` (default `./upgrd-out`).

## Security

**Runtime rule:** UpGrd runs fully on the customer edge. No Cursor, LLM, or external API access to source trees, WAR files, or logs.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for design principles.

## Modules

- `upgrd-core` — discovery, analysis, planning
- `upgrd-cli` — command-line interface (shaded JAR)
