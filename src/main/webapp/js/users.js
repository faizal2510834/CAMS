/**
 * CAMS Module 9: User Management Client Controller
 */
let currentAdmin = null;
let departmentsList = [];

document.addEventListener('DOMContentLoaded', async () => {
  await checkSession();
  await loadDepartments();
  await loadUsers();
  setupEventListeners();
});

async function checkSession() {
  try {
    const res = await fetch('../../api/auth/session');
    const json = await res.json();
    if (!json.success || !json.data) {
      window.location.href = '../login.html';
      return;
    }
    const session = json.data;
    if (session.role !== 'Administrator') {
      window.location.href = '../access-denied.html?required=Administrator&current=' + encodeURIComponent(session.role);
      return;
    }
    currentAdmin = session;

    // Populate Navigation Profile
    document.getElementById('navUserDisplayName').textContent = session.name || 'Administrator';
    document.getElementById('navUsername').textContent = '@' + (session.username || 'admin');
    document.getElementById('navUserDept').textContent = session.department || 'IT Infrastructure';
    document.getElementById('navAvatarLetter').textContent = (session.name || 'A').charAt(0).toUpperCase();

  } catch (err) {
    console.error('Session verification error:', err);
    window.location.href = '../login.html';
  }
}

async function loadDepartments() {
  try {
    const res = await fetch('../../api/departments');
    const json = await res.json();
    if (json.success && json.data) {
      departmentsList = json.data;
      populateDepartmentDropdowns();
    }
  } catch (e) {
    console.warn('Could not load departments dynamically, falling back to asset options', e);
    try {
      const optRes = await fetch('../../api/assets/options');
      const optJson = await optRes.json();
      if (optJson.success && optJson.data && optJson.data.departments) {
        departmentsList = optJson.data.departments.map(d => ({ departmentName: d }));
        populateDepartmentDropdowns();
      }
    } catch (ignore) {}
  }
}

function populateDepartmentDropdowns() {
  const deptFilter = document.getElementById('deptFilter');
  const createDept = document.getElementById('createDepartment');
  const editDept = document.getElementById('editDepartment');

  // Keep first option
  deptFilter.innerHTML = '<option value="ALL">All Departments</option>';
  createDept.innerHTML = '<option value="">Select Department...</option>';
  editDept.innerHTML = '<option value="">Select Department...</option>';

  departmentsList.forEach(d => {
    const name = typeof d === 'string' ? d : (d.departmentName || d.departmentId);
    if (!name) return;

    const opt1 = new Option(name, name);
    const opt2 = new Option(name, name);
    const opt3 = new Option(name, name);

    deptFilter.appendChild(opt1);
    createDept.appendChild(opt2);
    editDept.appendChild(opt3);
  });
}

async function loadUsers() {
  const search = document.getElementById('searchInput').value.trim();
  const role = document.getElementById('roleFilter').value;
  const department = document.getElementById('deptFilter').value;
  const active = document.getElementById('statusFilter').value;

  const params = new URLSearchParams();
  if (search) params.append('search', search);
  if (role && role !== 'ALL') params.append('role', role);
  if (department && department !== 'ALL') params.append('department', department);
  if (active && active !== 'ALL') params.append('active', active);

  try {
    const res = await fetch('../../api/users?' + params.toString());
    const json = await res.json();

    if (!json.success || !json.data) {
      document.getElementById('usersTableBody').innerHTML = `
        <tr><td colspan="8" style="text-align: center; color: var(--danger); padding: 2rem;">
          Failed to load users: ${json.message || 'Unknown error'}
        </td></tr>`;
      return;
    }

    const users = json.data.users || [];
    renderUsersTable(users);
    updateStats(users);

  } catch (err) {
    console.error('Error fetching users:', err);
    document.getElementById('usersTableBody').innerHTML = `
      <tr><td colspan="8" style="text-align: center; color: var(--danger); padding: 2rem;">
        Network error while fetching user accounts.
      </td></tr>`;
  }
}

function updateStats(users) {
  // If viewing filtered list, query all users count once or compute from visible
  const total = users.length;
  const activeCount = users.filter(u => u.active === 'Y').length;
  const facultyCount = users.filter(u => u.role === 'Faculty').length;
  const techCount = users.filter(u => u.role === 'Technical Staff').length;

  document.getElementById('statTotalUsers').textContent = total;
  document.getElementById('statActiveUsers').textContent = activeCount;
  document.getElementById('statFacultyUsers').textContent = facultyCount;
  document.getElementById('statTechUsers').textContent = techCount;
}

function renderUsersTable(users) {
  const tbody = document.getElementById('usersTableBody');

  if (!users || users.length === 0) {
    tbody.innerHTML = `
      <tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 2rem;">
        No user accounts match the selected filters.
      </td></tr>`;
    return;
  }

  tbody.innerHTML = users.map(u => {
    const isSelf = currentAdmin && currentAdmin.userId === u.userId;
    const isActive = u.active === 'Y';

    let roleClass = 'role-faculty';
    if (u.role === 'Administrator') roleClass = 'role-admin';
    if (u.role === 'Technical Staff') roleClass = 'role-tech';

    const statusBadge = isActive
      ? `<span class="status-badge status-active">● Active</span>`
      : `<span class="status-badge status-inactive">● Deactivated</span>`;

    const createdStr = u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '-';

    const deactivateBtn = isSelf
      ? `<button class="action-btn" disabled title="Administrators cannot deactivate their own account">🚫 Deactivate</button>`
      : (isActive
          ? `<button class="action-btn btn-danger" onclick="confirmDeactivate(${u.userId}, '${escapeHtml(u.username)}')">🚫 Deactivate</button>`
          : `<button class="action-btn btn-success" onclick="reactivateUser(${u.userId}, '${escapeHtml(u.username)}')">✅ Reactivate</button>`
        );

    return `
      <tr style="${!isActive ? 'opacity: 0.65;' : ''}">
        <td style="font-family: var(--font-mono); color: var(--text-dim);">${u.userId}</td>
        <td>
          <strong style="color: var(--text-main);">${escapeHtml(u.name)}</strong>
          ${isSelf ? '<span style="font-size: 0.7rem; margin-left: 0.35rem; color: var(--accent); font-weight: 600;">(YOU)</span>' : ''}
        </td>
        <td style="font-family: var(--font-mono); color: var(--accent-cyan);">@${escapeHtml(u.username)}</td>
        <td><span class="role-tag ${roleClass}">${escapeHtml(u.role)}</span></td>
        <td>${escapeHtml(u.department)}</td>
        <td>${statusBadge}</td>
        <td style="font-size: 0.8rem; color: var(--text-muted);">${createdStr}</td>
        <td style="text-align: right;">
          <div style="display: inline-flex; gap: 0.4rem;">
            <button class="action-btn" onclick="openEditModal(${u.userId}, '${escapeHtml(u.name)}', '${escapeHtml(u.username)}', '${escapeHtml(u.role)}', '${escapeHtml(u.department)}')">
              ✏️ Edit
            </button>
            <button class="action-btn btn-warning" onclick="openResetModal(${u.userId}, '${escapeHtml(u.name)} (@${escapeHtml(u.username)})')">
              🔑 Reset
            </button>
            ${deactivateBtn}
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

function setupEventListeners() {
  document.getElementById('searchInput').addEventListener('input', debounce(loadUsers, 300));
  document.getElementById('roleFilter').addEventListener('change', loadUsers);
  document.getElementById('deptFilter').addEventListener('change', loadUsers);
  document.getElementById('statusFilter').addEventListener('change', loadUsers);
  document.getElementById('refreshBtn').addEventListener('click', loadUsers);

  // Logout
  document.getElementById('logoutBtn').addEventListener('click', async () => {
    try {
      await fetch('../../api/auth/logout', { method: 'POST' });
    } finally {
      window.location.href = '../login.html';
    }
  });

  // Open Create Modal
  document.getElementById('createUserBtn').addEventListener('click', () => {
    document.getElementById('createUserForm').reset();
    document.getElementById('createError').style.display = 'none';
    openModal('createUserModal');
  });

  // Close modals
  document.querySelectorAll('.closeModalBtn').forEach(btn => {
    btn.addEventListener('click', () => {
      closeAllModals();
    });
  });

  // Create User Form Submit
  document.getElementById('createUserForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById('createError');
    errorEl.style.display = 'none';

    const payload = {
      name: document.getElementById('createName').value.trim(),
      username: document.getElementById('createUsername').value.trim(),
      role: document.getElementById('createRole').value,
      department: document.getElementById('createDepartment').value,
      password: document.getElementById('createPassword').value
    };

    try {
      const res = await fetch('../../api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        errorEl.textContent = json.message || 'Failed to create user account';
        errorEl.style.display = 'block';
        return;
      }

      closeAllModals();
      await loadUsers();
    } catch (err) {
      errorEl.textContent = 'Network error: ' + err.message;
      errorEl.style.display = 'block';
    }
  });

  // Edit User Form Submit
  document.getElementById('editUserForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById('editError');
    errorEl.style.display = 'none';

    const userId = document.getElementById('editUserId').value;
    const payload = {
      name: document.getElementById('editName').value.trim(),
      role: document.getElementById('editRole').value,
      department: document.getElementById('editDepartment').value
    };

    try {
      const res = await fetch(`../../api/users/${userId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        errorEl.textContent = json.message || 'Failed to update user profile';
        errorEl.style.display = 'block';
        return;
      }

      closeAllModals();
      await loadUsers();
    } catch (err) {
      errorEl.textContent = 'Network error: ' + err.message;
      errorEl.style.display = 'block';
    }
  });

  // Reset Password Form Submit
  document.getElementById('resetPasswordForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorEl = document.getElementById('resetError');
    errorEl.style.display = 'none';

    const userId = document.getElementById('resetUserId').value;
    const customPassword = document.getElementById('resetCustomPassword').value.trim();

    const payload = {};
    if (customPassword) {
      payload.temporaryPassword = customPassword;
    }

    try {
      const res = await fetch(`../../api/users/${userId}/reset-password`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const json = await res.json();
      if (!res.ok || !json.success) {
        errorEl.textContent = json.message || 'Failed to execute password reset';
        errorEl.style.display = 'block';
        return;
      }

      // Display the generated/custom temporary password
      const tempPass = json.data ? json.data.temporaryPassword : customPassword;
      document.getElementById('generatedPasswordDisplay').textContent = tempPass;
      document.getElementById('resetResultCard').style.display = 'block';
      document.getElementById('submitResetBtn').style.display = 'none';

    } catch (err) {
      errorEl.textContent = 'Network error: ' + err.message;
      errorEl.style.display = 'block';
    }
  });

  // Copy Password Button
  document.getElementById('copyPasswordBtn').addEventListener('click', () => {
    const text = document.getElementById('generatedPasswordDisplay').textContent;
    navigator.clipboard.writeText(text).then(() => {
      document.getElementById('copyPasswordBtn').textContent = '✅ Copied!';
      setTimeout(() => {
        document.getElementById('copyPasswordBtn').textContent = '📋 Copy';
      }, 2000);
    });
  });
}

function openEditModal(userId, name, username, role, department) {
  document.getElementById('editUserId').value = userId;
  document.getElementById('editUsername').value = username;
  document.getElementById('editName').value = name;
  document.getElementById('editRole').value = role;
  document.getElementById('editDepartment').value = department;
  document.getElementById('editError').style.display = 'none';
  openModal('editUserModal');
}

function openResetModal(userId, userDisplay) {
  document.getElementById('resetUserId').value = userId;
  document.getElementById('resetTargetUser').textContent = userDisplay;
  document.getElementById('resetCustomPassword').value = '';
  document.getElementById('resetResultCard').style.display = 'none';
  document.getElementById('submitResetBtn').style.display = 'inline-block';
  document.getElementById('resetError').style.display = 'none';
  openModal('resetPasswordModal');
}

async function confirmDeactivate(userId, username) {
  if (!confirm(`Are you sure you want to deactivate the user account '@${username}'?\n\nDeactivating this user will revoke their login access across all systems. Historical audit and maintenance records will remain intact.`)) {
    return;
  }

  try {
    const res = await fetch(`../../api/users/${userId}/deactivate`, {
      method: 'PUT'
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      alert('Deactivation rejected: ' + (json.message || 'Unknown error'));
      return;
    }
    await loadUsers();
  } catch (err) {
    alert('Error during deactivation: ' + err.message);
  }
}

async function reactivateUser(userId, username) {
  try {
    const res = await fetch(`../../api/users/${userId}/activate`, {
      method: 'PUT'
    });
    const json = await res.json();
    if (!res.ok || !json.success) {
      alert('Reactivation failed: ' + (json.message || 'Unknown error'));
      return;
    }
    await loadUsers();
  } catch (err) {
    alert('Error during reactivation: ' + err.message);
  }
}

function openModal(id) {
  const el = document.getElementById(id);
  if (el) el.style.display = 'flex';
}

function closeAllModals() {
  document.querySelectorAll('.cams-modal-overlay').forEach(el => {
    el.style.display = 'none';
  });
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

function debounce(fn, ms) {
  let timer;
  return function(...args) {
    clearTimeout(timer);
    timer = setTimeout(() => fn.apply(this, args), ms);
  };
}
