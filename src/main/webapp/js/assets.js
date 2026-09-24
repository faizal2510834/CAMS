/**
 * CAMS Asset Management Controller (Module 2)
 * Handles session role checking, dynamic dropdown population,
 * paginated search/filter, and Add/Edit/Retire modal workflows.
 */

let currentUser = null;
let currentOptions = null;
let currentPage = 1;
let currentSize = 10;
let totalPages = 1;

document.addEventListener('DOMContentLoaded', async () => {
  await initSession();
  await loadOptions();
  await loadAssets();

  setupEventListeners();
});

// 1. Session & Role Verification
async function initSession() {
  try {
    const res = await fetch('../api/auth/session');
    if (!res.ok) {
      window.location.href = 'login.html?redirect=/pages/assets.html';
      return;
    }
    const data = await res.json();
    currentUser = data.data;

    // Populate Top Nav
    document.getElementById('navUserDisplayName').textContent = currentUser.name;
    document.getElementById('navUsername').textContent = '@' + currentUser.username;
    document.getElementById('navUserDept').textContent = currentUser.department;
    document.getElementById('navAvatarLetter').textContent = currentUser.name.charAt(0).toUpperCase();

    const roleBadge = document.getElementById('navRoleBadge');
    roleBadge.textContent = currentUser.role;
    if (currentUser.role === 'Administrator') roleBadge.className = 'role-badge admin';
    else if (currentUser.role === 'Faculty') roleBadge.className = 'role-badge faculty';
    else if (currentUser.role === 'Technical Staff') roleBadge.className = 'role-badge technical';

    // Show Admin actions only for Administrator
    if (currentUser.role === 'Administrator') {
      document.getElementById('adminActionSection').style.display = 'block';
    }

  } catch (err) {
    window.location.href = 'login.html?redirect=/pages/assets.html';
  }
}

// 2. Fetch Master Options (Departments, Categories, Locations, Statuses)
async function loadOptions() {
  try {
    const res = await fetch('../api/assets/options');
    if (res.ok) {
      const data = await res.json();
      currentOptions = data.data;

      // Populate filter dropdowns
      populateSelect('filterDepartment', currentOptions.departments, 'All Departments');
      populateSelect('filterCategory', currentOptions.categories, 'All Categories');
      populateSelect('filterStatus', currentOptions.statuses, 'All Statuses');

      // Populate Add Modal dropdowns
      populateSelect('addCategory', currentOptions.categories, '-- Select Category --');
      populateSelect('addDepartment', currentOptions.departments, '-- Select Department --');
      populateSelect('addLocation', currentOptions.locations, '-- Select Location --');

      // Populate Edit Modal dropdowns
      populateSelect('editCategory', currentOptions.categories, '-- Select Category --');
      populateSelect('editDepartment', currentOptions.departments, '-- Select Department --');
      populateSelect('editLocation', currentOptions.locations, '-- Select Location --');
    }
  } catch (err) {
    console.error('Failed to load asset options', err);
  }
}

function populateSelect(selectId, items, placeholder) {
  const select = document.getElementById(selectId);
  if (!select) return;
  select.innerHTML = placeholder ? `<option value="">${placeholder}</option>` : '';
  items.forEach(item => {
    const opt = document.createElement('option');
    opt.value = item;
    opt.textContent = item;
    select.appendChild(opt);
  });
}

// 3. Load & Render Assets Table
async function loadAssets() {
  const tbody = document.getElementById('assetsTableBody');
  tbody.innerHTML = `
    <tr>
      <td colspan="8" style="text-align: center; color: var(--text-muted); padding: 2.5rem;">
        <span class="spinner" style="display: inline-block; margin-right: 0.5rem;"></span> Loading assets from registry...
      </td>
    </tr>
  `;

  const keyword = document.getElementById('searchKeyword').value.trim();
  const department = document.getElementById('filterDepartment').value;
  const category = document.getElementById('filterCategory').value;
  const status = document.getElementById('filterStatus').value;
  const includeDisposed = document.getElementById('includeDisposedCheck').checked;

  const params = new URLSearchParams();
  params.append('page', currentPage);
  params.append('size', currentSize);
  if (keyword) params.append('keyword', keyword);
  if (department) params.append('department', department);
  if (category) params.append('category', category);
  if (status) params.append('status', status);
  if (includeDisposed) params.append('includeDisposed', 'true');

  try {
    const res = await fetch(`../api/assets?${params.toString()}`);
    const data = await res.json();

    if (res.ok && data.success) {
      renderTable(data.data);
    } else {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">${data.message || 'Failed to retrieve assets'}</td></tr>`;
    }
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: #f87171; padding: 2rem;">Error connecting to server</td></tr>`;
  }
}

function renderTable(pagedResult) {
  const tbody = document.getElementById('assetsTableBody');
  const items = pagedResult.items || [];
  totalPages = pagedResult.totalPages || 1;

  // Update pagination summary
  const start = pagedResult.totalItems === 0 ? 0 : ((pagedResult.page - 1) * pagedResult.size) + 1;
  const end = Math.min(pagedResult.page * pagedResult.size, pagedResult.totalItems);
  document.getElementById('paginationSummary').textContent = `Showing ${start}-${end} of ${pagedResult.totalItems} assets`;
  document.getElementById('currentPageBadge').textContent = `Page ${pagedResult.page} of ${Math.max(1, totalPages)}`;

  document.getElementById('prevPageBtn').disabled = (pagedResult.page <= 1);
  document.getElementById('nextPageBtn').disabled = (pagedResult.page >= totalPages);

  if (items.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--text-dim); padding: 3rem;">
          No assets match the current filter criteria.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = '';
  items.forEach(asset => {
    const tr = document.createElement('tr');

    const statusClass = 
      asset.status === 'AVAILABLE' ? 'status-available' :
      asset.status === 'ISSUED' ? 'status-issued' :
      asset.status === 'UNDER_MAINTENANCE' ? 'status-maintenance' : 'status-disposed';

    const formattedCost = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })
      .format(asset.purchaseCost);

    // Build Actions based on Role & Status
    let actionsHtml = `<button class="action-btn" onclick="openViewModal('${escapeHtml(asset.assetId)}')">👁️ View</button>`;

    if (currentUser && currentUser.role === 'Administrator') {
      const isDisposed = asset.status === 'DISPOSED';
      const isIssued = asset.status === 'ISSUED';

      if (!isDisposed) {
        actionsHtml += ` <button class="action-btn" onclick="openEditModal('${escapeHtml(asset.assetId)}')">✏️ Edit</button>`;
      }

      if (!isDisposed && !isIssued) {
        actionsHtml += ` <button class="action-btn danger" onclick="openRetireModal('${escapeHtml(asset.assetId)}')">🗑️ Retire</button>`;
      } else if (isIssued) {
        actionsHtml += ` <button class="action-btn" disabled title="Issued assets cannot be retired until returned" style="opacity: 0.4;">🚫 Issued</button>`;
      }
    }

    tr.innerHTML = `
      <td><span style="font-family: var(--font-mono); font-size: 0.82rem; color: #a5b4fc; font-weight: 600;">${escapeHtml(asset.assetId)}</span></td>
      <td>
        <div style="font-weight: 600;">${escapeHtml(asset.assetName)}</div>
        <div style="font-size: 0.72rem; color: var(--text-dim);">${escapeHtml(asset.category)}</div>
      </td>
      <td>${escapeHtml(asset.department)}</td>
      <td>${escapeHtml(asset.location)}</td>
      <td style="font-family: var(--font-mono);">${formattedCost}</td>
      <td><span class="status-pill ${statusClass}">${asset.status}</span></td>
      <td style="font-size: 0.8rem; color: var(--text-muted);">${asset.warrantyExpiry || '—'}</td>
      <td style="text-align: right; white-space: nowrap;">${actionsHtml}</td>
    `;
    tbody.appendChild(tr);
  });
}

// 4. Modal Handlers
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
  document.getElementById('addAssetForm').reset();
  hideModalAlert('addModalAlert');
  // Default purchase date to today
  document.getElementById('addPurchaseDate').value = new Date().toISOString().split('T')[0];
  openModal('addModal');
});

document.getElementById('addAssetForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('addModalAlert');

  const payload = {
    assetId: document.getElementById('addAssetId').value.trim().toUpperCase(),
    assetName: document.getElementById('addAssetName').value.trim(),
    category: document.getElementById('addCategory').value,
    department: document.getElementById('addDepartment').value,
    location: document.getElementById('addLocation').value,
    purchaseDate: document.getElementById('addPurchaseDate').value,
    purchaseCost: parseFloat(document.getElementById('addPurchaseCost').value),
    warrantyExpiry: document.getElementById('addWarrantyExpiry').value || null,
    vendorId: document.getElementById('addVendorId').value.trim() || null
  };

  const submitBtn = document.getElementById('submitAddBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch('../api/assets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('addModal');
      showGlobalAlert('success', `Asset '${data.data.assetId}' registered successfully!`);
      loadAssets();
    } else {
      showModalAlert('addModalAlert', 'addModalAlertMsg', data.message || 'Validation failed');
    }
  } catch (err) {
    showModalAlert('addModalAlert', 'addModalAlertMsg', 'Network error registering asset');
  } finally {
    submitBtn.disabled = false;
  }
});

// Edit Modal
async function openEditModal(assetId) {
  hideModalAlert('editModalAlert');
  try {
    const res = await fetch(`../api/assets/${encodeURIComponent(assetId)}`);
    const data = await res.json();
    if (res.ok && data.success) {
      const a = data.data;
      document.getElementById('editAssetId').value = a.assetId;
      document.getElementById('editStatus').value = a.status;
      document.getElementById('editAssetName').value = a.assetName;
      document.getElementById('editCategory').value = a.category;
      document.getElementById('editDepartment').value = a.department;
      document.getElementById('editLocation').value = a.location;
      document.getElementById('editPurchaseCost').value = a.purchaseCost;
      document.getElementById('editPurchaseDate').value = a.purchaseDate;
      document.getElementById('editWarrantyExpiry').value = a.warrantyExpiry || '';
      document.getElementById('editVendorId').value = a.vendorId || '';

      openModal('editModal');
    } else {
      showGlobalAlert('danger', data.message || 'Could not load asset details');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Error connecting to server');
  }
}

document.getElementById('editAssetForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('editModalAlert');

  const assetId = document.getElementById('editAssetId').value;
  const payload = {
    assetName: document.getElementById('editAssetName').value.trim(),
    category: document.getElementById('editCategory').value,
    department: document.getElementById('editDepartment').value,
    location: document.getElementById('editLocation').value,
    purchaseDate: document.getElementById('editPurchaseDate').value,
    purchaseCost: parseFloat(document.getElementById('editPurchaseCost').value),
    warrantyExpiry: document.getElementById('editWarrantyExpiry').value || null,
    vendorId: document.getElementById('editVendorId').value.trim() || null
  };

  const submitBtn = document.getElementById('submitEditBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch(`../api/assets/${encodeURIComponent(assetId)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('editModal');
      showGlobalAlert('success', `Asset '${assetId}' updated successfully!`);
      loadAssets();
    } else {
      showModalAlert('editModalAlert', 'editModalAlertMsg', data.message || 'Update failed');
    }
  } catch (err) {
    showModalAlert('editModalAlert', 'editModalAlertMsg', 'Network error updating asset');
  } finally {
    submitBtn.disabled = false;
  }
});

// Retire Modal
function openRetireModal(assetId) {
  document.getElementById('retireAssetId').value = assetId;
  document.getElementById('retireReason').value = '';
  hideModalAlert('retireModalAlert');
  openModal('retireModal');
}

document.getElementById('retireAssetForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('retireModalAlert');

  const assetId = document.getElementById('retireAssetId').value;
  const reason = document.getElementById('retireReason').value.trim();

  const submitBtn = document.getElementById('submitRetireBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch(`../api/assets/${encodeURIComponent(assetId)}/retire`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason })
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('retireModal');
      showGlobalAlert('success', `Asset '${assetId}' retired (soft-deleted) successfully!`);
      loadAssets();
    } else {
      showModalAlert('retireModalAlert', 'retireModalAlertMsg', data.message || 'Retire failed');
    }
  } catch (err) {
    showModalAlert('retireModalAlert', 'retireModalAlertMsg', 'Network error retiring asset');
  } finally {
    submitBtn.disabled = false;
  }
});

// View Details Modal
async function openViewModal(assetId) {
  try {
    const res = await fetch(`../api/assets/${encodeURIComponent(assetId)}`);
    const data = await res.json();
    if (res.ok && data.success) {
      const a = data.data;
      document.getElementById('viewModalTitle').textContent = `${a.assetId} - ${a.assetName}`;

      const list = document.getElementById('viewDetailsList');
      list.innerHTML = `
        <div class="info-item"><span class="info-key">Asset ID</span><span class="info-val">${escapeHtml(a.assetId)}</span></div>
        <div class="info-item"><span class="info-key">Item Name</span><span class="info-val">${escapeHtml(a.assetName)}</span></div>
        <div class="info-item"><span class="info-key">Category</span><span class="info-val">${escapeHtml(a.category)}</span></div>
        <div class="info-item"><span class="info-key">Department</span><span class="info-val">${escapeHtml(a.department)}</span></div>
        <div class="info-item"><span class="info-key">Physical Location</span><span class="info-val">${escapeHtml(a.location)}</span></div>
        <div class="info-item"><span class="info-key">Purchase Cost</span><span class="info-val">₹${parseFloat(a.purchaseCost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></div>
        <div class="info-item"><span class="info-key">Purchase Date</span><span class="info-val">${a.purchaseDate}</span></div>
        <div class="info-item"><span class="info-key">Warranty Expiry</span><span class="info-val">${a.warrantyExpiry || 'None specified'}</span></div>
        <div class="info-item"><span class="info-key">Vendor Reference</span><span class="info-val">${escapeHtml(a.vendorId || 'N/A')}</span></div>
        <div class="info-item"><span class="info-key">Operational Status</span><span class="info-val">${a.status}</span></div>
        ${a.status === 'DISPOSED' ? `
          <div class="info-item"><span class="info-key" style="color: #f87171;">Disposed Date</span><span class="info-val" style="color: #f87171;">${a.disposedDate || 'N/A'}</span></div>
          <div class="info-item"><span class="info-key" style="color: #f87171;">Disposal Reason</span><span class="info-val" style="color: #f87171;">${escapeHtml(a.disposalReason || 'N/A')}</span></div>
        ` : ''}
        <div class="info-item"><span class="info-key">Registered At</span><span class="info-val">${a.createdAt || 'N/A'}</span></div>
        <div class="info-item"><span class="info-key">Last Updated</span><span class="info-val">${a.updatedAt || 'N/A'}</span></div>
      `;
      openModal('viewModal');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Error loading asset specifications');
  }
}

// Event Listeners for Filters & Navigation
function setupEventListeners() {
  document.getElementById('applyFiltersBtn')?.addEventListener('click', () => {
    currentPage = 1;
    loadAssets();
  });

  document.getElementById('resetFiltersBtn')?.addEventListener('click', () => {
    document.getElementById('searchKeyword').value = '';
    document.getElementById('filterDepartment').value = '';
    document.getElementById('filterCategory').value = '';
    document.getElementById('filterStatus').value = '';
    document.getElementById('includeDisposedCheck').checked = false;
    currentPage = 1;
    loadAssets();
  });

  // Debounced search on typing
  let debounceTimeout;
  document.getElementById('searchKeyword')?.addEventListener('input', () => {
    clearTimeout(debounceTimeout);
    debounceTimeout = setTimeout(() => {
      currentPage = 1;
      loadAssets();
    }, 400);
  });

  document.getElementById('includeDisposedCheck')?.addEventListener('change', () => {
    currentPage = 1;
    loadAssets();
  });

  document.getElementById('pageSizeSelect')?.addEventListener('change', (e) => {
    currentSize = parseInt(e.target.value) || 10;
    currentPage = 1;
    loadAssets();
  });

  document.getElementById('prevPageBtn')?.addEventListener('click', () => {
    if (currentPage > 1) {
      currentPage--;
      loadAssets();
    }
  });

  document.getElementById('nextPageBtn')?.addEventListener('click', () => {
    if (currentPage < totalPages) {
      currentPage++;
      loadAssets();
    }
  });

  document.getElementById('backToDashBtn')?.addEventListener('click', () => {
    if (!currentUser) { window.location.href = 'login.html'; return; }
    if (currentUser.role === 'Administrator') window.location.href = 'admin/dashboard.html';
    else if (currentUser.role === 'Faculty') window.location.href = 'faculty/dashboard.html';
    else if (currentUser.role === 'Technical Staff') window.location.href = 'technical/dashboard.html';
    else window.location.href = 'login.html';
  });

  document.getElementById('logoutBtn')?.addEventListener('click', async () => {
    try {
      await fetch('../api/auth/logout', { method: 'POST' });
    } catch (e) {}
    window.location.href = 'login.html';
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
