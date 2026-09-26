/**
 * CAMS Module 8: Physical Inventory Audit & Verification Controller
 */

let currentUser = null;
let currentPage = 1;
let currentSize = 15;
let totalPages = 1;
let searchTimeout = null;

document.addEventListener('DOMContentLoaded', () => {
  initAuditWorkspace();
});

async function initAuditWorkspace() {
  await loadSession();
  await loadDepartments();
  await loadAuditSummary();
  await loadAudits(1);

  // Asset ID input listener for instant lookup on Enter / Barcode scan
  const scanInput = document.getElementById('scanAssetId');
  if (scanInput) {
    scanInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        lookupAsset();
      }
    });
  }
}

async function loadSession() {
  try {
    const res = await fetch('../../api/auth/session');
    if (res.ok) {
      const data = await res.json();
      currentUser = data.data;

      document.getElementById('userDisplayName').textContent = currentUser.name || currentUser.username;
      document.getElementById('userUsername').textContent = '@' + currentUser.username;
      document.getElementById('userDept').textContent = currentUser.department || 'Operations';
      document.getElementById('avatarLetter').textContent = (currentUser.name || currentUser.username).charAt(0).toUpperCase();

      const badge = document.getElementById('roleBadge');
      if (badge) {
        badge.textContent = currentUser.role === 'Administrator' ? 'ADMIN' : 'TECH';
        badge.className = 'role-badge ' + (currentUser.role === 'Administrator' ? 'admin' : 'technical');
      }
    } else {
      window.location.href = '../login.html?redirect=/pages/technical/audit.html';
    }
  } catch (err) {
    console.error('Session verification failed', err);
  }
}

async function loadDepartments() {
  try {
    const res = await fetch('../../api/assets/options');
    if (res.ok) {
      const data = await res.json();
      const select = document.getElementById('filterDepartment');
      if (select && data.data && data.data.departments) {
        data.data.departments.forEach(dept => {
          const opt = document.createElement('option');
          opt.value = dept;
          opt.textContent = dept;
          select.appendChild(opt);
        });
      }
    }
  } catch (err) {
    console.error('Failed to load departments', err);
  }
}

function setStatus(status) {
  document.getElementById('selectedStatus').value = status;

  const btnVer = document.getElementById('btnStatusVerified');
  const btnMis = document.getElementById('btnStatusMislocated');
  const btnMiss = document.getElementById('btnStatusMissing');

  btnVer.className = 'status-select-btn' + (status === 'VERIFIED' ? ' active verified' : '');
  btnMis.className = 'status-select-btn' + (status === 'MISLOCATED' ? ' active mislocated' : '');
  btnMiss.className = 'status-select-btn' + (status === 'MISSING' ? ' active missing' : '');
}

async function lookupAsset() {
  const assetIdInput = document.getElementById('scanAssetId');
  const assetId = assetIdInput ? assetIdInput.value.trim() : '';
  const previewBox = document.getElementById('assetPreviewBox');
  hideFormAlert();

  if (!assetId) {
    showFormAlert('Please enter or scan an Asset Tag first.');
    return;
  }

  try {
    const res = await fetch(`../../api/assets/${encodeURIComponent(assetId)}`);
    const json = await res.json();

    if (res.ok && json.success && json.data) {
      const asset = json.data;
      document.getElementById('previewAssetName').textContent = asset.assetName;
      document.getElementById('previewCategory').textContent = asset.category;
      document.getElementById('previewDept').textContent = asset.department;
      document.getElementById('previewLocation').textContent = asset.location || 'Not Specified';

      const badge = document.getElementById('previewStatusBadge');
      badge.textContent = asset.status;
      badge.className = 'badge ' + (
        asset.status === 'AVAILABLE' ? 'status-available' :
        asset.status === 'ISSUED' ? 'status-issued' :
        asset.status === 'UNDER_MAINTENANCE' ? 'status-maintenance' : 'status-disposed'
      );

      previewBox.style.display = 'block';
    } else {
      previewBox.style.display = 'none';
      showFormAlert(json.message || `Asset '${assetId}' not found in registry.`);
    }
  } catch (err) {
    previewBox.style.display = 'none';
    showFormAlert('Failed to connect to asset registry: ' + err.message);
  }
}

async function submitAudit(event) {
  event.preventDefault();
  hideFormAlert();

  const assetId = document.getElementById('scanAssetId').value.trim();
  const status = document.getElementById('selectedStatus').value;
  const remarks = document.getElementById('auditRemarks').value.trim();
  const submitBtn = document.getElementById('btnSubmitAudit');

  if (!assetId) {
    showFormAlert('Asset Tag is required.');
    return;
  }

  submitBtn.disabled = true;
  submitBtn.textContent = 'Saving...';

  try {
    const res = await fetch('../../api/audits', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assetId, status, remarks })
    });

    const json = await res.json();

    if (res.ok && json.success) {
      resetForm();
      await loadAuditSummary();
      await loadAudits(1);
      // Brief success notification
      const alertBox = document.getElementById('formAlertBox');
      alertBox.className = 'notice-box notice-success';
      alertBox.innerHTML = `<span>✓</span><span>Audit logged: <strong>${escapeHtml(json.data.auditId)}</strong> (${json.data.status}) for ${escapeHtml(assetId)}</span>`;
      alertBox.style.display = 'flex';
      setTimeout(() => { alertBox.style.display = 'none'; }, 4000);
    } else {
      showFormAlert(json.message || 'Failed to record audit verification.');
    }
  } catch (err) {
    showFormAlert('Error recording audit verification: ' + err.message);
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = '✓ Record Audit Verification';
  }
}

function resetForm() {
  document.getElementById('auditForm').reset();
  document.getElementById('assetPreviewBox').style.display = 'none';
  setStatus('VERIFIED');
  hideFormAlert();
  document.getElementById('scanAssetId').focus();
}

async function loadAuditSummary() {
  try {
    const res = await fetch('../../api/audits/summary');
    if (res.ok) {
      const json = await res.json();
      if (json.success && json.data) {
        const d = json.data;
        document.getElementById('statTotalAudits').textContent = d.totalAudits || 0;
        document.getElementById('statVerified').textContent = d.verifiedCount || 0;
        document.getElementById('statMislocated').textContent = d.mislocatedCount || 0;
        document.getElementById('statMissing').textContent = d.missingCount || 0;
      }
    }
  } catch (err) {
    console.error('Failed to load audit summary', err);
  }
}

async function loadAudits(page = 1) {
  currentPage = page;
  const tbody = document.getElementById('auditTableBody');
  tbody.innerHTML = `
    <tr>
      <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 2rem;">
        <span class="spinner" style="display: inline-block; margin-right: 0.5rem;"></span> Refreshing audit log...
      </td>
    </tr>
  `;

  const assetKw = document.getElementById('filterAsset').value.trim();
  const status = document.getElementById('filterStatus').value;
  const dept = document.getElementById('filterDepartment').value;

  const params = new URLSearchParams();
  params.append('page', currentPage);
  params.append('size', currentSize);
  if (assetKw) params.append('assetId', assetKw);
  if (status) params.append('status', status);
  if (dept) params.append('department', dept);

  try {
    const res = await fetch(`../../api/audits?${params.toString()}`);
    const json = await res.json();

    if (res.ok && json.success && json.data) {
      renderAuditTable(json.data);
    } else {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">${escapeHtml(json.message || 'Error loading audits')}</td></tr>`;
    }
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">Network error loading audits: ${escapeHtml(err.message)}</td></tr>`;
  }
}

function renderAuditTable(pagedResult) {
  const tbody = document.getElementById('auditTableBody');
  const items = pagedResult.items || [];
  totalPages = pagedResult.totalPages || 1;

  const start = pagedResult.totalItems === 0 ? 0 : ((pagedResult.page - 1) * pagedResult.size) + 1;
  const end = Math.min(pagedResult.page * pagedResult.size, pagedResult.totalItems);
  document.getElementById('paginationSummary').textContent = `Showing ${start}-${end} of ${pagedResult.totalItems} logs`;
  document.getElementById('tableCountSummary').textContent = `${pagedResult.totalItems} Total Records`;
  document.getElementById('currentPageBadge').textContent = `Page ${pagedResult.page} of ${Math.max(1, totalPages)}`;

  document.getElementById('prevPageBtn').disabled = (pagedResult.page <= 1);
  document.getElementById('nextPageBtn').disabled = (pagedResult.page >= totalPages);

  if (items.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--text-dim); padding: 3rem;">
          No audit verifications recorded matching criteria.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = items.map(audit => {
    const statusClass = 
      audit.status === 'VERIFIED' ? 'status-verified' :
      audit.status === 'MISSING' ? 'status-missing' : 'status-mislocated';

    return `
      <tr>
        <td><span style="font-family: var(--font-mono); font-size: 0.82rem; color: #a5b4fc; font-weight: 600;">${escapeHtml(audit.auditId)}</span></td>
        <td><span style="font-family: var(--font-mono); font-weight: 700; color: var(--text-main);">${escapeHtml(audit.assetId)}</span></td>
        <td>
          <div style="font-weight: 600;">${escapeHtml(audit.assetName || '-')}</div>
          <div style="font-size: 0.72rem; color: var(--text-dim);">${escapeHtml(audit.category || '-')}</div>
        </td>
        <td>${escapeHtml(audit.department || '-')}</td>
        <td style="font-size: 0.8rem; color: var(--text-muted);">${formatDate(audit.auditDate)}</td>
        <td>
          <span style="font-size: 0.82rem; color: #a5b4fc; font-family: var(--font-mono);">@${escapeHtml(audit.verifiedBy || '-')}</span>
        </td>
        <td>
          <span class="badge ${statusClass}">${escapeHtml(audit.status)}</span>
        </td>
        <td style="font-size: 0.82rem; color: var(--text-muted); max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(audit.remarks || '')}">
          ${escapeHtml(audit.remarks || '-')}
        </td>
      </tr>
    `;
  }).join('');
}

function prevPage() {
  if (currentPage > 1) {
    loadAudits(currentPage - 1);
  }
}

function nextPage() {
  if (currentPage < totalPages) {
    loadAudits(currentPage + 1);
  }
}

function debounceSearch() {
  clearTimeout(searchTimeout);
  searchTimeout = setTimeout(() => {
    loadAudits(1);
  }, 350);
}

function resetFilters() {
  document.getElementById('filterAsset').value = '';
  document.getElementById('filterStatus').value = '';
  document.getElementById('filterDepartment').value = '';
  loadAudits(1);
}

function showFormAlert(message) {
  const box = document.getElementById('formAlertBox');
  const msg = document.getElementById('formAlertMsg');
  box.className = 'notice-box notice-danger';
  msg.textContent = message;
  box.style.display = 'flex';
}

function hideFormAlert() {
  const box = document.getElementById('formAlertBox');
  box.style.display = 'none';
}

function formatDate(dateStr) {
  if (!dateStr) return '-';
  try {
    const d = new Date(dateStr);
    return d.toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  } catch (e) {
    return dateStr;
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

async function logout() {
  try {
    await fetch('../../api/auth/logout', { method: 'POST' });
  } catch (e) {}
  window.location.href = '../login.html';
}
