# UpGrd Architecture

## Non-negotiable constraints

1. **Edge-only runtime** — All analysis and migration runs on customer infrastructure. No telemetry, no cloud upload, no AI/LLM calls at runtime.
2. **Auditable output** — Same inputs produce the same plan. Every change links to a rule or recipe ID, with reason, evidence, and before/after diff when applied.
3. **Dual deployment** — Upgraded application code is portable Jakarta EE. Local dev/CI targets **WildFly**; production targets **WebLogic 14c**. Server differences live in `deploy/wildfly` and `deploy/weblogic` only.
4. **Mechanical vs advisory** — Framework/API migrations (log4j, javax→jakarta) can be auto-applied. Structural redesign (extract service layer, break god classes) is proposed with reasoning and requires explicit approval.
5. **Living documentation** — UpGrd generates structured, agent-readable documentation during analyze and apply — not as an afterthought.
6. **Security by default** — Known vulnerabilities are detected during analyze and remediated during apply where safe; remaining findings are tracked in `security-report.json`.
7. **Automation-ready output** — Migrated applications use standard Maven layout, embedded `upgrd-analysis.json`, `AGENTS.md`, and JUnit 5 smoke tests so automation tools and AI agents can analyze them without re-discovery.
8. **Anonymous failure sharing** — On verify/build failure, UpGrd generates sanitized reports under `migrated/.upgrd/failure-report/` so teams can seek external AI help without exposing proprietary business logic, paths, or secrets.

## Pipeline

```
Discover → Analyze → Plan (dry-run) → Apply → Verify → Report
```

| Phase | Purpose |
|-------|---------|
| Discover | Detect build system, Java version, technology fingerprint, project profile |
| Analyze | WAR/source sync, log usage mapping, design advisory, security scan, agent documentation |
| Plan | Profile-aware recipe list with per-step reasoning + security remediation steps |
| Apply | Rewrite source, generate POMs, fix security issues, update change ledger and documentation |
| Verify | `upgrd verify` runs `mvn verify`; `-Psecurity-verify` for SpotBugs + OWASP; anonymous failure reports on error |
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
├── AGENTS.md                    # agent onboarding (embedded in upgraded app)
├── upgrd-analysis.json          # machine-readable layout + entry points
├── .upgrd/manifest.json
├── pom.xml
├── app-web/
│   ├── src/main/java/...
│   ├── src/test/java/com/upgrd/smoke/   # JUnit 5 smoke tests
│   └── pom.xml                  # JUnit 5 + Surefire
├── deploy/
│   ├── wildfly/
│   └── weblogic/
└── upgrd-out/                   # audit reports (sibling to migrated/)
    ├── analysis-report.json
    ├── upgrade-plan.json
    ├── change-ledger.json
    ├── design-advisory.json
    ├── app-documentation.json
    ├── apply-report.json
    ├── sync-report.json
    ├── usage-report.json
    └── security-report.json
```

Note: `AGENTS.md` exists in both `upgrd-out/` (audit context) and `migrated/` (embedded in the upgraded app).

## Agent documentation (living knowledge base)

UpGrd documents the application **during** analyze and apply so future agents (and humans) can onboard without re-scanning from scratch.

`app-documentation.json` sections include:

| Section | Phase | Purpose |
|---------|-------|---------|
| Application Overview | analyze | Profile, build system, Java version |
| Technology Stack | analyze | Frameworks, logging, servlet API |
| Code Inventory | analyze | Class counts, source roots, sync status |
| Runtime Hot Paths | analyze | Log-discovered critical paths |
| Security Baseline | analyze | Findings at analysis time |
| Guide for Future Agents | analyze | How to read reports and respect advisory steps |
| Upgrade Execution | apply | Steps applied and status |
| Change Summary | apply | Link to change ledger |
| Security After Upgrade | apply | Remediated vs open findings |
| Post-Upgrade Agent Notes | apply | Migrated layout pointers |

`AGENTS.md` mirrors the JSON in Markdown for tools that prefer plain text.

## Security remediation during upgrade

Security is not deferred to a separate verify-only phase. UpGrd:

1. **Detects** during `analyze` and `plan` — classpath JARs, log4j 1.x, weak crypto, hardcoded secrets, SQL concatenation, unsafe deserialization
2. **Plans** auto-fix steps for safe, mechanical remediations (linked to CVE/CWE where applicable)
3. **Applies** fixes during `apply` via `FileRecipe` transforms — recorded in `change-ledger.json`
4. **Tracks** remediated vs open findings in `security-report.json`

| Finding | Auto-fix recipe | Notes |
|---------|-----------------|-------|
| Log4j 1.x | `upgrd:Log4j1ToSlf4j` | CVE-2019-17571 |
| Weak hash (MD5/SHA-1) | `upgrd:RemediateWeakHash` | Replaced with SHA-256 |
| Hardcoded secrets | `upgrd:ExternalizeSecrets` | `${ENV_VAR}` placeholders |
| SQL concatenation | — | Reported; manual PreparedStatement refactor |
| Unsafe deserialization | — | Reported; requires design review |

## Automation-ready migrated applications

Upgraded code lives in `migrated/` with structures that automation tools and AI agents expect:

| Artifact | Purpose |
|----------|---------|
| Standard Maven layout | `src/main/java`, `src/test/java`, `pom.xml` with Surefire |
| `upgrd-analysis.json` | Machine-readable layout map, entry points, test command |
| `migrated/AGENTS.md` | Onboarding guide embedded in the application tree |
| `com.upgrd.smoke.*` tests | JUnit 5 smoke tests for log hot paths or main classes |
| `upgrd verify` | Runs `mvn test` against the migrated project |

Apply steps: `test-scaffold` (generate tests) → `automation-ready` (embed metadata).

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
