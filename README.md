# UpGrd

Edge-local Java modernization toolkit. Analyzes legacy applications (source, WAR, logs), plans upgrades, and produces an upgraded Maven-based codebase — without sending customer data to any AI or cloud service.

## Goals

- Detect source vs deployed WAR drift
- Map runtime usage from logs (used vs unused components)
- Plan and apply upgrades: Java 21, Jakarta EE, WebLogic 14c (production), Maven conversion
- Run locally on **WildFly**; deploy to **WebLogic** with minimal application code changes
- Add test scaffolding and fix security vulnerabilities (deterministic tooling only)

## Security

**Runtime rule:** UpGrd runs fully on the customer edge. No Cursor, LLM, or external API access to source trees, WAR files, or logs.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for design principles and pipeline phases.

## Status

Early bootstrap — repository initialized, implementation not started.

## Planned CLI

```bash
upgrd analyze  --source ./app --war ./app.war --logs ./logs/
upgrd plan     upgrade --target java21 --server weblogic-14c --dry-run
upgrd apply    upgrade --plan ./upgrd-out/upgrade-plan.json
upgrd verify   --plan ./upgrd-out/upgrade-plan.json
upgrd run      local --profile wildfly
```

## Development

Built with Java 21 and Maven. Use Cursor (or any IDE) only for developing UpGrd itself — never as part of customer runtime.

```bash
mvn verify
```
