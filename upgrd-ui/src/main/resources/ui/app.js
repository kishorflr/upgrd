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
  const el_ = document.getElementById('change-ledger');
  el_.innerHTML = '';
  if (!ledger) {
    el_.innerHTML = '<p class="empty">Run <code>upgrd apply</code> to generate change-ledger.json</p>';
    return;
  }
  (ledger.changes || []).forEach(change => {
    const div = el('div', 'change');
    const h = el('h3');
    h.textContent = change.file + ' — ' + change.category;
    h.prepend(badge(change.risk || 'pending', 'risk-' + (change.risk || 'pending').toLowerCase()));
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
    el_.appendChild(div);
  });
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
     ['Deployed', wf.deployed ? 'yes' : 'no']].forEach(([k, v]) => {
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

async function init() {
  const [analysis, plan, ledger, design, antiPatterns, security, verify, documentation] = await Promise.all([
    fetchReport('analysis-report.json'),
    fetchReport('upgrade-plan.json'),
    fetchReport('change-ledger.json'),
    fetchReport('design-advisory.json'),
    fetchReport('anti-pattern-report.json'),
    fetchReport('security-report.json'),
    fetchReport('verify-report.json'),
    fetchReport('app-documentation.json')
  ]);
  renderDashboard(analysis);
  renderPlan(plan);
  renderChanges(ledger);
  renderDesign(design);
  renderAntiPatterns(antiPatterns);
  renderUsage(analysis);
  renderSecurity(security);
  renderVerify(verify);
  renderDocumentation(documentation);
}

init();
