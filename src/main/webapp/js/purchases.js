/**
 * CAMS Purchase & Procurement Controller (Module 3)
 * Handles procurement creation, searchable assets/vendors, approval workflow, and transaction verification.
 */

let currentUser = null;
let currentPage = 1;
let currentSize = 10;
let totalPages = 1;
let activeVendors = [];
let allAssets = [];
let existingPurchasedAssetIds = new Set();

document.addEventListener('DOMContentLoaded', async () => {
  await initSession();
  await loadActiveVendors();
  await loadPurchases();
  setupEventListeners();
});

// 1. Session Verification
async function initSession() {
  try {
    const res = await fetch('../../api/auth/session');
    if (!res.ok) {
      window.location.href = '../login.html?redirect=/pages/admin/purchases.html';
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
    window.location.href = '../login.html?redirect=/pages/admin/purchases.html';
  }
}

// 2. Load Active Vendors for Dropdowns
async function loadActiveVendors() {
  try {
    const res = await fetch('../../api/vendors?activeOnly=true');
    const data = await res.json();
    if (res.ok && data.success) {
      activeVendors = data.data;

      // Populate filter dropdown
      const filterSelect = document.getElementById('filterVendor');
      filterSelect.innerHTML = '<option value="">All Vendors</option>';
      activeVendors.forEach(v => {
        const opt = document.createElement('option');
        opt.value = v.vendorId;
        opt.textContent = `${v.vendorName} (${v.vendorId})`;
        filterSelect.appendChild(opt);
      });

      // Populate record modal vendor dropdown
      const recordSelect = document.getElementById('recordVendorSelect');
      recordSelect.innerHTML = '<option value="">-- Select Active Vendor --</option>';
      activeVendors.forEach(v => {
        const opt = document.createElement('option');
        opt.value = v.vendorId;
        opt.textContent = `${v.vendorName} (${v.vendorId})`;
        recordSelect.appendChild(opt);
      });
    }
  } catch (err) {
    console.error('Failed to load active vendors', err);
  }
}

// 3. Load Available Assets (Exclude assets that already have a purchase record)
async function loadAvailableAssets() {
  try {
    // Fetch all non-disposed assets
    const [assetsRes, purchasesRes] = await Promise.all([
      fetch('../../api/assets?size=100'),
      fetch('../../api/purchases?size=100')
    ]);

    const assetsData = await assetsRes.json();
    const purchasesData = await purchasesRes.json();

    existingPurchasedAssetIds.clear();
    if (purchasesData.ok && purchasesData.data && purchasesData.data.items) {
      purchasesData.data.items.forEach(p => existingPurchasedAssetIds.add(p.assetId));
    }

    const select = document.getElementById('recordAssetSelect');
    select.innerHTML = '<option value="">-- Select Campus Asset --</option>';

    if (assetsData.ok && assetsData.data && assetsData.data.items) {
      allAssets = assetsData.data.items;
      allAssets.forEach(a => {
        const opt = document.createElement('option');
        opt.value = a.assetId;
        const isPurchased = existingPurchasedAssetIds.has(a.assetId);
        opt.textContent = `${a.assetId} - ${a.assetName} (${a.category}) ${isPurchased ? ' [ALREADY PURCHASED]' : ''}`;
        if (isPurchased) {
          opt.disabled = true;
          opt.style.color = '#9ca3af';
        }
        select.appendChild(opt);
      });
    }
  } catch (err) {
    console.error('Failed to load assets for procurement', err);
  }
}

// 4. Load Purchases with Status/Vendor Filtering
async function loadPurchases() {
  const tbody = document.getElementById('purchaseTableBody');
  tbody.innerHTML = `
    <tr>
      <td colspan="8" style="text-align: center; color: var(--text-dim); padding: 3rem;">
        Fetching procurement records...
      </td>
    </tr>
  `;

  const status = document.getElementById('filterStatus').value;
  const vendorId = document.getElementById('filterVendor').value;

  const params = new URLSearchParams({
    page: currentPage,
    size: currentSize
  });

  if (status) params.append('status', status);
  if (vendorId) params.append('vendorId', vendorId);

  try {
    const res = await fetch(`../../api/purchases?${params.toString()}`);
    const data = await res.json();

    if (res.ok && data.success) {
      renderTable(data.data.items);
      updatePagination(data.data);
    } else {
      showGlobalAlert('danger', data.message || 'Failed to load purchases');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Network error fetching purchases');
  }
}

// 5. Render Table Rows
function renderTable(purchases) {
  const tbody = document.getElementById('purchaseTableBody');
  if (!purchases || purchases.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--text-dim); padding: 3rem;">
          No purchases match the selected filter criteria.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = '';
  purchases.forEach(p => {
    const tr = document.createElement('tr');

    const statusClass = 
      p.status === 'APPROVED' ? 'status-approved' :
      p.status === 'REJECTED' ? 'status-rejected' : 'status-pending';

    const formattedCost = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' })
      .format(p.cost);

    let actions = `<button class="action-btn" onclick="openViewModal('${escapeHtml(p.purchaseId)}')">👁️ View</button>`;

    if (p.status === 'PENDING') {
      actions += ` <button class="action-btn success" onclick="openApproveModal('${escapeHtml(p.purchaseId)}', '${escapeHtml(p.assetId)}', '${escapeHtml(p.assetName || '')}', '${escapeHtml(p.vendorName || p.vendorId)}')">✓ Approve</button>`;
      actions += ` <button class="action-btn danger" onclick="openRejectModal('${escapeHtml(p.purchaseId)}')">✗ Reject</button>`;
    }

    tr.innerHTML = `
      <td><span style="font-family: var(--font-mono); font-size: 0.82rem; color: #a5b4fc; font-weight: 600;">${escapeHtml(p.purchaseId)}</span></td>
      <td>
        <div style="font-weight: 600; color: #ffffff;">${escapeHtml(p.assetName || p.assetId)}</div>
        <div style="font-size: 0.72rem; color: var(--text-dim); font-family: var(--font-mono);">${escapeHtml(p.assetId)}</div>
      </td>
      <td>
        <div style="font-size: 0.85rem; color: #ffffff;">${escapeHtml(p.vendorName || p.vendorId)}</div>
        <div style="font-size: 0.72rem; color: var(--text-dim); font-family: var(--font-mono);">${escapeHtml(p.vendorId)}</div>
      </td>
      <td style="font-family: var(--font-mono); font-size: 0.82rem; color: var(--text-muted);">${escapeHtml(p.invoiceNumber)}</td>
      <td style="font-size: 0.82rem;">${p.purchaseDate}</td>
      <td style="font-family: var(--font-mono); font-weight: 600; color: #34d399;">${formattedCost}</td>
      <td><span class="status-pill ${statusClass}">${p.status}</span></td>
      <td style="text-align: right; white-space: nowrap;">${actions}</td>
    `;
    tbody.appendChild(tr);
  });
}

// 6. Pagination
function updatePagination(meta) {
  currentPage = meta.page;
  totalPages = meta.totalPages || 1;

  const start = meta.totalItems === 0 ? 0 : (meta.page - 1) * meta.size + 1;
  const end = Math.min(meta.page * meta.size, meta.totalItems);
  document.getElementById('paginationInfo').textContent = `Showing ${start}-${end} of ${meta.totalItems} purchases`;
  document.getElementById('pageIndicator').textContent = `Page ${meta.page} of ${totalPages}`;

  document.getElementById('prevPageBtn').disabled = meta.page <= 1;
  document.getElementById('nextPageBtn').disabled = meta.page >= totalPages;
}

// 7. Modals Management
function openModal(id) {
  const m = document.getElementById(id);
  if (m) m.style.display = 'flex';
}

function closeModal(id) {
  const m = document.getElementById(id);
  if (m) m.style.display = 'none';
}

// Record Purchase Modal
document.getElementById('openRecordModalBtn')?.addEventListener('click', async () => {
  document.getElementById('recordPurchaseForm').reset();
  hideModalAlert('recordModalAlert');
  // Set default and max purchase date to today
  const today = new Date().toISOString().split('T')[0];
  const dateInput = document.getElementById('recordPurchaseDate');
  dateInput.value = today;
  dateInput.max = today;

  await loadAvailableAssets();
  openModal('recordModal');
});

document.getElementById('recordPurchaseForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('recordModalAlert');

  const assetId = document.getElementById('recordAssetSelect').value;
  const vendorId = document.getElementById('recordVendorSelect').value;
  const invoiceNumber = document.getElementById('recordInvoice').value.trim();
  const purchaseDate = document.getElementById('recordPurchaseDate').value;
  const cost = parseFloat(document.getElementById('recordCost').value);

  if (!assetId || !vendorId) {
    showModalAlert('recordModalAlert', 'recordModalAlertMsg', 'Please select both an asset and a vendor');
    return;
  }

  const payload = {
    assetId,
    vendorId,
    invoiceNumber,
    purchaseDate,
    cost
  };

  const submitBtn = document.getElementById('submitRecordBtn');
  submitBtn.disabled = true;

  try {
    const res = await fetch('../../api/purchases', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('recordModal');
      showGlobalAlert('success', `Purchase order recorded successfully as PENDING for asset '${assetId}'!`);
      loadPurchases();
    } else {
      showModalAlert('recordModalAlert', 'recordModalAlertMsg', data.message || 'Failed to record purchase');
    }
  } catch (err) {
    showModalAlert('recordModalAlert', 'recordModalAlertMsg', 'Network error recording purchase');
  } finally {
    submitBtn.disabled = false;
  }
});

// Approve Modal
function openApproveModal(purchaseId, assetId, assetName, vendorName) {
  document.getElementById('approvePurchaseId').value = purchaseId;
  document.getElementById('approvePurchaseIdText').textContent = purchaseId;
  document.getElementById('approveAssetNameText').textContent = `${assetName || assetId} (${assetId})`;
  document.getElementById('approveVendorText').textContent = vendorName;
  hideModalAlert('approveModalAlert');
  openModal('approveModal');
}

document.getElementById('confirmApproveBtn')?.addEventListener('click', async () => {
  const purchaseId = document.getElementById('approvePurchaseId').value;
  const btn = document.getElementById('confirmApproveBtn');
  btn.disabled = true;

  try {
    const res = await fetch(`../../api/purchases/${encodeURIComponent(purchaseId)}/approve`, {
      method: 'PUT'
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('approveModal');
      showGlobalAlert('success', `Purchase '${purchaseId}' approved and asset vendor reference atomically bound!`);
      loadPurchases();
    } else {
      showModalAlert('approveModalAlert', 'approveModalAlertMsg', data.message || 'Failed to approve purchase');
    }
  } catch (err) {
    showModalAlert('approveModalAlert', 'approveModalAlertMsg', 'Network error approving purchase');
  } finally {
    btn.disabled = false;
  }
});

// Reject Modal
function openRejectModal(purchaseId) {
  document.getElementById('rejectPurchaseId').value = purchaseId;
  document.getElementById('rejectPurchaseIdText').textContent = purchaseId;
  document.getElementById('rejectReason').value = '';
  hideModalAlert('rejectModalAlert');
  openModal('rejectModal');
}

document.getElementById('rejectPurchaseForm')?.addEventListener('submit', async (e) => {
  e.preventDefault();
  hideModalAlert('rejectModalAlert');

  const purchaseId = document.getElementById('rejectPurchaseId').value;
  const reason = document.getElementById('rejectReason').value.trim();
  const btn = document.getElementById('confirmRejectBtn');
  btn.disabled = true;

  try {
    const res = await fetch(`../../api/purchases/${encodeURIComponent(purchaseId)}/reject`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ reason })
    });
    const data = await res.json();

    if (res.ok && data.success) {
      closeModal('rejectModal');
      showGlobalAlert('warning', `Purchase '${purchaseId}' has been marked REJECTED.`);
      loadPurchases();
    } else {
      showModalAlert('rejectModalAlert', 'rejectModalAlertMsg', data.message || 'Failed to reject purchase');
    }
  } catch (err) {
    showModalAlert('rejectModalAlert', 'rejectModalAlertMsg', 'Network error rejecting purchase');
  } finally {
    btn.disabled = false;
  }
});

// View Details Modal
async function openViewModal(purchaseId) {
  try {
    const res = await fetch(`../../api/purchases/${encodeURIComponent(purchaseId)}`);
    const data = await res.json();

    if (res.ok && data.success) {
      const p = data.data;
      document.getElementById('viewModalTitle').textContent = `Order ${p.purchaseId}`;

      const list = document.getElementById('viewDetailsList');
      list.innerHTML = `
        <div class="info-item"><span class="info-key">Purchase Order ID</span><span class="info-val" style="font-family: var(--font-mono); color: #a5b4fc;">${escapeHtml(p.purchaseId)}</span></div>
        <div class="info-item"><span class="info-key">Linked Asset ID</span><span class="info-val" style="font-family: var(--font-mono);">${escapeHtml(p.assetId)}</span></div>
        <div class="info-item"><span class="info-key">Linked Asset Name</span><span class="info-val">${escapeHtml(p.assetName || 'N/A')}</span></div>
        <div class="info-item"><span class="info-key">Vendor ID</span><span class="info-val" style="font-family: var(--font-mono);">${escapeHtml(p.vendorId)}</span></div>
        <div class="info-item"><span class="info-key">Vendor Company</span><span class="info-val">${escapeHtml(p.vendorName || 'N/A')}</span></div>
        <div class="info-item"><span class="info-key">Invoice / PO Number</span><span class="info-val">${escapeHtml(p.invoiceNumber)}</span></div>
        <div class="info-item"><span class="info-key">Purchase Date</span><span class="info-val">${p.purchaseDate}</span></div>
        <div class="info-item"><span class="info-key">Total Cost</span><span class="info-val" style="color: #34d399; font-family: var(--font-mono);">₹${parseFloat(p.cost).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span></div>
        <div class="info-item"><span class="info-key">Approval Status</span><span class="info-val">${p.status}</span></div>
        ${p.approvedBy ? `
          <div class="info-item"><span class="info-key" style="color: #34d399;">Approved By</span><span class="info-val" style="color: #34d399;">@${escapeHtml(p.approvedBy)}</span></div>
          <div class="info-item"><span class="info-key" style="color: #34d399;">Approved At</span><span class="info-val" style="color: #34d399;">${p.approvedAt || 'N/A'}</span></div>
        ` : ''}
        <div class="info-item"><span class="info-key">Record Created At</span><span class="info-val">${p.createdAt || 'N/A'}</span></div>
      `;
      openModal('viewModal');
    } else {
      showGlobalAlert('danger', data.message || 'Failed to fetch purchase details');
    }
  } catch (err) {
    showGlobalAlert('danger', 'Network error loading purchase details');
  }
}

// 8. Event Listeners
function setupEventListeners() {
  document.getElementById('applyFiltersBtn')?.addEventListener('click', () => {
    currentPage = 1;
    loadPurchases();
  });

  document.getElementById('resetFiltersBtn')?.addEventListener('click', () => {
    document.getElementById('filterStatus').value = '';
    document.getElementById('filterVendor').value = '';
    currentPage = 1;
    loadPurchases();
  });

  document.getElementById('filterStatus')?.addEventListener('change', () => {
    currentPage = 1;
    loadPurchases();
  });

  document.getElementById('filterVendor')?.addEventListener('change', () => {
    currentPage = 1;
    loadPurchases();
  });

  document.getElementById('pageSizeSelect')?.addEventListener('change', (e) => {
    currentSize = parseInt(e.target.value) || 10;
    currentPage = 1;
    loadPurchases();
  });

  document.getElementById('prevPageBtn')?.addEventListener('click', () => {
    if (currentPage > 1) {
      currentPage--;
      loadPurchases();
    }
  });

  document.getElementById('nextPageBtn')?.addEventListener('click', () => {
    if (currentPage < totalPages) {
      currentPage++;
      loadPurchases();
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
