# UpGrd Architecture

## Non-negotiable constraints

1. **Edge-only runtime** — All analysis and migration runs on customer infrastructure. No telemetry, no cloud upload, no AI/LLM calls at runtime.
2. **Auditable output** — Same inputs produce the same plan. Every change links to a rule or recipe ID.
3. **Dual deployment** — Upgraded application code is portable Jakarta EE. Local dev/CI targets **WildFly**; production targets **WebLogic 14c**. Server differences live in `deploy/wildfly` and `deploy/weblogic` only.

## Pipeline

```
Discover → Analyze → Plan (dry-run) → Apply → Verify → Report
```

| Phase | Purpose |
|-------|---------|
| Discover | Detect build system, Java version, descriptors, dependencies |
| Analyze | WAR/source sync, log usage mapping, call graph, server-specific API scan |
| Plan | OpenRewrite recipe list, Maven conversion steps, security/test tasks |
| Apply | Rewrite source, generate POMs, server overlays |
| Verify | `mvn verify`, WildFly profile, OWASP Dependency-Check, SpotBugs |
| Report | JSON + local HTML dashboard (localhost only) |

## Module layout (target)

```
upgrd/
├── upgrd-cli/          # CLI entry (analyze, plan, apply, verify, run)
├── upgrd-core/         # Discovery, analysis, reporting
├── upgrd-recipes/      # OpenRewrite + WebLogic/WildFly recipe packs
└── upgrd-ui/           # Optional local static report UI
```

## Upgraded application output (target)

```
migrated/
├── pom.xml
├── app-web/                 # portable WAR sources
├── deploy/
│   ├── wildfly/             # local: Docker, datasource CLI, jboss-web.xml
│   └── weblogic/            # prod: weblogic.xml, jdbc bindings
└── upgrd-out/
    ├── upgrade-plan.json
    ├── sync-report.json
    ├── usage-report.json
    └── security-report.json
```

## Portable application layer

- No `weblogic.*` in business code after upgrade (rewrite or thin adapter module)
- Logical JNDI names identical across environments; binding differs per server
- Maven profiles: `-Plocal-wildfly`, `-Pproduction-weblogic`

## Technology stack

| Concern | Tool |
|---------|------|
| Migration | OpenRewrite |
| Static analysis | JavaParser, ASM, ClassGraph |
| Security | OWASP Dependency-Check, SpotBugs |
| Build | Maven 3.9+, Java 21 |
| Local app server | WildFly (Docker) |
| Production app server | WebLogic 14c |

## Milestones

- **M1** — `analyze` + `plan --dry-run` (sync, usage, recipe list)
- **M2** — Maven conversion + Java 21 apply
- **M3** — WebLogic 14c + WildFly dual-target packaging
- **M4** — Test scaffolding + security scan in `verify`
- **M5** — Anti-pattern rules + local report UI
