/**
 * CAMS Vendor Management Controller (Module 3)
 * Handles CRUD workflows, search/filtering, deactivation, and session verification.
 */

let currentUser = null;
let currentPage = 1;
let currentSize = 10;
let totalPages = 1;

document.addEventListener('DOMContentLoaded', async () => {
  await initSession();
  await loadVendors();
  setupEventListeners();
});

// 1. Session Verification
async function initSession() {
  try {
    const res = await fetch('../../api/auth/session');
    if (!res.ok) {
      window.location.href = '../login.html?redirect=/pages/admin/vendors.html';
      return;
    }
    const data = await res.json();
    currentUser = data.data;

    if (currentUser.role !== 'Administrator') {
      window.location.href = '../access-denied.html?required=Administrator&current=' + encodeURIComponent(currentUser.role);
      return;
    }

    document.getElementById('navUserDisplayName').textContent = currentUser.name;
    document.getElementById('navUsername').textContent = '@' + currentUser.username;
    document.getElementById('navAvatarLetter').textContent = currentUser.name.charAt(0).toUpperCase();

  } catch (err) {
    window.location.href = '../login.html?redirect=/pages/admin/vendors.html';
  }
}

// 2. Load Vendors with Search & Filtering
async function loadVendors() {
  const tbody = document.getElementById('vendorTableBody');
  tbody.innerHTML = `
    <tr>
      <td colspan="7" style="text-align: center; color: var(--text-dim); padding: 3rem;">
        Fetching verified vendors...
      </td>
    </tr>
  `;

  const search = document.getElementById('searchKeyword').value.trim();
  const active = document.getElementById('filterActive').value;

  const params = new URLSearchParams({
    page: currentPage,
    size: currentSize
  });

  if (search) params.append('search', search);
  if (active) params.append('active', active);

  try {
    const res = await fetch(`../../api/vendors?${params.toString()}`);
    const data = await res.json();

    if (res.ok && data.success) {
      renderTable(data.data.items);
      updatePagination(data.data);
    } else {
      showGlobalAlert('danger', data.message || 'Failed to load vendors');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Network error fetching vendors list');
  }
}

// 3. Render Table Rows
function renderTable(vendors) {
  const tbody = document.getElementById('vendorTableBody');
  if (!vendors || vendors.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--text-dim); padding: 3rem;">
          No vendors match the current search or status filter.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = '';
  vendors.forEach(v => {
    const tr = document.createElement('tr');
    const isActive = v.active === 'Y';
    const statusClass = isActive ? 'status-active' : 'status-inactive';
    const statusLabel = isActive ? 'Active' : 'Inactive';

    let actions = `<button class="action-btn" onclick="openEditModal('${escapeHtml(v.vendorId)}')">✏️ Edit</button>`;
    if (isActive) {
      actions += ` <button class="action-btn danger" onclick="openDeactivateModal('${escapeHtml(v.vendorId)}', '${escapeHtml(v.vendorName)}')">🚫 Deactivate</button>`;
    } else {
      actions += ` <span style="font-size: 0.75rem; color: var(--text-dim); padding: 0.35rem 0.5rem;">Deactivated</span>`;
    }

    tr.innerHTML = `
      <td><span style="font-family: var(--font-mono); font-size: 0.82rem; color: #a5b4fc; font-weight: 600;">${escapeHtml(v.vendorId)}</span></td>
      <td>
        <div style="font-weight: 600; color: #ffffff;">${escapeHtml(v.vendorName)}</div>
      </td>
      <td style="font-family: var(--font-mono); font-size: 0.82rem;">${escapeHtml(v.contact || '—')}</td>
      <td style="font-size: 0.82rem; color: var(--text-muted);">${escapeHtml(v.email || '—')}</td>
      <td style="font-size: 0.82rem; color: var(--text-muted); max-width: 250px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;" title="${escapeHtml(v.address || '')}">
        ${escapeHtml(v.address || '—')}
      </td>
      <td><span class="status-pill ${statusClass}">${statusLabel}</span></td>
      <td style="text-align: right; white-space: nowrap;">${actions}</td>
    `;
    tbody.appendChild(tr);
  });
}

// 4. Pagination
function updatePagination(meta) {
  currentPage = meta.page;
  totalPages = meta.totalPages || 1;

  const start = meta.totalItems === 0 ? 0 : (meta.page - 1) * meta.size + 1;
  const end = Math.min(meta.page * meta.size, meta.totalItems);
  document.getElementById('paginationInfo').textContent = `Showing ${start}-${end} of ${meta.totalItems} vendors`;
  document.getElementById('pageIndicator').textContent = `Page ${meta.page} of ${totalPages}`;

  document.getElementById('prevPageBtn').disabled = meta.page <= 1;
  document.getElementById('nextPageBtn').disabled = meta.page >= totalPages;
}

// 5. Modals Management
function openModal(id) {
  const m = document.getElementById(id);
  if (m) m.style.display = 'flex';
}

function closeModal(id) {
  const m = document.getElementById(id);
  if (m) m.style.display = 'none';
}

// Add Modal
document.getElementById('openAddModalBtn')?.addEventListener('click', () => {
  document.getElementById('addVendorForm').reset();
  hideModalAlert('addModalAlert');
  openModal('addModal');
});

document.getElementById('addVendorForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('addModalAlert');

  const vendorId = document.getElementById('addVendorId').value.trim();
  const vendorName = document.getElementById('addVendorName').value.trim();
  const contact = document.getElementById('addContact').value.trim();
  const email = document.getElementById('addEmail').value.trim();
  const address = document.getElementById('addAddress').value.trim();

  const payload = {
    vendorName,
    contact: contact || null,
    email: email || null,
    address: address || null
  };
  if (vendorId) payload.vendorId = vendorId;

  const submitBtn = document.getElementById('submitAddBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch('../../api/vendors', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('addModal');
      showGlobalAlert('success', `Vendor '${data.data.vendorName}' registered successfully!`);
      loadVendors();
    } else {
      showModalAlert('addModalAlert', 'addModalAlertMsg', data.message || 'Failed to save vendor');
    }
  } catch (err) {
    showModalAlert('addModalAlert', 'addModalAlertMsg', 'Network error registering vendor');
  } finally {
    submitBtn.disabled = false;
  }
});

// Edit Modal
async function openEditModal(vendorId) {
  try {
    const res = await fetch(`../../api/vendors/${encodeURIComponent(vendorId)}`);
    const data = await res.json();

    if (res.ok && data.success) {
      const v = data.data;
      document.getElementById('editVendorId').value = v.vendorId;
      document.getElementById('editVendorName').value = v.vendorName;
      document.getElementById('editContact').value = v.contact || '';
      document.getElementById('editEmail').value = v.email || '';
      document.getElementById('editAddress').value = v.address || '';
      hideModalAlert('editModalAlert');
      openModal('editModal');
    } else {
      showGlobalAlert('danger', data.message || 'Failed to fetch vendor details');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Network error loading vendor details');
  }
}

document.getElementById('editVendorForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('editModalAlert');

  const vendorId = document.getElementById('editVendorId').value;
  const vendorName = document.getElementById('editVendorName').value.trim();
  const contact = document.getElementById('editContact').value.trim();
  const email = document.getElementById('editEmail').value.trim();
  const address = document.getElementById('editAddress').value.trim();

  const payload = {
    vendorName,
    contact: contact || null,
    email: email || null,
    address: address || null
  };

  const submitBtn = document.getElementById('submitEditBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch(`../../api/vendors/${encodeURIComponent(vendorId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('editModal');
      showGlobalAlert('success', `Vendor '${data.data.vendorName}' updated successfully!`);
      loadVendors();
    } else {
      showModalAlert('editModalAlert', 'editModalAlertMsg', data.message || 'Failed to update vendor');
    }
  } catch (err) {
    showModalAlert('editModalAlert', 'editModalAlertMsg', 'Network error updating vendor');
  } finally {
    submitBtn.disabled = false;
  }
});

// Deactivate Modal
function openDeactivateModal(vendorId, vendorName) {
  document.getElementById('deactivateVendorId').value = vendorId;
  document.getElementById('deactivateVendorName').textContent = vendorName;
  document.getElementById('deactivateVendorIdCode').textContent = vendorId;
  hideModalAlert('deactivateModalAlert');
  openModal('deactivateModal');
}

document.getElementById('confirmDeactivateBtn')?.addEventListener('click', async () => {
  const vendorId = document.getElementById('deactivateVendorId').value;
  const btn = document.getElementById('confirmDeactivateBtn');
  btn.disabled = true;

  try {
    const res = await fetch(`../../api/vendors/${encodeURIComponent(vendorId)}/deactivate`, {
      method: 'PUT'
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('deactivateModal');
      showGlobalAlert('success', `Vendor '${vendorId}' has been deactivated.`);
      loadVendors();
    } else {
      showModalAlert('deactivateModalAlert', 'deactivateModalAlertMsg', data.message || 'Failed to deactivate vendor');
    }
  } catch (err) {
    showModalAlert('deactivateModalAlert', 'deactivateModalAlertMsg', 'Network error deactivating vendor');
  } finally {
    btn.disabled = false;
  }
});

// 6. Event Listeners
function setupEventListeners() {
  document.getElementById('applyFiltersBtn')?.addEventListener('click', () => {
    currentPage = 1;
    loadVendors();
  });

  document.getElementById('resetFiltersBtn')?.addEventListener('click', () => {
    document.getElementById('searchKeyword').value = '';
    document.getElementById('filterActive').value = 'Y';
    currentPage = 1;
    loadVendors();
  });

  let debounceTimeout;
  document.getElementById('searchKeyword')?.addEventListener('input', () => {
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(() => {
      currentPage = 1;
      loadVendors();
    }, 400);
  });

  document.getElementById('filterActive')?.addEventListener('change', () => {
    currentPage = 1;
    loadVendors();
  });

  document.getElementById('pageSizeSelect')?.addEventListener('change', (e) => {
    currentSize = parseInt(e.target.value) || 10;
    currentPage = 1;
    loadVendors();
  });

  document.getElementById('prevPageBtn')?.addEventListener('click', () => {
    if (currentPage > 1) {
      currentPage--;
      loadVendors();
    }
  });

  document.getElementById('nextPageBtn')?.addEventListener('click', () => {
    if (currentPage < totalPages) {
      currentPage++;
      loadVendors();
    }
  });

  document.getElementById('logoutBtn')?.addEventListener('click', async () => {
    try {
      await fetch('../../api/auth/logout', { method: 'POST' });
    } catch (e) {}
    window.location.href = '../login.html';
  });
}

function showGlobalAlert(type, message) {
  const box = document.getElementById('globalAlert');
  const icon = document.getElementById('alertIcon');
  const text = document.getElementById('alertText');
  box.className = 'notice-box ' + (type === 'danger' ? 'notice-danger' : type === 'success' ? 'notice-success' : 'notice-warning');
  icon.textContent = (type === 'danger' ? '⛔' : type === 'success' ? '✓' : '⚠️');
  text.textContent = message;
  box.style.display = 'flex';
  setTimeout(() => { box.style.display = 'none'; }, 6000);
}

function showModalAlert(boxId, msgId, message) {
  const box = document.getElementById(boxId);
  const msg = document.getElementById(msgId);
  if (box && msg) {
    msg.textContent = message;
    box.style.display = 'flex';
  }
}

function hideModalAlert(boxId) {
  const box = document.getElementById(boxId);
  if (box) box.style.display = 'none';
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
