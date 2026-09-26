/**
 * CAMS - Enterprise Reports & Institutional Analytics
 * Module 10: Reports Controller & Filter Engine
 */

document.addEventListener('DOMContentLoaded', () => {
  loadSession();
  initPrintDate();
  loadDepartmentsFilter();
  loadReportData();
});

const formatInr = (val) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(val || 0);

function initPrintDate() {
  const el = document.getElementById('printDate');
  if (el) {
    el.textContent = new Date().toLocaleString('en-IN', {
      dateStyle: 'full',
      timeStyle: 'medium'
    });
  }
}

async function loadSession() {
  try {
    const res = await fetch('../../api/auth/session');
    if (!res.ok) {
      window.location.href = '../login.html?redirect=/pages/admin/reports.html';
      return;
    }
    const data = await res.json();
    const user = data.data;

    // RBAC: Administrator only
    if (user.role !== 'Administrator') {
      alert('Access Denied: The Reports Center requires Administrator privileges.');
      window.location.href = '../login.html';
      return;
    }

    const nameEl = document.getElementById('userDisplayName');
    const userEl = document.getElementById('userUsername');
    const deptEl = document.getElementById('userDept');
    const avatarEl = document.getElementById('avatarLetter');

    if (nameEl) nameEl.textContent = user.name;
    if (userEl) userEl.textContent = '@' + user.username;
    if (deptEl) deptEl.textContent = user.department;
    if (avatarEl) avatarEl.textContent = user.name.charAt(0).toUpperCase();

  } catch (err) {
    console.error('Session validation error:', err);
    window.location.href = '../login.html';
  }
}

async function loadDepartmentsFilter() {
  try {
    const res = await fetch('../../api/departments');
    if (!res.ok) return;
    const json = await res.json();
    const list = json.data || json || [];

    const select = document.getElementById('filterDept');
    if (!select) return;

    list.forEach(dept => {
      const name = typeof dept === 'string' ? dept : dept.departmentName;
      if (name) {
        const opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        select.appendChild(opt);
      }
    });
  } catch (err) {
    console.warn('Could not load departments filter options:', err);
  }
}

async function loadReportData() {
  const dept = document.getElementById('filterDept')?.value || '';
  const startDate = document.getElementById('filterStartDate')?.value || '';
  const endDate = document.getElementById('filterEndDate')?.value || '';

  const params = new URLSearchParams();
  if (dept) params.append('department', dept);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);

  try {
    const url = `../../api/reports/summary?${params.toString()}`;
    const res = await fetch(url);
    if (!res.ok) {
      console.error('Report fetch failed with status:', res.status);
      return;
    }

    const json = await res.json();
    if (!json.success || !json.data) return;
    const d = json.data;

    // 1. Asset Status Summary
    if (d.assetStatusSummary) {
      const a = d.assetStatusSummary;
      setText('rptTotalAssets', a.total);
      setText('rptAvailableAssets', a.available);
      setText('rptIssuedAssets', a.issued);
      setText('rptMaintAssets', a.underMaintenance);
      setText('rptDisposedAssets', a.disposed);
    }

    // 2. Campus Valuation & Depreciation (Module 7)
    if (d.valuationSummary) {
      const v = d.valuationSummary;
      setText('rptValOriginal', formatInr(v.totalPurchaseCost));
      setText('rptValCurrent', formatInr(v.totalCurrentValue));
      setText('rptValDepr', formatInr(v.totalAccumulatedDepreciation));
      setText('rptValCount', v.assetCount ?? 0);

      const tbody = document.getElementById('rptCategoryValBody');
      if (tbody && v.categoryBreakdown) {
        if (v.categoryBreakdown.length === 0) {
          tbody.innerHTML = '<tr><td colspan="5" style="text-align: center; color: var(--text-muted); padding: 1rem;">No asset depreciation records found.</td></tr>';
        } else {
          tbody.innerHTML = v.categoryBreakdown.map(cat => `
            <tr>
              <td style="font-weight: 600; color: #ffffff;">${cat.category}</td>
              <td style="font-family: var(--font-mono);">${cat.assetCount}</td>
              <td>${formatInr(cat.originalCost)}</td>
              <td style="color: #34d399; font-weight: 600;">${formatInr(cat.currentValue)}</td>
              <td style="color: #fbbf24;">${formatInr(cat.depreciatedAmount)}</td>
            </tr>
          `).join('');
        }
      }
    }

    // 3. Purchases Summary
    if (d.purchaseSummary) {
      const p = d.purchaseSummary;
      setText('rptTotalPurchases', p.totalPurchases);
      setText('rptApprovedPurchases', p.approvedCount);
      setText('rptPendingPurchases', p.pendingCount);
      setText('rptRejectedPurchases', p.rejectedCount);
      setText('rptTotalSpend', formatInr(p.totalPurchaseCost));
    }

    // 4. Circulation Summary
    if (d.circulationSummary) {
      const c = d.circulationSummary;
      setText('rptTotalIssues', c.totalIssues);
      setText('rptActiveLoans', c.activeLoans);
      setText('rptOverdueLoans', c.overdueLoans);
      setText('rptGoodReturns', c.returnedCount);
      setText('rptDamagedReturns', c.damagedReturns);
    }

    // 5. Maintenance Summary
    if (d.maintenanceSummary) {
      const m = d.maintenanceSummary;
      setText('rptTotalMaintTickets', m.totalTickets);
      setText('rptOpenMaintQueue', m.openQueue);
      setText('rptCompletedMaint', m.completedCount);
      setText('rptTotalMaintCost', formatInr(m.totalMaintenanceCost));
    }

    // 6. Physical Inventory Audit Summary
    if (d.auditSummary) {
      const au = d.auditSummary;
      setText('rptTotalAudits', au.totalAudits);
      setText('rptVerifiedAudits', au.verifiedCount);
      setText('rptMissingAudits', au.missingCount);
      setText('rptMislocatedAudits', au.mislocatedCount);
    }

    // 7. Department Breakdown Table
    const deptBody = document.getElementById('rptDeptBreakdownBody');
    const deptList = d.departmentBreakdown || [];
    if (deptBody) {
      if (deptList.length === 0) {
        deptBody.innerHTML = '<tr><td colspan="7" style="text-align: center; color: var(--text-muted); padding: 1.25rem;">No departmental data available.</td></tr>';
      } else {
        deptBody.innerHTML = deptList.map(item => `
          <tr>
            <td style="font-weight: 700; color: #ffffff;">${item.department}</td>
            <td style="font-family: var(--font-mono); font-weight: 600; color: #a5b4fc;">${item.assetCount}</td>
            <td style="font-weight: 600; color: #34d399;">${formatInr(item.totalAssetCost)}</td>
            <td style="color: #6ee7b7;">${item.availableCount}</td>
            <td style="color: #60a5fa;">${item.issuedCount}</td>
            <td style="color: #fbbf24;">${item.maintenanceCount}</td>
            <td style="color: #ef4444;">${item.disposedCount}</td>
          </tr>
        `).join('');
      }
    }

  } catch (err) {
    console.error('Error fetching report analytics:', err);
  }
}

function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = (val !== undefined && val !== null) ? val : '-';
}

function applyReportFilters() {
  loadReportData();
}

function resetReportFilters() {
  const dept = document.getElementById('filterDept');
  const start = document.getElementById('filterStartDate');
  const end = document.getElementById('filterEndDate');

  if (dept) dept.value = '';
  if (start) start.value = '';
  if (end) end.value = '';

  loadReportData();
}

function exportReportCsv() {
  const dept = document.getElementById('filterDept')?.value || '';
  const startDate = document.getElementById('filterStartDate')?.value || '';
  const endDate = document.getElementById('filterEndDate')?.value || '';

  const params = new URLSearchParams();
  if (dept) params.append('department', dept);
  if (startDate) params.append('startDate', startDate);
  if (endDate) params.append('endDate', endDate);

  window.location.href = `../../api/reports/export/csv?${params.toString()}`;
}

async function logout() {
  try {
    await fetch('../../api/auth/logout', { method: 'POST' });
  } catch (e) {}
  window.location.href = '../login.html';
}
