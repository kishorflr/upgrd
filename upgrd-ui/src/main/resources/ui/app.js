async function fetchReport(name) {
  const res = await fetch('/api/reports/' + name);
  if (!res.ok) return null;
  return res.json();
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

function renderChanges(ledger) {
  renderChangeList(document.getElementById('change-ledger'), ledger,
    'Run <code>upgrd apply</code> to generate change-ledger.json');
}

function renderChangeList(container, ledger, emptyMessage) {
  container.innerHTML = '';
  if (!ledger) {
    container.innerHTML = '<p class="empty">' + emptyMessage + '</p>';
    return;
  }
  (ledger.changes || []).forEach(change => {
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

let previewLedgerCache = null;
let previewFilter = 'ALL';

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

function renderPreviewChanges(ledger) {
  previewLedgerCache = ledger;
  const container = document.getElementById('preview-changes');
  container.innerHTML = '';
  if (!ledger) {
    container.innerHTML = '<p class="empty">No preview ledger — run plan preview first</p>';
    return;
  }
  const filtered = (ledger.changes || []).filter(c =>
    previewFilter === 'ALL' || c.classification === previewFilter);
  if (filtered.length === 0) {
    container.innerHTML = '<p class="empty">No changes for filter: ' + previewFilter + '</p>';
    return;
  }
  renderChangeList(container, { ...ledger, changes: filtered }, '');
}

let approvalState = null;

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

async function saveApproval() {
  const status = document.getElementById('approval-status');
  if (!approvalState) {
    status.textContent = 'Nothing to save';
    return;
  }
  approvalState.generatedAt = new Date().toISOString();
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
    el_.innerHTML = '<p class="empty">Run <code>upgrd verify --output ./upgrd-out</code></p>';
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
    el_.innerHTML = '<p class="empty">Run <code>upgrd apply</code> and <code>upgrd verify --wildfly-smoke</code></p>';
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

document.querySelectorAll('.tab').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('.tab').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    btn.classList.add('active');
    document.getElementById(btn.dataset.panel).classList.add('active');
  });
});

document.querySelectorAll('#review-filters .filter').forEach(btn => {
  btn.addEventListener('click', () => {
    document.querySelectorAll('#review-filters .filter').forEach(b => b.classList.remove('active'));
    btn.classList.add('active');
    previewFilter = btn.dataset.classification;
    renderPreviewChanges(previewLedgerCache);
  });
});

document.getElementById('save-approval').addEventListener('click', saveApproval);

async function init() {
  const [analysis, plan, ledger, previewReport, previewLedger, approval, design, antiPatterns, security, verify, applyReport, documentation] = await Promise.all([
    fetchReport('analysis-report.json'),
    fetchReport('upgrade-plan.json'),
    fetchReport('change-ledger.json'),
    fetchReport('upgrade-preview-report.json'),
    fetchReport('change-ledger-preview.json'),
    fetchReport('approved-plan.json'),
    fetchReport('design-advisory.json'),
    fetchReport('anti-pattern-report.json'),
    fetchReport('security-report.json'),
    fetchReport('verify-report.json'),
    fetchReport('apply-report.json'),
    fetchReport('app-documentation.json')
  ]);
  renderDashboard(analysis);
  renderPlan(plan);
  renderChanges(ledger);
  renderPreviewSummary(previewReport);
  renderPreviewChanges(previewLedger);
  renderApproval(plan, approval);
  renderDesign(design);
  renderAntiPatterns(antiPatterns);
  renderUsage(analysis);
  renderSecurity(security);
  renderVerify(verify);
  renderDeploy(applyReport, verify);
  renderDocumentation(documentation);
}

init();
