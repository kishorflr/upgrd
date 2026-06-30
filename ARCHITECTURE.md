# UpGrd Architecture

## Non-negotiable constraints

1. **Edge-only runtime** — All analysis and migration runs on customer infrastructure. No telemetry, no cloud upload, no AI/LLM calls at runtime.
2. **Auditable output** — Same inputs produce the same plan. Every change links to a rule or recipe ID, with reason, evidence, and before/after diff when applied.
3. **Dual deployment** — Upgraded application code is portable Jakarta EE. Local dev/CI targets **WildFly**; production targets **WebLogic 14c**. Server differences live in `deploy/wildfly` and `deploy/weblogic` only.
4. **Mechanical vs advisory** — Framework/API migrations (log4j, javax→jakarta) can be auto-applied. Structural redesign (extract service layer, break god classes) is proposed with reasoning and requires explicit approval.

## Pipeline

```
Discover → Analyze → Plan (dry-run) → Apply → Verify → Report
```

| Phase | Purpose |
|-------|---------|
| Discover | Detect build system, Java version, technology fingerprint, project profile |
| Analyze | WAR/source sync, log usage mapping, design advisory, risk signals |
| Plan | Profile-aware OpenRewrite recipe list with per-step reasoning |
| Apply | Rewrite source, generate POMs, server overlays, change ledger |
| Verify | `mvn verify`, WildFly profile, OWASP Dependency-Check, SpotBugs |
| Report | JSON reports + local HTML dashboard (localhost only) |

## Use case profiles

| | **Profile A: Legacy web app** | **Profile B: Legacy backend** |
|---|---|---|
| **Build** | Ant / IDE / custom (non-Maven) | Ant or flat layout |
| **Stack** | Spring MVC 4.x + Struts, log4j 1.x, javax APIs | Plain Java, minimal frameworks |
| **Pain** | Framework migration, logging, servlet/Jakarta, WAR packaging | Language upgrade + structural/design debt |
| **Upgrade goal** | Modern web stack (Spring 6 / Boot 3), Java 21, Maven | Java 21, cleaner layering, testability |
| **Apply mode** | Mostly automated (framework/API migrations) | Language automated; design changes advisory |

Profiles are auto-detected from `TechnologyFingerprint` or can be overridden via `--profile`.

## Technology fingerprint

Discovery produces a structured fingerprint beyond build system:

```
TechnologyFingerprint
├── frameworks: [SPRING_MVC_4, STRUTS_1|2, ...]
├── logging: LOG4J_1 | LOG4J_2 | JUL | SLF4J | MIXED
├── servletApi: JAVAX | JAKARTA | MIXED | NONE
├── persistence: JDBC | HIBERNATE | JPA | UNKNOWN
└── riskSignals: [deprecated-api, thread-unsafe-singleton, god-class, ...]
```

## Change ledger (audit trail)

Every planned and applied change carries accountability metadata:

```json
{
  "changeId": "migrate-log4j1-0042",
  "ruleId": "upgrd:Log4j1ToSlf4j",
  "category": "logging",
  "file": "src/com/example/UserAction.java",
  "lineRange": [12, 14],
  "before": "private static Logger log = Logger.getLogger(UserAction.class);",
  "after": "private static final Logger log = LoggerFactory.getLogger(UserAction.class);",
  "reason": "Log4j 1.x is EOL with known CVEs; SLF4J is required for Spring Boot 3.",
  "evidence": ["import org.apache.log4j.Logger", "classpath:log4j-1.2.17.jar"],
  "risk": "LOW",
  "reversible": true,
  "recipeVersion": "1.1.0",
  "automated": true
}
```

## Module layout

```
upgrd/
├── upgrd-cli/          # CLI entry (analyze, plan, apply, verify, run)
├── upgrd-core/         # Discovery, analysis, planning, reporting, report server
├── upgrd-recipes/      # OpenRewrite + WebLogic/WildFly recipe packs
└── upgrd-ui/           # Local static audit dashboard (localhost only)
```

## Upgraded application output

```
migrated/
├── pom.xml
├── app-web/                 # portable WAR sources
├── deploy/
│   ├── wildfly/             # local: Docker, datasource CLI, jboss-web.xml
│   └── weblogic/            # prod: weblogic.xml, jdbc bindings
└── upgrd-out/
    ├── analysis-report.json
    ├── upgrade-plan.json
    ├── change-ledger.json
    ├── design-advisory.json
    ├── apply-report.json
    ├── sync-report.json
    ├── usage-report.json
    └── security-report.json
```

## Local audit UI

`upgrd run --serve-ui` serves a read-only dashboard on localhost. It reads JSON reports from `--output` only — no cloud, no AI.

| View | Data source |
|------|-------------|
| Dashboard | `analysis-report.json` — profile, fingerprint, risk summary |
| Plan | `upgrade-plan.json` — steps with reasoning per step |
| Changes | `change-ledger.json` — file-by-file diffs linked to rules |
| Design | `design-advisory.json` — Profile B smells + suggested refactors |
| Usage | `usage-report.json` — log heatmap |
| Security | `security-report.json` — CVE / dependency findings (M4) |

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

| Milestone | Focus |
|-----------|-------|
| **M1** | `analyze` + `plan --dry-run` (sync, usage, recipe list) |
| **M2** | Ant→Maven, Java 21 apply, change ledger schema |
| **M2.5** | Framework fingerprint + Spring/Struts/log4j detection, profile-aware planning |
| **M3** | Framework migration recipes (Struts, Spring 4→6, log4j1) |
| **M3.5** | Design advisory engine (smells, layering suggestions) |
| **M4** | `verify` + security scan + audit UI (dashboard, diffs, export) |
| **M5** | Anti-pattern rules + PDF/JSON audit export |
