/**
 * CAMS - Master Data Management Client
 */

let currentTab = 'departments';
let isEditing = false;

document.addEventListener('DOMContentLoaded', () => {
  checkSession();
  setupEventListeners();
  loadData();
});

async function checkSession() {
  try {
    const res = await fetch('../../api/auth/session');
    const data = await res.json();
    if (!data.success || !data.data || data.data.role !== 'Administrator') {
      window.location.href = '../login.html';
      return;
    }
    const user = data.data;
    const userBadge = document.getElementById('sessionUser');
    if (userBadge) {
      userBadge.textContent = user.username + ' (' + user.role + ')';
    }
    if (document.getElementById('navUserDisplayName')) document.getElementById('navUserDisplayName').textContent = user.name || 'Administrator';
    if (document.getElementById('navUsername')) document.getElementById('navUsername').textContent = '@' + user.username;
    if (document.getElementById('navUserDept')) document.getElementById('navUserDept').textContent = user.department || 'IT Infrastructure';
    if (document.getElementById('navAvatarLetter')) document.getElementById('navAvatarLetter').textContent = (user.name || user.username || 'A').charAt(0).toUpperCase();
    if (document.getElementById('navRoleBadge')) {
      const b = document.getElementById('navRoleBadge');
      b.textContent = user.role;
      b.className = 'role-badge ' + (user.role === 'Administrator' ? 'admin' : (user.role === 'Faculty' ? 'faculty' : 'technical'));
    }
  } catch (err) {
    window.location.href = '../login.html';
  }
}

function setupEventListeners() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      btn.classList.add('active');
      currentTab = btn.getAttribute('data-tab');
      updateUiForTab();
      loadData();
    });
  });

  document.getElementById('addItemBtn').addEventListener('click', openAddModal);
  document.getElementById('masterForm').addEventListener('submit', handleFormSubmit);

  const logoutBtn = document.getElementById('logoutBtn');
  if (logoutBtn) {
    logoutBtn.addEventListener('click', async () => {
      await fetch('../../api/auth/logout', { method: 'POST' });
      window.location.href = '../login.html';
    });
  }
}

function updateUiForTab() {
  const addBtnLabel = document.getElementById('addBtnLabel');
  const thCode = document.getElementById('thCode');
  const thName = document.getElementById('thName');

  if (currentTab === 'departments') {
    addBtnLabel.textContent = 'Add Department';
    thCode.textContent = 'Department Code';
    thName.textContent = 'Department Name';
  } else if (currentTab === 'locations') {
    addBtnLabel.textContent = 'Add Location';
    thCode.textContent = 'Location Identifier';
    thName.textContent = 'Location Name';
  } else if (currentTab === 'categories') {
    addBtnLabel.textContent = 'Add Category';
    thCode.textContent = 'Category Code';
    thName.textContent = 'Category Name';
  }
}

async function loadData() {
  const tbody = document.getElementById('tableBody');
  tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-dim);">Loading ${currentTab}...</td></tr>`;

  try {
    const res = await fetch(`../../api/${currentTab}`);
    const json = await res.json();

    if (!json.success || !json.data) {
      tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: #f87171; padding: 2rem;">Failed to load ${currentTab}</td></tr>`;
      return;
    }

    renderTable(json.data);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: #f87171; padding: 2rem;">Error: ${err.message}</td></tr>`;
  }
}

function renderTable(items) {
  const tbody = document.getElementById('tableBody');
  if (items.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; padding: 2rem; color: var(--text-dim);">No ${currentTab} found.</td></tr>`;
    return;
  }

  tbody.innerHTML = items.map(item => {
    let id = item.departmentId || item.locationId || item.categoryId;
    let name = item.departmentName || item.locationName || item.categoryName;
    let active = item.active === 'Y';

    return `
      <tr>
        <td style="font-family: 'JetBrains Mono', monospace; font-weight: 600; color: #a5b4fc;">${escapeHtml(id)}</td>
        <td style="font-weight: 500;">${escapeHtml(name)}</td>
        <td>
          <span class="badge ${active ? 'badge-active' : 'badge-inactive'}">
            ${active ? 'ACTIVE' : 'INACTIVE'}
          </span>
        </td>
        <td style="text-align: right; white-space: nowrap;">
          <button class="action-btn" onclick="openEditModal('${escapeHtml(id)}', '${escapeHtml(name)}', '${item.active}')">✏️ Edit</button>
          ${active ? `<button class="action-btn danger" style="margin-left: 0.35rem;" onclick="deactivateItem('${escapeHtml(id)}')">🚫 Deactivate</button>` : ''}
        </td>
      </tr>
    `;
  }).join('');
}

function openAddModal() {
  isEditing = false;
  document.getElementById('modalTitle').textContent = `Add ${getSingularTitle()}`;
  document.getElementById('itemIdInput').value = '';
  document.getElementById('itemIdInput').disabled = false;
  document.getElementById('itemNameInput').value = '';
  document.getElementById('activeToggleContainer').style.display = 'none';
  document.getElementById('formError').style.display = 'none';
  document.getElementById('masterModal').style.display = 'flex';
}

function openEditModal(id, name, active) {
  isEditing = true;
  document.getElementById('modalTitle').textContent = `Edit ${getSingularTitle()}`;
  document.getElementById('itemIdInput').value = id;
  document.getElementById('itemIdInput').disabled = true;
  document.getElementById('itemNameInput').value = name;
  document.getElementById('itemActiveInput').value = active;
  document.getElementById('activeToggleContainer').style.display = 'block';
  document.getElementById('formError').style.display = 'none';
  document.getElementById('masterModal').style.display = 'flex';
}

function closeModal() {
  document.getElementById('masterModal').style.display = 'none';
}

async function handleFormSubmit(e) {
  e.preventDefault();
  const formError = document.getElementById('formError');
  formError.style.display = 'none';

  const id = document.getElementById('itemIdInput').value.trim();
  const name = document.getElementById('itemNameInput').value.trim();
  const active = document.getElementById('itemActiveInput').value;

  if (!id || !name) {
    formError.textContent = 'Identifier and Name are required.';
    formError.style.display = 'block';
    return;
  }

  const payload = {};
  if (currentTab === 'departments') {
    payload.departmentId = id;
    payload.departmentName = name;
    payload.active = active;
  } else if (currentTab === 'locations') {
    payload.locationId = id;
    payload.locationName = name;
    payload.active = active;
  } else if (currentTab === 'categories') {
    payload.categoryId = id;
    payload.categoryName = name;
    payload.active = active;
  }

  try {
    const url = isEditing
      ? `../../api/${currentTab}/${encodeURIComponent(id)}`
      : `../../api/${currentTab}`;
    const method = isEditing ? 'PUT' : 'POST';

    const res = await fetch(url, {
      method,
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    const json = await res.json();
    if (!json.success) {
      formError.textContent = json.message || 'Operation failed';
      formError.style.display = 'block';
      return;
    }

    closeModal();
    loadData();
  } catch (err) {
    formError.textContent = 'Network error: ' + err.message;
    formError.style.display = 'block';
  }
}

async function deactivateItem(id) {
  if (!confirm(`Are you sure you want to deactivate ${id}? It will no longer appear in new asset dropdowns.`)) {
    return;
  }

  try {
    const res = await fetch(`../../api/${currentTab}/${encodeURIComponent(id)}/deactivate`, {
      method: 'PUT'
    });
    const json = await res.json();
    if (!json.success) {
      alert('Failed: ' + (json.message || 'Unknown error'));
      return;
    }
    loadData();
  } catch (err) {
    alert('Error: ' + err.message);
  }
}

function getSingularTitle() {
  if (currentTab === 'departments') return 'Department';
  if (currentTab === 'locations') return 'Location';
  if (currentTab === 'categories') return 'Category';
  return 'Item';
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
}
