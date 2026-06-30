# legacy-e2e-web fixture

Self-contained Ant/Struts legacy web app used by `FullUpgradeWorkflowE2ETest`.

| Artifact | Purpose |
|----------|---------|
| `src/`, `WEB-INF/`, `pages/` | Out-of-date source tree (javax, Struts 1, log4j 1.x) |
| `logs/access.log`, `logs/server.log` | Runtime usage for `LogUsageAnalyzer` (hot paths + stack traces) |
| WAR (built in test) | Production WAR with drift: `WarOnlyAction` class + `legacy-extra.jar` not in source |

Expected analyze signals: sync severity ≥ HIGH, API compatibility hits (javax.servlet, log4j), usage hits for `UserAction` and `WarOnlyAction`.
