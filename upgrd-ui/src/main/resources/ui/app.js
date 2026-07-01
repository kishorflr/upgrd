const PAGE_SIZE = 25;
const loadedTabs = new Set();

async function fetchReport(name) {
  const res = await fetch('/api/reports/' + name);
  if (!res.ok) return null;
  return res.json();
}

async function fetchReportPage(name, offset, limit, filter) {
  const params = new URLSearchParams({
    name,
    offset: String(offset),
    limit: String(limit)
  });
  if (filter && filter !== 'ALL') {
    params.set('filter', filter);
  }
  const res = await fetch('/api/reports/page?' + params);
  if (!res.ok) return null;
  return res.json();
}

function renderPager(pagerId, total, offset, limit, onPage) {
  const bar = document.getElementById(pagerId);
  if (!bar) return;
  bar.innerHTML = '';
  if (!total || total <= limit) return;
  const prev = document.createElement('button');
  prev.className = 'filter';
  prev.type = 'button';
  prev.textContent = 'Previous';
  prev.disabled = offset <= 0;
  prev.addEventListener('click', () => onPage(Math.max(0, offset - limit)));
  const info = el('span', 'pager-info',
    'Showing ' + (offset + 1) + '–' + Math.min(offset + limit, total) + ' of ' + total);
  const next = document.createElement('button');
  next.className = 'filter';
  next.type = 'button';
  next.textContent = 'Next';
  next.disabled = offset + limit >= total;
  next.addEventListener('click', () => onPage(offset + limit));
  bar.appendChild(prev);
  bar.appendChild(info);
  bar.appendChild(next);
}

function invalidateTab(panelId) {
  loadedTabs.delete(panelId);
}

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function badge(text, kind) {
  const b = el('span', 'badge badge-' + kind, text);
  return b;
}

function classificationBadge(classification) {
  if (!classification) return null;
  const kind = classification.toLowerCase().replace('_', '-');
  return badge(classification.replace('_', ' '), 'class-' + kind);
}

function renderDashboard(analysis) {
  const profileEl = document.getElementById('profile-summary');
  const fpEl = document.getElementById('fingerprint');
  const risksEl = document.getElementById('risks');

  if (!analysis) {
    profileEl.innerHTML = '<p class="empty">Run <code>upgrd analyze</code> to generate analysis-report.json</p>';
    fpEl.innerHTML = '';
    risksEl.innerHTML = '';
    return;
  }

  const d = analysis.discovery;
  profileEl.innerHTML = '';
  const dl = el('dl', 'grid');
  [['Profile', d.profile], ['Build', d.buildSystem], ['Java hint', d.javaVersionHint],
   ['WebLogic API', d.containsWeblogicApi ? 'yes' : 'no']].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v)));
  });
  profileEl.appendChild(dl);

  const fp = d.fingerprint;
  fpEl.innerHTML = '';
  const fpDl = el('dl', 'grid');
  [['Frameworks', (fp.frameworks || []).join(', ') || 'none'],
   ['Logging', fp.logging], ['Servlet API', fp.servletApi],
   ['Persistence', fp.persistenceHint]].forEach(([k, v]) => {
    fpDl.appendChild(el('dt', null, k));
    fpDl.appendChild(el('dd', null, String(v)));
  });
  fpEl.appendChild(fpDl);

  risksEl.innerHTML = '';
  const signals = fp.riskSignals || [];
  if (signals.length === 0) {
    risksEl.appendChild(el('p', 'empty', 'No risk signals detected'));
  } else {
    const ul = el('ul');
    signals.forEach(s => ul.appendChild(el('li', null, s)));
    risksEl.appendChild(ul);
  }

  renderSync(analysis && analysis.sync);
}

function renderSync(sync) {
  const el_ = document.getElementById('sync-summary');
  el_.innerHTML = '';
  if (!sync) {
    el_.appendChild(el('p', 'empty', 'Provide --war during analyze to compare production WAR vs source'));
    return;
  }
  if (sync.severity) {
    const h = el('h3');
    h.textContent = 'Sync severity: ' + sync.severity;
    h.prepend(badge(sync.severity, 'risk-' + syncSeverityClass(sync.severity)));
    el_.appendChild(h);
    if (sync.severityReason) {
      el_.appendChild(el('p', 'reason', sync.severityReason));
    }
  }
  const dl = el('dl', 'grid');
  [['WAR classes', sync.warClassCount], ['Source classes', sync.sourceClassCount],
   ['Only in WAR', (sync.onlyInWar || []).length],
   ['Only in source', (sync.onlyInSource || []).length],
   ['In both', (sync.inBoth || []).length],
   ['WAR libs', sync.warLibCount || 0],
   ['Source libs', sync.sourceLibCount || 0],
   ['Libs only in WAR', (sync.onlyInWarLibs || []).length]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v)));
  });
  el_.appendChild(dl);
  if ((sync.onlyInWar || []).length) {
    const h = el('h3', null, 'Production-only classes (WAR truth)');
    el_.appendChild(h);
    const ul = el('ul');
    sync.onlyInWar.slice(0, 15).forEach(c => ul.appendChild(el('li', null, c)));
    el_.appendChild(ul);
  }
  if ((sync.onlyInWarLibs || []).length) {
    const h = el('h3', null, 'Production-only JARs (WEB-INF/lib)');
    el_.appendChild(h);
    const ul = el('ul');
    sync.onlyInWarLibs.slice(0, 15).forEach(c => ul.appendChild(el('li', null, c)));
    el_.appendChild(ul);
  }
}

async function renderWarMerge(warMerge) {
  const el_ = document.getElementById('war-merge-summary');
  if (!el_) return;
  el_.innerHTML = '';
  if (!warMerge) {
    el_.appendChild(el('p', 'empty', 'WAR merge report appears after apply with --war'));
    return;
  }
  const dl = el('dl', 'grid');
  [['Policy', warMerge.policy], ['Merged libs', warMerge.mergedLibCount],
   ['Extracted classes', warMerge.extractedClassCount], ['Stubs', warMerge.stubCount],
   ['Conflicts', warMerge.conflictCount]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v ?? '')));
  });
  el_.appendChild(dl);
  if (warMerge.conflicts && warMerge.conflicts.length) {
    const ul = el('ul');
    warMerge.conflicts.forEach(c =>
      ul.appendChild(el('li', null, c.qualifiedClassName + ' — ' + c.resolution)));
    el_.appendChild(ul);
  }
}

function syncSeverityClass(severity) {
  if (severity === 'CRITICAL' || severity === 'HIGH') return 'high';
  if (severity === 'MEDIUM') return 'medium';
  if (severity === 'IN_SYNC' || severity === 'LOW') return 'low';
  return 'pending';
}

function renderPlan(plan) {
  const el_ = document.getElementById('plan-steps');
  el_.innerHTML = '';
  if (!plan) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd plan upgrade</code> to generate upgrade-plan.json</p>';
    return;
  }
  (plan.steps || []).forEach(step => {
    const div = el('div', 'step');
    const h = el('h3');
    h.textContent = step.description;
    h.prepend(badge(step.mode === 'ADVISORY' ? 'advisory' : 'automated',
      step.mode === 'ADVISORY' ? 'advisory' : 'automated'));
    if (step.classification) {
      const cb = classificationBadge(step.classification);
      if (cb) h.prepend(cb);
    }
    div.appendChild(h);
    div.appendChild(el('p', 'muted', '[' + step.category + '] ' + step.recipe));
    const reason = el('div', 'reason', step.reason || '');
    div.appendChild(reason);
    if (step.evidence && step.evidence.length) {
      const ul = el('ul', 'evidence');
      step.evidence.forEach(e => ul.appendChild(el('li', null, e)));
      div.appendChild(ul);
    }
    el_.appendChild(div);
  });
}

function renderChangesFromItems(container, items, emptyMessage) {
  container.innerHTML = '';
  if (!items || items.length === 0) {
    container.innerHTML = '<p class="empty">' + emptyMessage + '</p>';
    return;
  }
  renderChangeItems(container, items);
}

function renderChangeItems(container, items) {
  items.forEach(change => {
    const div = el('div', 'change');
    const h = el('h3');
    h.textContent = change.file + ' — ' + change.category;
    h.prepend(badge(change.risk || 'pending', 'risk-' + (change.risk || 'pending').toLowerCase()));
    if (change.classification) {
      const cb = classificationBadge(change.classification);
      if (cb) h.prepend(cb);
    }
    div.appendChild(h);
    div.appendChild(el('p', 'muted', change.ruleId));
    div.appendChild(el('div', 'reason', change.reason || ''));
    if (change.before && change.before.trim() && change.before !== '(generated smoke test)') {
      const diff = el('div', 'diff');
      diff.appendChild(el('p', 'muted', 'Before'));
      diff.appendChild(el('pre', 'diff-block', change.before));
      diff.appendChild(el('p', 'muted', 'After'));
      diff.appendChild(el('pre', 'diff-block after', change.after || ''));
      div.appendChild(diff);
    }
    if (change.evidence && change.evidence.length) {
      const ul = el('ul', 'evidence');
      change.evidence.forEach(e => ul.appendChild(el('li', null, e)));
      div.appendChild(ul);
    }
    container.appendChild(div);
  });
}

function renderChangeList(container, ledger, emptyMessage) {
  container.innerHTML = '';
  if (!ledger) {
    container.innerHTML = '<p class="empty">' + emptyMessage + '</p>';
    return;
  }
  renderChangeItems(container, ledger.changes || []);
}

let changesPageOffset = 0;

async function loadChangesTab(offset) {
  changesPageOffset = offset;
  const container = document.getElementById('change-ledger');
  container.innerHTML = '<p class="tab-loading">Loading change ledger…</p>';
  const page = await fetchReportPage('change-ledger.json', offset, PAGE_SIZE, 'ALL');
  if (!page) {
    container.innerHTML = '<p class="empty">Run <code>upgrd apply</code> to generate change-ledger.json</p>';
    renderPager('change-ledger-pager', 0, 0, PAGE_SIZE, loadChangesTab);
    return;
  }
  renderChangesFromItems(container, page.items, 'No changes recorded');
  renderPager('change-ledger-pager', page.total, page.offset, page.limit, loadChangesTab);
}

let previewPageOffset = 0;
let previewFilter = 'ALL';
let featureUsageCache = null;
let coverageFilter = 'ALL';

function renderPreviewSummary(preview) {
  const el_ = document.getElementById('preview-summary');
  el_.innerHTML = '';
  if (!preview) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd plan preview</code> or <code>pipeline run</code> (without <code>--confirm</code>)</p>';
    return;
  }
  const dl = el('dl', 'grid');
  [['Automated steps', preview.automatedSteps], ['Advisory steps', preview.advisorySteps],
   ['Previewed file changes', preview.previewedFileChanges]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v)));
  });
  el_.appendChild(dl);
  if (preview.steps && preview.steps.length) {
    const ul = el('ul');
    preview.steps.forEach(s => {
      const li = el('li');
      if (s.classification) li.appendChild(classificationBadge(s.classification));
      li.appendChild(document.createTextNode(' ' + s.description + ' — ' + (s.previewNote || '')));
      ul.appendChild(li);
    });
    el_.appendChild(ul);
  }
}

async function loadPreviewChangesPage(offset) {
  previewPageOffset = offset;
  const container = document.getElementById('preview-changes');
  container.innerHTML = '<p class="tab-loading">Loading preview diffs…</p>';
  const page = await fetchReportPage(
    'change-ledger-preview.json', offset, PAGE_SIZE, previewFilter);
  if (!page) {
    container.innerHTML = '<p class="empty">No preview ledger — run plan preview first</p>';
    renderPager('preview-changes-pager', 0, 0, PAGE_SIZE, loadPreviewChangesPage);
    return;
  }
  if (!page.items || page.items.length === 0) {
    container.innerHTML = '<p class="empty">No changes for filter: ' + previewFilter + '</p>';
  } else {
    renderChangesFromItems(container, page.items, '');
  }
  renderPager('preview-changes-pager', page.total, page.offset, page.limit, loadPreviewChangesPage);
}

function renderPreviewChanges(ledger) {
  if (ledger) {
    renderChangeList(document.getElementById('preview-changes'), ledger, '');
    return;
  }
  loadPreviewChangesPage(previewPageOffset);
}

let approvalState = null;
let planCache = null;

function defaultApproved(step) {
  if (step.mode === 'ADVISORY') return false;
  return step.classification === 'MANDATORY';
}

function buildApprovalFromPlan(plan, existing) {
  const existingMap = {};
  if (existing && existing.steps) {
    existing.steps.forEach(s => { existingMap[s.stepId] = s; });
  }
  const steps = (plan.steps || []).map(step => {
    const prev = existingMap[step.id];
    return {
      stepId: step.id,
      approved: prev ? prev.approved : defaultApproved(step),
      classification: step.classification,
      mode: step.mode,
      note: prev ? prev.note : (step.mode === 'ADVISORY' ? 'Advisory — manual refactor' : '')
    };
  });
  return {
    upgrdVersion: plan.upgrdVersion || 'ui',
    generatedAt: new Date().toISOString(),
    planUpgrdVersion: plan.upgrdVersion,
    sourceRoot: existing ? existing.sourceRoot : '',
    steps
  };
}

function renderApproval(plan, approval) {
  planCache = plan;
  const container = document.getElementById('approval-steps');
  container.innerHTML = '';
  if (!plan) {
    container.innerHTML = '<p class="empty">Run <code>upgrd plan upgrade</code> first</p>';
    approvalState = null;
    return;
  }
  approvalState = buildApprovalFromPlan(plan, approval);
  (plan.steps || []).forEach((step, idx) => {
    const row = el('div', 'approval-row' + (step.mode === 'ADVISORY' ? ' disabled' : ''));
    const cb = document.createElement('input');
    cb.type = 'checkbox';
    cb.id = 'approve-' + step.id;
    cb.checked = approvalState.steps[idx].approved;
    cb.disabled = step.mode === 'ADVISORY';
    cb.addEventListener('change', () => {
      approvalState.steps[idx].approved = cb.checked;
      approvalState.steps[idx].note = cb.checked ? 'Approved in UI' : 'Rejected in UI';
    });
    const label = document.createElement('label');
    label.htmlFor = cb.id;
    label.textContent = step.description + ' [' + step.category + ']';
    if (step.classification) label.prepend(classificationBadge(step.classification));
    row.appendChild(cb);
    row.appendChild(label);
    container.appendChild(row);
  });
}

async function workspaceSourceRoot() {
  try {
    const res = await fetch('/api/workspace');
    if (!res.ok) return '';
    const ws = await res.json();
    return (ws.sourceRoot || '').trim();
  } catch (_) {
    return '';
  }
}

function approveRecommended() {
  const status = document.getElementById('approval-status');
  if (!approvalState || !planCache) {
    status.textContent = 'Load a plan first (run plan upgrade)';
    return;
  }
  let count = 0;
  (planCache.steps || []).forEach(step => {
    if (step.mode === 'ADVISORY') return;
    if (step.classification !== 'MANDATORY' && step.classification !== 'RECOMMENDED') return;
    const idx = approvalState.steps.findIndex(s => s.stepId === step.id);
    if (idx < 0) return;
    approvalState.steps[idx].approved = true;
    approvalState.steps[idx].note = 'Approved recommended (UI)';
    const cb = document.getElementById('approve-' + step.id);
    if (cb) cb.checked = true;
    count++;
  });
  status.textContent = 'Selected ' + count + ' mandatory/recommended step(s) — click Save approval or Apply';
}

async function saveApproval() {
  const status = document.getElementById('approval-status');
  if (!approvalState) {
    status.textContent = 'Nothing to save';
    return;
  }
  approvalState.generatedAt = new Date().toISOString();
  const sourceRoot = await workspaceSourceRoot();
  if (sourceRoot) approvalState.sourceRoot = sourceRoot;
  const approved = approvalState.steps.filter(s => s.approved).length;
  if (approved === 0) {
    status.textContent = 'Approve at least one automated step';
    return;
  }
  try {
    const res = await fetch('/api/approval', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(approvalState)
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      status.textContent = err.error || 'Save failed';
      return;
    }
    status.textContent = 'Saved ' + approved + ' approved step(s) to approved-plan.json';
  } catch (e) {
    status.textContent = 'Save failed: ' + e.message;
  }
}

function setUpgradeButtonsDisabled(disabled) {
  ['approve-recommended', 'save-approval', 'apply-approved', 'build-verify'].forEach(id => {
    const btn = document.getElementById(id);
    if (btn) btn.disabled = disabled;
  });
}

function showUpgradeSummary(text) {
  const box = document.getElementById('upgrade-action-summary');
  if (!text) {
    box.hidden = true;
    box.textContent = '';
    return;
  }
  box.hidden = false;
  box.textContent = text;
}

function advisoryWarningMessage() {
  if (!planCache) return '';
  const advisory = (planCache.steps || []).filter(s => s.mode === 'ADVISORY');
  if (advisory.length === 0) return '';
  return advisory.length + ' advisory item(s) will not be applied (WAR drift, manual API rewrites, etc.).\n'
    + advisory.map(s => '• ' + s.description).join('\n');
}

async function applyApproved() {
  const status = document.getElementById('approval-status');
  if (!approvalState) {
    status.textContent = 'Nothing to apply — load plan first';
    return;
  }
  const approved = approvalState.steps.filter(s => s.approved).length;
  if (approved === 0) {
    status.textContent = 'Approve at least one automated step';
    return;
  }
  const advisoryMsg = advisoryWarningMessage();
  const prompt = 'Apply ' + approved + ' approved step(s) to upgrd-out/migrated/?'
    + (advisoryMsg ? '\n\n' + advisoryMsg : '');
  if (!window.confirm(prompt)) return;

  setUpgradeButtonsDisabled(true);
  status.textContent = 'Saving approval…';
  showUpgradeSummary('');
  await saveApproval();

  status.textContent = 'Applying… (may take a minute)';
  try {
    const res = await fetch('/api/upgrade/apply', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ warPolicy: 'mark-conflict' })
    });
    const body = await res.json();
    if (!res.ok) {
      status.textContent = body.error || 'Apply failed';
      return;
    }
    status.textContent = 'Applied ' + body.appliedCount + ' step(s) → ' + body.migratedRoot;
    let summary = 'Applied: ' + body.appliedCount + ' | Skipped: ' + body.skippedCount
      + ' | Migrated: ' + body.migratedRoot;
    if (body.advisoryWarnings && body.advisoryWarnings.length) {
      summary += '\n\nManual follow-up:\n' + body.advisoryWarnings.map(w => '• ' + w).join('\n');
    }
    showUpgradeSummary(summary);
    await refreshAfterApply();
  } catch (e) {
    status.textContent = 'Apply failed: ' + e.message;
  } finally {
    setUpgradeButtonsDisabled(false);
  }
}

async function buildAndVerify() {
  const status = document.getElementById('approval-status');
  setUpgradeButtonsDisabled(true);
  status.textContent = 'Running mvn verify on migrated project…';
  showUpgradeSummary('');
  try {
    const res = await fetch('/api/upgrade/verify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ securityScan: false })
    });
    const body = await res.json();
    if (!res.ok) {
      status.textContent = body.error || 'Verify failed';
      return;
    }
    status.textContent = body.passed ? 'Build passed' : 'Build failed (exit ' + body.exitCode + ')';
    let summary = (body.passed ? 'PASSED' : 'FAILED') + ' — ' + body.command;
    if (body.summaryLines && body.summaryLines.length) {
      summary += '\n' + body.summaryLines.join('\n');
    }
    if (body.logTail) {
      summary += '\n\n--- log tail ---\n' + body.logTail;
    }
    showUpgradeSummary(summary);
    await refreshAfterApply();
  } catch (e) {
    status.textContent = 'Verify failed: ' + e.message;
  } finally {
    setUpgradeButtonsDisabled(false);
  }
}

async function refreshAfterApply() {
  invalidateTab('changes');
  invalidateTab('review');
  invalidateTab('deploy');
  invalidateTab('verify');
  invalidateTab('documentation');
  const [approval, warMerge, verify, applyReport, documentation] = await Promise.all([
    fetchReport('approved-plan.json'),
    fetchReport('war-merge-report.json'),
    fetchReport('verify-report.json'),
    fetchReport('apply-report.json'),
    fetchReport('app-documentation.json')
  ]);
  renderApproval(planCache, approval);
  renderWarMerge(warMerge);
  renderVerify(verify);
  renderDeploy(applyReport, verify);
  renderDocumentation(documentation);
  if (loadedTabs.has('changes')) await loadChangesTab(changesPageOffset);
  if (loadedTabs.has('review')) {
    await loadReviewTab();
  }
}

async function refreshAfterLogAnalysis() {
  invalidateTab('coverage');
  invalidateTab('dashboard');
  const analysis = await fetchReport('analysis-report.json');
  renderDashboard(analysis);
  renderUsage(analysis);
  if (loadedTabs.has('coverage')) {
    await loadCoverageTab();
  }
}

let apiPageOffset = 0;
let apiSummaryCache = null;

async function loadApiTab(offset) {
  apiPageOffset = offset;
  const el_ = document.getElementById('api-compatibility');
  el_.innerHTML = '<p class="tab-loading">Loading API compatibility…</p>';
  const page = await fetchReportPage('api-compatibility-report.json', offset, PAGE_SIZE, 'ALL');
  if (!page) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd analyze</code> to generate api-compatibility-report.json</p>';
    renderPager('api-compatibility-pager', 0, 0, PAGE_SIZE, loadApiTab);
    return;
  }
  apiSummaryCache = page.summary;
  renderApiCompatibilityPage(el_, page.summary, page.items);
  renderPager('api-compatibility-pager', page.total, page.offset, page.limit, loadApiTab);
}

function renderApiCompatibilityPage(summaryEl, summary, hits) {
  summaryEl.innerHTML = '';
  if (!hits || hits.length === 0) {
    summaryEl.innerHTML = '<p class="empty">No API compatibility hits</p>';
    return;
  }
  if (summary) {
    summaryEl.appendChild(el('p', null, summary.summary || ('Total hits: ' + (summary.totalHits || hits.length))));
    if (summary.countsByRemediationType) {
      const dl = el('dl', 'grid');
      Object.entries(summary.countsByRemediationType).forEach(([k, v]) => {
        if (v > 0) {
          dl.appendChild(el('dt', null, k));
          dl.appendChild(el('dd', null, String(v)));
        }
      });
      summaryEl.appendChild(dl);
    }
  }
  hits.forEach(hit => {
    const div = el('div', 'change');
    const h = el('h3');
    h.textContent = hit.file + ':' + (hit.lineRange && hit.lineRange[0]) + ' — ' + hit.api;
    h.prepend(badge(hit.remediationType, 'api-' + hit.remediationType.toLowerCase()));
    div.appendChild(h);
    if (hit.planStepId) {
      div.appendChild(el('p', 'muted', 'Plan step: ' + hit.planStepId
          + (hit.recipeId ? ' | Recipe: ' + hit.recipeId : '')));
    }
    div.appendChild(el('div', 'reason', hit.description || ''));
    if (hit.replacement) {
      div.appendChild(el('p', null, 'Replacement: ' + hit.replacement));
    }
    if (hit.snippet) {
      div.appendChild(el('pre', 'diff-block', hit.snippet));
    }
    summaryEl.appendChild(div);
  });
}

function renderApiCompatibility(report) {
  if (report) {
    const el_ = document.getElementById('api-compatibility');
    renderApiCompatibilityPage(el_, report, report.hits || []);
    return;
  }
  loadApiTab(apiPageOffset);
}

let coveragePageOffset = 0;
let logManifestPageOffset = 0;

async function loadCoverageFeaturesPage(offset) {
  coveragePageOffset = offset;
  const listEl = document.getElementById('feature-usage-list');
  listEl.innerHTML = '<p class="tab-loading">Loading features…</p>';
  const page = await fetchReportPage(
    'feature-usage-report.json', offset, PAGE_SIZE, coverageFilter);
  const summaryEl = document.getElementById('feature-usage-summary');
  if (!page) {
    summaryEl.innerHTML = '<p class="empty">Configure workspace below and run log analysis, or use <code>upgrd analyze --logs-dir</code></p>';
    listEl.innerHTML = '';
    renderPager('feature-usage-pager', 0, 0, PAGE_SIZE, loadCoverageFeaturesPage);
    return;
  }
  featureUsageCache = page.summary;
  renderFeatureUsageSummary(summaryEl, page.summary);
  renderFeatureUsageItems(listEl, page.items);
  renderPager('feature-usage-pager', page.total, page.offset, page.limit, loadCoverageFeaturesPage);
}

function renderFeatureUsageSummary(summaryEl, report) {
  summaryEl.innerHTML = '';
  if (!report) {
    summaryEl.innerHTML = '<p class="empty">No feature usage data</p>';
    return;
  }
  const dl = el('dl', 'grid');
  [['Total features', report.totalFeatures],
   ['Healthy (observed, no errors)', report.healthyCount],
   ['Broken (accessed with errors)', report.brokenCount],
   ['Unobserved', report.unobservedCount],
   ['Staged log files', report.logFileCount]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v ?? '')));
  });
  summaryEl.appendChild(dl);
  if (report.hitsByLogKind) {
    summaryEl.appendChild(el('p', 'muted', 'Lines by log kind: ' +
      Object.entries(report.hitsByLogKind).map(([k, v]) => k + '=' + v).join(', ')));
  }
  if (report.notes && report.notes.length) {
    const ul = el('ul');
    report.notes.forEach(note => ul.appendChild(el('li', null, note)));
    summaryEl.appendChild(ul);
  }
}

function renderFeatureUsageItems(listEl, features) {
  listEl.innerHTML = '';
  if (!features || features.length === 0) {
    listEl.innerHTML = '<p class="empty">No features match filter</p>';
    return;
  }
  features.forEach(feature => {
    const div = el('div', 'change');
    const h = el('h3');
    h.textContent = feature.name + (feature.detail ? ' → ' + feature.detail : '');
    const healthBadge = feature.health === 'BROKEN' ? 'broken'
      : feature.health === 'HEALTHY' ? 'healthy' : 'unobserved';
    h.prepend(badge(feature.health, healthBadge));
    h.prepend(badge(feature.kind.replace(/_/g, ' '), 'kind'));
    div.appendChild(h);
    const meta = el('p', 'muted', [
      feature.deployPresence,
      feature.hitCount != null ? feature.hitCount + ' usage hit(s)' : null,
      feature.errorHitCount ? feature.errorHitCount + ' error hit(s)' : null,
      feature.observedInLogKinds && feature.observedInLogKinds.length
        ? 'logs: ' + feature.observedInLogKinds.join(', ') : null
    ].filter(Boolean).join(' | '));
    div.appendChild(meta);
    if (feature.sample) div.appendChild(el('pre', 'diff-block', feature.sample));
    if (feature.errorSample) {
      div.appendChild(el('p', 'muted', 'Error sample'));
      div.appendChild(el('pre', 'diff-block', feature.errorSample));
    }
    if (feature.migrationGuidance) div.appendChild(el('div', 'reason', feature.migrationGuidance));
    listEl.appendChild(div);
  });
}

function renderFeatureUsage(report) {
  if (report && report.features) {
    featureUsageCache = report;
    renderFeatureUsageSummary(document.getElementById('feature-usage-summary'), report);
    renderFeatureUsageItems(document.getElementById('feature-usage-list'), report.features);
    return;
  }
  loadCoverageFeaturesPage(coveragePageOffset);
}

async function loadLogManifestPage(offset) {
  logManifestPageOffset = offset;
  const container = document.getElementById('log-source-summary');
  container.innerHTML = '<p class="tab-loading">Loading log sources…</p>';
  const page = await fetchReportPage('log-source-manifest.json', offset, PAGE_SIZE, 'ALL');
  renderLogManifestPage(container, page);
  if (page) {
    renderPager('log-source-pager', page.total, page.offset, page.limit, loadLogManifestPage);
  } else {
    renderPager('log-source-pager', 0, 0, PAGE_SIZE, loadLogManifestPage);
  }
}

function renderLogManifestPage(container, page) {
  container.innerHTML = '';
  container.appendChild(el('h3', null, 'Log sources'));
  if (!page || !page.items || page.items.length === 0) {
    container.appendChild(el('p', 'empty', 'No staged logs yet'));
    return;
  }
  const ul = el('ul');
  page.items.forEach(entry => {
    const label = entry.stagedFile || entry.originalName || JSON.stringify(entry);
    const detail = (entry.kind || '') + ' ← ' + (entry.archiveSource || entry.originalName || '');
    ul.appendChild(el('li', null, label + ' (' + detail + ')'));
  });
  container.appendChild(ul);
}

function renderLogManifest(container, manifest, sources) {
  if (manifest && manifest.entries) {
    container.innerHTML = '';
    container.appendChild(el('h3', null, 'Log sources'));
    const ul = el('ul');
    manifest.entries.slice(0, 40).forEach(entry => {
      const label = entry.stagedFile || entry.originalName || JSON.stringify(entry);
      const detail = (entry.kind || '') + ' ← ' + (entry.archiveSource || entry.originalName || '');
      ul.appendChild(el('li', null, label + ' (' + detail + ')'));
    });
    if (manifest.entries.length > 40) {
      ul.appendChild(el('li', 'muted', '… and ' + (manifest.entries.length - 40) + ' more (use pager after reload)'));
    }
    container.appendChild(ul);
    return;
  }
  loadLogManifestPage(logManifestPageOffset);
}

async function loadWorkspace() {
  const res = await fetch('/api/workspace');
  if (!res.ok) return;
  const ws = await res.json();
  if (ws.sourceRoot) document.getElementById('ws-source').value = ws.sourceRoot;
  if (ws.warPath) document.getElementById('ws-war').value = ws.warPath;
  if (ws.logsDir) document.getElementById('ws-logs-dir').value = ws.logsDir;
}

async function saveWorkspace() {
  const status = document.getElementById('coverage-action-status');
  const payload = {
    sourceRoot: document.getElementById('ws-source').value.trim(),
    warPath: document.getElementById('ws-war').value.trim(),
    logsDir: document.getElementById('ws-logs-dir').value.trim()
  };
  try {
    const res = await fetch('/api/workspace', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    status.textContent = res.ok ? 'Workspace saved' : 'Save failed';
  } catch (e) {
    status.textContent = 'Save failed: ' + e.message;
  }
}

async function runLogAnalysis() {
  const status = document.getElementById('coverage-action-status');
  status.textContent = 'Analyzing logs…';
  await saveWorkspace();
  const logsDir = document.getElementById('ws-logs-dir').value.trim();
  try {
    const res = await fetch('/api/analyze/logs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ logsDir })
    });
    const body = await res.json();
    if (!res.ok) {
      status.textContent = body.error || 'Analysis failed';
      return;
    }
    status.textContent = 'Done — ' + body.stagedLogFiles + ' log file(s), ' +
      body.observedFeatures + ' observed, ' + body.brokenFeatures + ' broken';
    await refreshAfterLogAnalysis();
  } catch (e) {
    status.textContent = 'Analysis failed: ' + e.message;
  }
}

function renderDesign(report) {
  const el_ = document.getElementById('design-advisory');
  el_.innerHTML = '';
  if (!report) {
    el_.innerHTML = '<p class="empty">Design advisories appear after <code>upgrd analyze</code></p>';
    return;
  }
  (report.advisories || []).forEach(adv => {
    const div = el('div', 'advisory');
    const h = el('h3');
    h.textContent = adv.smell + ' — ' + adv.file;
    h.prepend(badge(adv.risk, 'risk-' + adv.risk.toLowerCase()));
    div.appendChild(h);
    div.appendChild(el('p', null, adv.suggestion));
    div.appendChild(el('div', 'reason', adv.reason || ''));
    el_.appendChild(div);
  });
}

function renderUsage(analysis) {
  const el_ = document.getElementById('usage-report');
  el_.innerHTML = '';
  if (!analysis || !analysis.usage) {
    el_.innerHTML = '<p class="empty">Usage data from log analysis appears in analysis-report.json</p>';
    return;
  }
  const u = analysis.usage;
  const dl = el('dl', 'grid');
  [['Total hits', u.totalHits], ['Unused WAR classes', (u.unusedInWar || []).length],
   ['Top hits', (u.hits || []).length]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v)));
  });
  el_.appendChild(dl);
  if (u.hits && u.hits.length) {
    const ul = el('ul');
    u.hits.slice(0, 20).forEach(h => {
      ul.appendChild(el('li', null, (h.qualifiedName || h.className || JSON.stringify(h)) +
        ' (' + (h.hitCount || h.count || '?') + ')'));
    });
    el_.appendChild(ul);
  }
}

function renderSecurity(security) {
  const el_ = document.getElementById('security-report');
  el_.innerHTML = '';
  if (!security) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd analyze</code> or <code>upgrd plan upgrade</code></p>';
    return;
  }
  const summary = el('p', null, 'Remediated: ' + security.remediatedCount +
    ' | Open: ' + security.openCount);
  el_.appendChild(summary);
  (security.findings || []).forEach(f => {
    const div = el('div', 'advisory');
    const h = el('h3');
    h.textContent = f.description;
    h.prepend(badge(f.remediated ? 'ok' : f.severity.toLowerCase(),
      f.remediated ? 'automated' : 'risk-' + f.severity.toLowerCase()));
    div.appendChild(h);
    div.appendChild(el('p', 'muted', (f.cveId || f.category) + ' | ' + f.file));
    div.appendChild(el('div', 'reason', f.remediation || ''));
    el_.appendChild(div);
  });
}

function renderAntiPatterns(report) {
  const el_ = document.getElementById('anti-pattern-report');
  el_.innerHTML = '';
  if (!report) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd analyze</code> to generate anti-pattern-report.json</p>';
    return;
  }
  el_.appendChild(el('p', null, 'Total findings: ' + (report.totalFindings || 0)));
  if (report.countsBySeverity) {
    const dl = el('dl', 'grid');
    Object.entries(report.countsBySeverity).forEach(([k, v]) => {
      dl.appendChild(el('dt', null, k));
      dl.appendChild(el('dd', null, String(v)));
    });
    el_.appendChild(dl);
  }
  (report.findings || []).forEach(f => {
    const div = el('div', 'advisory');
    const h = el('h3');
    h.textContent = f.pattern + ' — ' + f.file;
    h.prepend(badge(f.severity, 'risk-' + f.severity.toLowerCase()));
    div.appendChild(h);
    div.appendChild(el('p', 'muted', f.ruleId + ' | ' + f.category));
    div.appendChild(el('div', 'reason', f.suggestion || ''));
    div.appendChild(el('p', null, f.rationale || ''));
    el_.appendChild(div);
  });
}

function renderVerify(verify) {
  const el_ = document.getElementById('verify-report');
  el_.innerHTML = '';
  if (!verify) {
    el_.innerHTML = '<p class="empty">Run <strong>Build &amp; verify</strong> on the Review tab, or <code>upgrd verify --output ./upgrd-out</code></p>';
    return;
  }
  const status = verify.passed ? badge('passed', 'automated') : badge('failed', 'risk-high');
  const h = el('h3');
  h.textContent = verify.passed ? 'Verification passed' : 'Verification failed';
  h.prepend(status);
  el_.appendChild(h);
  const dl = el('dl', 'grid');
  [['Exit code', verify.exitCode], ['Security scan', verify.securityScan ? 'yes' : 'no'],
   ['Command', verify.command]].forEach(([k, v]) => {
    dl.appendChild(el('dt', null, k));
    dl.appendChild(el('dd', null, String(v ?? '')));
  });
  el_.appendChild(dl);
  if (verify.summaryLines && verify.summaryLines.length) {
    const ul = el('ul');
    verify.summaryLines.forEach(line => ul.appendChild(el('li', null, line)));
    el_.appendChild(ul);
  }
  if (verify.wildflySmoke && verify.wildflySmoke.checked) {
    const wf = verify.wildflySmoke;
    el_.appendChild(el('h3', null, 'WildFly'));
    const dl2 = el('dl', 'grid');
    [['Scaffold', wf.scaffoldPresent ? 'present' : 'missing'],
     ['Docker', wf.dockerAvailable ? 'available' : 'not found'],
     ['Container', wf.containerRunning ? 'running' : 'stopped'],
     ['WAR built', wf.warBuilt ? 'yes' : 'no'],
     ['Deployed', wf.deployed ? 'yes' : 'no'],
     ['HTTP checked', wf.httpChecked ? 'yes' : 'no'],
     ['HTTP reachable', wf.httpReachable ? 'yes (' + wf.httpStatusCode + ')' : 'no']].forEach(([k, v]) => {
      dl2.appendChild(el('dt', null, k));
      dl2.appendChild(el('dd', null, String(v)));
    });
    el_.appendChild(dl2);
    if (wf.notes && wf.notes.length) {
      const ul = el('ul');
      wf.notes.forEach(note => ul.appendChild(el('li', null, note)));
      el_.appendChild(ul);
    }
  }
}

function renderDeploy(applyReport, verify) {
  const el_ = document.getElementById('deploy-pipeline');
  el_.innerHTML = '';
  if (!applyReport && !verify) {
    el_.innerHTML = '<p class="empty">Use <strong>Apply approved steps</strong> and <strong>Build &amp; verify</strong> on the Review tab</p>';
    return;
  }
  if (applyReport && applyReport.steps) {
    el_.appendChild(el('h3', null, 'Apply steps'));
    const ul = el('ul');
    applyReport.steps.forEach(step => {
      const li = el('li');
      const status = step.status === 'APPLIED' ? badge('applied', 'automated')
          : step.status === 'ADVISORY' ? badge('advisory', 'advisory')
          : badge(step.status || 'pending', 'pending');
      li.appendChild(status);
      li.appendChild(document.createTextNode(' ' + (step.description || step.id)));
      ul.appendChild(li);
    });
    el_.appendChild(ul);
  }
  if (verify && verify.wildflySmoke && verify.wildflySmoke.checked) {
    el_.appendChild(el('h3', null, 'WildFly (from verify)'));
    const wf = verify.wildflySmoke;
    const dl = el('dl', 'grid');
    [['HTTP reachable', wf.httpReachable ? 'yes' : 'no'],
     ['Container', wf.containerRunning ? 'running' : 'stopped'],
     ['WAR deployed', wf.deployed ? 'yes' : 'no']].forEach(([k, v]) => {
      dl.appendChild(el('dt', null, k));
      dl.appendChild(el('dd', null, String(v)));
    });
    el_.appendChild(dl);
  }
  el_.appendChild(el('p', 'muted', 'WebLogic: run upgrd weblogic validate — production wldeploy templates in migrated/deploy/weblogic/'));
}

function renderDocumentation(doc) {
  const el_ = document.getElementById('app-documentation');
  el_.innerHTML = '';
  if (!doc) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd analyze</code> to generate app-documentation.json and AGENTS.md</p>';
    return;
  }
  el_.appendChild(el('p', null, doc.summary));
  (doc.sections || []).forEach(section => {
    const div = el('div', 'step');
    div.appendChild(el('h3', null, section.title));
    div.appendChild(el('p', 'muted', section.phase + ' | ' + section.category));
    div.appendChild(el('pre', 'reason', section.content));
    el_.appendChild(div);
  });
}

async function loadDashboardTab() {
  const [analysis, warMerge] = await Promise.all([
    fetchReport('analysis-report.json'),
    fetchReport('war-merge-report.json')
  ]);
  renderDashboard(analysis);
  renderWarMerge(warMerge);
  renderUsage(analysis);
}

async function loadPlanTab() {
  const plan = await fetchReport('upgrade-plan.json');
  renderPlan(plan);
}

async function loadReviewTab() {
  const [previewReport, plan, approval] = await Promise.all([
    fetchReport('upgrade-preview-report.json'),
    fetchReport('upgrade-plan.json'),
    fetchReport('approved-plan.json')
  ]);
  planCache = plan;
  renderPreviewSummary(previewReport);
  renderApproval(plan, approval);
  await loadPreviewChangesPage(previewPageOffset);
}

async function loadCoverageTab() {
  await Promise.all([
    loadLogManifestPage(logManifestPageOffset),
    loadCoverageFeaturesPage(coveragePageOffset)
  ]);
}

async function loadDesignTab() {
  renderDesign(await fetchReport('design-advisory.json'));
}

async function loadAntipatternsTab() {
  renderAntiPatterns(await fetchReport('anti-pattern-report.json'));
}

async function loadSecurityTab() {
  renderSecurity(await fetchReport('security-report.json'));
}

async function loadVerifyTab() {
  renderVerify(await fetchReport('verify-report.json'));
}

async function loadDeployTab() {
  const [applyReport, verify] = await Promise.all([
    fetchReport('apply-report.json'),
    fetchReport('verify-report.json')
  ]);
  renderDeploy(applyReport, verify);
}

async function loadDocumentationTab() {
  renderDocumentation(await fetchReport('app-documentation.json'));
}

const TAB_LOADERS = {
  dashboard: loadDashboardTab,
  plan: loadPlanTab,
  review: loadReviewTab,
  compatibility: () => loadApiTab(apiPageOffset),
  coverage: loadCoverageTab,
  changes: () => loadChangesTab(changesPageOffset),
  design: loadDesignTab,
  antipatterns: loadAntipatternsTab,
  security: loadSecurityTab,
  verify: loadVerifyTab,
  deploy: loadDeployTab,
  documentation: loadDocumentationTab
};

async function activateTab(panelId) {
  document.querySelectorAll('.tab').forEach(b => {
    b.classList.toggle('active', b.dataset.panel === panelId);
  });
  document.querySelectorAll('.panel').forEach(p => {
    p.classList.toggle('active', p.id === panelId);
  });
  if (!loadedTabs.has(panelId) && TAB_LOADERS[panelId]) {
    await TAB_LOADERS[panelId]();
    loadedTabs.add(panelId);
  }
}

document.querySelectorAll('.tab').forEach(btn => {
  btn.addEventListener('click', () => {
    activateTab(btn.dataset.panel);
  });
});

document.querySelectorAll('#review-filters .filter').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('#review-filters .filter').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    previewFilter = btn.dataset.classification;
    previewPageOffset = 0;
    loadPreviewChangesPage(0);
  });
});

document.querySelectorAll('#coverage-filters .filter').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('#coverage-filters .filter').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    coverageFilter = btn.dataset.health;
    coveragePageOffset = 0;
    loadCoverageFeaturesPage(0);
  });
});

document.getElementById('save-workspace').addEventListener('click', saveWorkspace);
document.getElementById('run-log-analysis').addEventListener('click', runLogAnalysis);

document.getElementById('save-approval').addEventListener('click', saveApproval);
document.getElementById('approve-recommended').addEventListener('click', approveRecommended);
document.getElementById('apply-approved').addEventListener('click', applyApproved);
document.getElementById('build-verify').addEventListener('click', buildAndVerify);

async function init() {
  loadWorkspace();
  await activateTab('dashboard');
}

init();
