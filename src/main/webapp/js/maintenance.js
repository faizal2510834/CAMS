/**
 * CAMS - Maintenance & Repair Management Client
 */

let currentUser = null;
let currentPage = 1;
const pageSize = 10;

document.addEventListener('DOMContentLoaded', () => {
  init();
});

async function init() {
  await checkSession();
  setupEventListeners();
  const todayStr = new Date().toISOString().split('T')[0];
  const schedDateInput = document.getElementById('maintScheduledDate');
  if (schedDateInput) schedDateInput.value = todayStr;
  const compDateInput = document.getElementById('updateCompletedDate');
  if (compDateInput) compDateInput.value = todayStr;
  await loadTickets();
}

async function checkSession() {
  try {
    const res = await fetch('../../api/auth/session');
    const data = await res.json();
    if (!data.success || !data.data) {
      window.location.href = '../login.html';
      return;
    }
    currentUser = data.data;

    // Faculty is forbidden per SRS
    if (currentUser.role === 'Faculty') {
      window.location.href = '../access-denied.html?required=Technical+Staff&current=Faculty';
      return;
    }

    const userBadge = document.getElementById('sessionUser');
    if (userBadge) {
      userBadge.textContent = currentUser.name + ' (' + currentUser.role + ')';
    }

    if (document.getElementById('navUserDisplayName')) document.getElementById('navUserDisplayName').textContent = currentUser.name;
    if (document.getElementById('navUsername')) document.getElementById('navUsername').textContent = '@' + currentUser.username;
    if (document.getElementById('navUserDept')) document.getElementById('navUserDept').textContent = currentUser.department;
    if (document.getElementById('navAvatarLetter')) document.getElementById('navAvatarLetter').textContent = (currentUser.name || 'T').charAt(0).toUpperCase();

    const roleBadge = document.getElementById('navRoleBadge');
    if (roleBadge) {
      roleBadge.textContent = currentUser.role;
      if (currentUser.role === 'Administrator') roleBadge.className = 'role-badge admin';
      else roleBadge.className = 'role-badge technical';
    }

    const dashNav = document.getElementById('dashNav');
    if (dashNav) {
      if (currentUser.role === 'Administrator') dashNav.href = '../admin/dashboard.html';
      else dashNav.href = 'dashboard.html';
    }

    if (currentUser.role === 'Administrator') {
      const adminLinks = document.getElementById('adminLinks');
      if (adminLinks) adminLinks.style.display = 'inline-flex';
    }
  } catch (err) {
    window.location.href = '../login.html';
  }
}

function setupEventListeners() {
  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      try {
        await fetch('../../api/auth/logout', { method: 'POST' });
      } finally {
        window.location.href = '../login.html';
      }
    });
  }

  const openSchedBtn = document.getElementById('openScheduleModalBtn');
  if (openSchedBtn) {
    openSchedBtn.addEventListener('click', () => {
      openScheduleModal();
    });
  }

  const filterBtn = document.getElementById('filterBtn');
  if (filterBtn) {
    filterBtn.addEventListener('click', () => {
      currentPage = 1;
      loadTickets();
    });
  }

  const resetBtn = document.getElementById('resetBtn');
  if (resetBtn) {
    resetBtn.addEventListener('click', () => {
      document.getElementById('filterStatus').value = 'ALL';
      document.getElementById('filterAsset').value = '';
      document.getElementById('filterTech').value = '';
      currentPage = 1;
      loadTickets();
    });
  }

  const prevBtn = document.getElementById('prevPageBtn');
  if (prevBtn) {
    prevBtn.addEventListener('click', () => {
      if (currentPage > 1) {
        currentPage--;
        loadTickets();
      }
    });
  }

  const nextBtn = document.getElementById('nextPageBtn');
  if (nextBtn) {
    nextBtn.addEventListener('click', () => {
      currentPage++;
      loadTickets();
    });
  }
}

async function loadTickets() {
  const tableBody = document.getElementById('maintenanceTableBody');
  tableBody.innerHTML = '<tr><td colspan="9" style="text-align: center; padding: 2rem; color: var(--text-dim);">Loading maintenance tickets...</td></tr>';

  const status = document.getElementById('filterStatus').value;
  const asset = document.getElementById('filterAsset').value.trim();
  const tech = document.getElementById('filterTech').value.trim();

  let query = `../../api/maintenance?page=${currentPage}&size=${pageSize}`;
  if (status && status !== 'ALL') query += `&status=${encodeURIComponent(status)}`;
  if (asset) query += `&assetId=${encodeURIComponent(asset)}`;
  if (tech) query += `&technician=${encodeURIComponent(tech)}`;

  try {
    const res = await fetch(query);
    const data = await res.json();

    if (!data.success) {
      tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; padding: 2rem; color: #f87171;">Failed to load records: ${data.message}</td></tr>`;
      return;
    }

    const { items, totalItems, totalPages, totalCost } = data.data;

    document.getElementById('statTotalItems').textContent = totalItems || 0;
    const costFormatted = Number(totalCost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    document.getElementById('statTotalCost').textContent = '₹' + costFormatted;

    document.getElementById('pageInfo').textContent = `Page ${currentPage} of ${totalPages || 1} (${totalItems || 0} items)`;
    document.getElementById('prevPageBtn').disabled = (currentPage <= 1);
    document.getElementById('nextPageBtn').disabled = (currentPage >= totalPages || totalPages === 0);

    if (!items || items.length === 0) {
      tableBody.innerHTML = '<tr><td colspan="9" style="text-align: center; padding: 2.5rem; color: var(--text-dim);">No maintenance records found.</td></tr>';
      return;
    }

    tableBody.innerHTML = items.map(m => renderTicketRow(m)).join('');
  } catch (err) {
    tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; padding: 2rem; color: #f87171;">Error connecting to server: ${err.message}</td></tr>`;
  }
}

function renderTicketRow(m) {
  let badgeClass = 'badge-scheduled';
  if (m.status === 'IN_PROGRESS') badgeClass = 'badge-in-progress';
  else if (m.status === 'COMPLETED') badgeClass = 'badge-completed';
  else if (m.status === 'REQUIRES_FURTHER_REPAIR') badgeClass = 'badge-repair';

  const costStr = Number(m.cost || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const safeDesc = escapeHtml(m.faultDescription || 'N/A');
  const safeTech = escapeHtml(m.technician || 'Unassigned');
  const safeAsset = escapeHtml(m.assetName ? `${m.assetId} (${m.assetName})` : m.assetId);

  let actionsHtml = '';
  if (m.status === 'COMPLETED') {
    actionsHtml = '<span style="font-size: 0.78rem; color: #34d399; font-weight: 600;">✓ Completed</span>';
  } else if (m.status === 'SCHEDULED') {
    actionsHtml = `
      <div style="display: flex; gap: 0.35rem; justify-content: flex-end;">
        <button class="action-btn primary" onclick="quickStartRepair('${m.maintenanceId}')">▶️ Start</button>
        <button class="action-btn" onclick="openUpdateModal('${m.maintenanceId}', '${m.status}', ${m.cost || 0}, '${escapeJs(m.technician || '')}', '${escapeJs(m.faultDescription || '')}')">✏️ Update</button>
      </div>
    `;
  } else {
    actionsHtml = `
      <div style="display: flex; gap: 0.35rem; justify-content: flex-end;">
        <button class="action-btn primary" onclick="openUpdateModal('${m.maintenanceId}', '${m.status}', ${m.cost || 0}, '${escapeJs(m.technician || '')}', '${escapeJs(m.faultDescription || '')}')">🛠️ Resolve / Update</button>
      </div>
    `;
  }

  return `
    <tr>
      <td><span style="font-family: 'JetBrains Mono', monospace; font-weight: 700; color: #f59e0b;">${m.maintenanceId}</span></td>
      <td>
        <div style="font-weight: 600;">${safeAsset}</div>
        <div style="font-size: 0.75rem; color: var(--text-dim);">${m.department || ''} &bull; ${m.location || ''}</div>
      </td>
      <td style="max-width: 240px; font-size: 0.82rem;" title="${safeDesc}">${safeDesc}</td>
      <td style="font-size: 0.82rem; color: var(--text-dim);">${m.scheduledDate || '-'}</td>
      <td style="font-size: 0.82rem; color: var(--text-dim);">${m.completedDate || '-'}</td>
      <td style="font-size: 0.82rem;">${safeTech}</td>
      <td style="font-family: 'JetBrains Mono', monospace; font-weight: 600;">₹${costStr}</td>
      <td><span class="badge ${badgeClass}">${m.status}</span></td>
      <td style="text-align: right;">${actionsHtml}</td>
    </tr>
  `;
}

function openScheduleModal() {
  document.getElementById('scheduleErrorMsg').style.display = 'none';
  document.getElementById('scheduleForm').reset();
  document.getElementById('maintScheduledDate').value = new Date().toISOString().split('T')[0];
  document.getElementById('scheduleModal').style.display = 'flex';
}

function closeScheduleModal() {
  document.getElementById('scheduleModal').style.display = 'none';
}

async function handleScheduleSubmit(e) {
  e.preventDefault();
  const errorDiv = document.getElementById('scheduleErrorMsg');
  errorDiv.style.display = 'none';

  const assetId = document.getElementById('maintAssetId').value.trim();
  const scheduledDate = document.getElementById('maintScheduledDate').value;
  const faultDescription = document.getElementById('maintFaultDesc').value.trim();
  const technician = document.getElementById('maintTech').value.trim();
  const cost = parseFloat(document.getElementById('maintCost').value) || 0.00;

  const payload = {
    assetId,
    scheduledDate,
    faultDescription,
    technician,
    cost
  };

  try {
    const res = await fetch('../../api/maintenance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (!data.success) {
      errorDiv.textContent = data.message || 'Failed to schedule maintenance';
      errorDiv.style.display = 'block';
      return;
    }

    closeScheduleModal();
    alert('✓ Maintenance ticket scheduled successfully!');
    await loadTickets();
  } catch (err) {
    errorDiv.textContent = 'Server connection error: ' + err.message;
    errorDiv.style.display = 'block';
  }
}

function openUpdateModal(ticketId, currentStatus, currentCost, currentTech, currentDesc) {
  document.getElementById('updateErrorMsg').style.display = 'none';
  document.getElementById('updateMaintId').value = ticketId;
  document.getElementById('updateStatus').value = (currentStatus === 'SCHEDULED') ? 'IN_PROGRESS' : currentStatus;
  document.getElementById('updateCost').value = currentCost || 0.00;
  document.getElementById('updateTech').value = currentTech || (currentUser ? currentUser.name : '');
  document.getElementById('updateRemarks').value = currentDesc || '';

  onUpdateStatusChange();
  document.getElementById('updateModal').style.display = 'flex';
}

function closeUpdateModal() {
  document.getElementById('updateModal').style.display = 'none';
}

function onUpdateStatusChange() {
  const status = document.getElementById('updateStatus').value;
  const compGroup = document.getElementById('completedDateGroup');
  const submitBtn = document.getElementById('updateSubmitBtn');

  if (status === 'COMPLETED') {
    compGroup.style.display = 'flex';
    document.getElementById('updateCompletedDate').value = new Date().toISOString().split('T')[0];
    submitBtn.textContent = 'Complete Repair & Release Asset';
    submitBtn.style.background = 'linear-gradient(135deg, #10b981, #059669)';
  } else {
    compGroup.style.display = 'none';
    submitBtn.textContent = 'Save Changes';
    submitBtn.style.background = 'linear-gradient(135deg, #4f46e5, #7c3aed)';
  }
}

async function quickStartRepair(ticketId) {
  if (!confirm(`Start servicing ticket ${ticketId}? This moves the status to IN_PROGRESS.`)) return;

  try {
    const res = await fetch(`../../api/maintenance/${encodeURIComponent(ticketId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        status: 'IN_PROGRESS',
        technician: currentUser ? currentUser.name : 'Technical Staff'
      })
    });
    const data = await res.json();
    if (!data.success) {
      alert('Error: ' + data.message);
      return;
    }
    await loadTickets();
  } catch (err) {
    alert('Connection error: ' + err.message);
  }
}

async function handleUpdateSubmit(e) {
  e.preventDefault();
  const errorDiv = document.getElementById('updateErrorMsg');
  errorDiv.style.display = 'none';

  const ticketId = document.getElementById('updateMaintId').value;
  const status = document.getElementById('updateStatus').value;
  const technician = document.getElementById('updateTech').value.trim();
  const cost = parseFloat(document.getElementById('updateCost').value) || 0.00;
  const faultDescription = document.getElementById('updateRemarks').value.trim();
  const completedDate = (status === 'COMPLETED') ? document.getElementById('updateCompletedDate').value : null;

  const payload = {
    status,
    technician,
    cost,
    faultDescription,
    completedDate
  };

  try {
    const res = await fetch(`../../api/maintenance/${encodeURIComponent(ticketId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (!data.success) {
      errorDiv.textContent = data.message || 'Failed to update maintenance';
      errorDiv.style.display = 'block';
      return;
    }

    closeUpdateModal();
    alert('✓ Maintenance record updated successfully!');
    await loadTickets();
  } catch (err) {
    errorDiv.textContent = 'Server connection error: ' + err.message;
    errorDiv.style.display = 'block';
  }
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/[&<>"']/g, function(m) {
    return {
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#039;'
    }[m];
  });
}

function escapeJs(str) {
  if (!str) return '';
  return str.replace(/'/g, "\\'").replace(/"/g, '&quot;');
}
