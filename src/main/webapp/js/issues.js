/**
 * CAMS - Equipment Issue & Return Management Client
 */

let currentUser = null;
let currentReturningIssueId = null;

document.addEventListener('DOMContentLoaded', () => {
  init();
});

async function init() {
  await checkSession();
  setupEventListeners();
  await loadDropdownOptions();
  await loadIssues();
}

async function checkSession() {
  try {
    const res = await fetch('../api/auth/session');
    const data = await res.json();
    if (!data.success || !data.data) {
      window.location.href = 'login.html';
      return;
    }
    currentUser = data.data;

    const userBadge = document.getElementById('sessionUser');
    if (userBadge) {
      userBadge.textContent = currentUser.name + ' (' + currentUser.role + ')';
    }

    const dashNav = document.getElementById('dashNav');
    if (dashNav) {
      if (currentUser.role === 'Administrator') dashNav.href = 'admin/dashboard.html';
      else if (currentUser.role === 'Faculty') dashNav.href = 'faculty/dashboard.html';
      else if (currentUser.role === 'Technical Staff') dashNav.href = 'technical/dashboard.html';
    }

    // Role adaptations
    if (currentUser.role === 'Administrator') {
      document.getElementById('adminLinks').style.display = 'inline-flex';
      document.getElementById('adminUserField').style.display = 'block';
    } else if (currentUser.role === 'Technical Staff') {
      // Technical Staff has read-only access
      const issueBtn = document.getElementById('openIssueModalBtn');
      if (issueBtn) issueBtn.style.display = 'none';
      document.getElementById('headerSubtitle').textContent =
        'Audit active equipment issuances and review maintenance context for returned items.';
    }
  } catch (err) {
    window.location.href = 'login.html';
  }
}

function setupEventListeners() {
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      await fetch('../api/auth/logout', { method: 'POST' });
      window.location.href = 'login.html';
    });
  }

  const openIssueModalBtn = document.getElementById('openIssueModalBtn');
  if (openIssueModalBtn) {
    openIssueModalBtn.addEventListener('click', openIssueModal);
  }

  document.getElementById('filterBtn').addEventListener('click', loadIssues);
  document.getElementById('resetBtn').addEventListener('click', () => {
    document.getElementById('filterStatus').value = 'ISSUED';
    document.getElementById('filterAsset').value = '';
    document.getElementById('filterDept').value = 'ALL';
    loadIssues();
  });

  document.getElementById('issueForm').addEventListener('submit', handleIssueSubmit);
  document.getElementById('returnForm').addEventListener('submit', handleReturnSubmit);
}

async function loadDropdownOptions() {
  try {
    const res = await fetch('../api/assets/options');
    const json = await res.json();
    if (json.success && json.data) {
      const depts = json.data.departments || [];
      const filterDept = document.getElementById('filterDept');
      const modalDept = document.getElementById('modalDeptSelect');

      filterDept.innerHTML = '<option value="ALL">All Departments</option>' +
        depts.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('');

      modalDept.innerHTML = '<option value="">Select Department *</option>' +
        depts.map(d => `<option value="${escapeHtml(d)}">${escapeHtml(d)}</option>`).join('');
    }
  } catch (e) {
    console.error('Failed to load options', e);
  }
}

async function openIssueModal() {
  document.getElementById('issueFormError').style.display = 'none';
  const assetSelect = document.getElementById('modalAssetSelect');
  assetSelect.innerHTML = '<option value="">Loading available assets...</option>';

  const todayStr = new Date().toISOString().split('T')[0];
  document.getElementById('modalIssueDate').value = todayStr;
  document.getElementById('modalIssueDate').max = todayStr;
  document.getElementById('modalExpectedDate').min = todayStr;
  document.getElementById('modalExpectedDate').value = '';
  document.getElementById('modalRemarks').value = '';

  document.getElementById('issueModal').style.display = 'flex';

  try {
    const res = await fetch('../api/assets?status=AVAILABLE&size=100');
    const json = await res.json();
    if (json.success && json.data && json.data.items) {
      const items = json.data.items;
      if (items.length === 0) {
        assetSelect.innerHTML = '<option value="">No assets currently AVAILABLE</option>';
      } else {
        assetSelect.innerHTML = '<option value="">Select an available asset *</option>' +
          items.map(a => `<option value="${escapeHtml(a.assetId)}">${escapeHtml(a.assetId)} - ${escapeHtml(a.assetName)} (${escapeHtml(a.category)})</option>`).join('');
      }
    }
  } catch (e) {
    assetSelect.innerHTML = '<option value="">Error loading available assets</option>';
  }
}

async function handleIssueSubmit(e) {
  e.preventDefault();
  const errorEl = document.getElementById('issueFormError');
  errorEl.style.display = 'none';

  const assetId = document.getElementById('modalAssetSelect').value;
  const deptId = document.getElementById('modalDeptSelect').value;
  const issueDate = document.getElementById('modalIssueDate').value;
  const expectedDate = document.getElementById('modalExpectedDate').value || null;
  const remarks = document.getElementById('modalRemarks').value.trim() || null;
  const userIdVal = document.getElementById('modalUserIdInput') ? document.getElementById('modalUserIdInput').value : null;

  if (!assetId || !deptId || !issueDate) {
    errorEl.textContent = 'Please fill all required fields.';
    errorEl.style.display = 'block';
    return;
  }

  const payload = {
    assetId: assetId,
    issuedToDepartment: deptId,
    issueDate: issueDate,
    expectedReturnDate: expectedDate,
    remarks: remarks
  };

  if (currentUser.role === 'Administrator' && userIdVal) {
    payload.issuedToUserId = parseInt(userIdVal);
  }

  try {
    const res = await fetch('../api/issues', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const json = await res.json();

    if (!json.success) {
      errorEl.textContent = json.message || 'Issuance failed.';
      errorEl.style.display = 'block';
      return;
    }

    closeModal('issueModal');
    loadIssues();
  } catch (err) {
    errorEl.textContent = 'Network error: ' + err.message;
    errorEl.style.display = 'block';
  }
}

async function loadIssues() {
  const tbody = document.getElementById('issuesTableBody');
  tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem; color: var(--text-dim);">Loading transactions...</td></tr>';

  const status = document.getElementById('filterStatus').value;
  const assetId = document.getElementById('filterAsset').value.trim();
  const dept = document.getElementById('filterDept').value;

  let url = `../api/issues?status=${encodeURIComponent(status)}&page=1&size=50`;
  if (assetId) url += `&assetId=${encodeURIComponent(assetId)}`;
  if (dept && dept !== 'ALL') url += `&department=${encodeURIComponent(dept)}`;

  try {
    const res = await fetch(url);
    const json = await res.json();

    if (!json.success || !json.data || !json.data.items) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">${escapeHtml(json.message || 'Failed to load issues')}</td></tr>`;
      return;
    }

    renderTable(json.data.items);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">Error: ${escapeHtml(err.message)}</td></tr>`;
  }
}

function renderTable(items) {
  const tbody = document.getElementById('issuesTableBody');
  if (items.length === 0) {
    tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; padding: 2rem; color: var(--text-dim);">No equipment issue transactions found.</td></tr>';
    return;
  }

  tbody.innerHTML = items.map(issue => {
    const isIssued = issue.status === 'ISSUED';
    const canReturn = isIssued && (
      currentUser.role === 'Administrator' ||
      (currentUser.role === 'Faculty' && issue.issuedToUserId === currentUser.userId)
    );

    return `
      <tr>
        <td style="font-family: 'JetBrains Mono', monospace; font-weight: 600;">${escapeHtml(issue.issueId)}</td>
        <td>
          <div style="font-weight: 600;">${escapeHtml(issue.assetName || issue.assetId)}</div>
          <small style="color: var(--text-dim); font-family: monospace;">${escapeHtml(issue.assetId)}</small>
        </td>
        <td>
          <div>${escapeHtml(issue.issuedToUserName || 'User #' + issue.issuedToUserId)}</div>
          <small style="color: var(--text-dim);">${escapeHtml(issue.departmentName || issue.issuedToDepartment)}</small>
        </td>
        <td>${escapeHtml(issue.issueDate)}</td>
        <td>${escapeHtml(issue.expectedReturnDate || '—')}</td>
        <td>${escapeHtml(issue.actualReturnDate || '—')}</td>
        <td>
          <span class="badge ${isIssued ? 'badge-issued' : 'badge-returned'}">
            ${escapeHtml(issue.status)}
          </span>
        </td>
        <td style="text-align: right; white-space: nowrap;">
          <button class="btn" style="padding: 0.25rem 0.6rem; font-size: 0.75rem; background: rgba(99, 102, 241, 0.15); color: #818cf8; border: 1px solid rgba(99, 102, 241, 0.3); border-radius: 4px; cursor: pointer; margin-right: 0.35rem;" onclick="viewIssue('${escapeHtml(issue.issueId)}')">View</button>
          ${canReturn ? `<button class="btn" style="padding: 0.25rem 0.6rem; font-size: 0.75rem; background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 4px; cursor: pointer;" onclick="openReturnModal('${escapeHtml(issue.issueId)}', '${escapeHtml(issue.assetId)}')">Return</button>` : ''}
        </td>
      </tr>
    `;
  }).join('');
}

function openReturnModal(issueId, assetId) {
  currentReturningIssueId = issueId;
  document.getElementById('returnFormError').style.display = 'none';
  document.getElementById('returnIssueIdDisplay').textContent = issueId;
  document.getElementById('returnAssetDisplay').textContent = assetId;
  document.getElementById('returnConditionSelect').value = 'GOOD';
  document.getElementById('returnRemarksInput').value = '';
  document.getElementById('returnModal').style.display = 'flex';
}

async function handleReturnSubmit(e) {
  e.preventDefault();
  const errorEl = document.getElementById('returnFormError');
  errorEl.style.display = 'none';

  const condition = document.getElementById('returnConditionSelect').value;
  const remarks = document.getElementById('returnRemarksInput').value.trim();

  try {
    const res = await fetch(`../api/issues/${encodeURIComponent(currentReturningIssueId)}/return`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        conditionOnReturn: condition,
        returnRemarks: remarks
      })
    });
    const json = await res.json();

    if (!json.success) {
      errorEl.textContent = json.message || 'Return processing failed.';
      errorEl.style.display = 'block';
      return;
    }

    closeModal('returnModal');
    loadIssues();
  } catch (err) {
    errorEl.textContent = 'Network error: ' + err.message;
    errorEl.style.display = 'block';
  }
}

async function viewIssue(issueId) {
  try {
    const res = await fetch(`../api/issues/${encodeURIComponent(issueId)}`);
    const json = await res.json();
    if (!json.success || !json.data) {
      alert('Failed: ' + (json.message || 'Could not load details'));
      return;
    }

    const item = json.data;
    const content = `
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; font-size: 0.88rem;">
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">TRANSACTION ID</span>
          <strong>${escapeHtml(item.issueId)}</strong>
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">STATUS</span>
          <span class="badge ${item.status === 'ISSUED' ? 'badge-issued' : 'badge-returned'}">${escapeHtml(item.status)}</span>
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ASSET</span>
          <strong>${escapeHtml(item.assetName)}</strong> (${escapeHtml(item.assetId)})
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">DEPARTMENT</span>
          ${escapeHtml(item.departmentName || item.issuedToDepartment)}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ISSUED TO</span>
          ${escapeHtml(item.issuedToUserName || 'User #' + item.issuedToUserId)}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ISSUED BY</span>
          @${escapeHtml(item.issuedBy)}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ISSUE DATE</span>
          ${escapeHtml(item.issueDate)}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">EXPECTED RETURN</span>
          ${escapeHtml(item.expectedReturnDate || 'N/A')}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">CONDITION ON ISSUE</span>
          ${escapeHtml(item.conditionOnIssue || 'GOOD')}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ACTUAL RETURN DATE</span>
          ${escapeHtml(item.actualReturnDate || 'Pending Return')}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">CONDITION ON RETURN</span>
          ${escapeHtml(item.conditionOnReturn || 'N/A')}
        </div>
        <div>
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">RECEIVED BY</span>
          ${item.returnedTo ? '@' + escapeHtml(item.returnedTo) : 'N/A'}
        </div>
      </div>
      ${item.remarks ? `
        <div style="margin-top: 1rem; padding-top: 0.75rem; border-top: 1px solid rgba(255,255,255,0.06); font-size: 0.85rem;">
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">ISSUE PURPOSE / REMARKS</span>
          ${escapeHtml(item.remarks)}
        </div>
      ` : ''}
      ${item.returnRemarks ? `
        <div style="margin-top: 0.75rem; padding-top: 0.75rem; border-top: 1px solid rgba(255,255,255,0.06); font-size: 0.85rem;">
          <span style="color: var(--text-dim); display: block; font-size: 0.75rem;">RETURN INSPECTION REMARKS</span>
          ${escapeHtml(item.returnRemarks)}
        </div>
      ` : ''}
    `;

    document.getElementById('viewModalContent').innerHTML = content;
    document.getElementById('viewModal').style.display = 'flex';
  } catch (err) {
    alert('Error fetching details: ' + err.message);
  }
}

function closeModal(id) {
  document.getElementById(id).style.display = 'none';
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}
