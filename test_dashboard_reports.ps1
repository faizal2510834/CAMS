# CAMS Module 10: Dashboard & Reports Comprehensive Test Suite
# Tests Admin/Faculty/Tech Dashboards, Reports Center, Department/Date Filters, CSV Export, and RBAC Security

$baseUrl = "http://localhost:8080"
$passCount = 0
$failCount = 0

function Assert-Condition {
    param(
        [bool]$condition,
        [string]$testName,
        [string]$details = ""
    )
    if ($condition) {
        Write-Host " [PASS] $testName" -ForegroundColor Green
        $script:passCount++
    } else {
        Write-Host " [FAIL] $testName" -ForegroundColor Red
        if ($details) {
            Write-Host "        Details: $details" -ForegroundColor Yellow
        }
        $script:failCount++
    }
}

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " CAMS MODULE 10: DASHBOARDS & REPORTS AUTOMATED TEST SUITE        " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# -------------------------------------------------------------------------
# Helper Functions: Authentication
# -------------------------------------------------------------------------
function Login-User {
    param([string]$username, [string]$password)
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    $res = Invoke-WebRequest -Uri "$baseUrl/api/auth/login" `
                             -Method Post `
                             -Body $body `
                             -ContentType "application/json" `
                             -WebSession $session `
                             -UseBasicParsing
    return $session
}

# -------------------------------------------------------------------------
# Section 1: Administrator Dashboard API (GET /api/dashboard/admin)
# -------------------------------------------------------------------------
Write-Host "`n--- 1. Testing Administrator Live Dashboard API ---" -ForegroundColor Yellow

$adminSession = Login-User "admin" "Admin@123"
$adminDashRes = Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($adminDashRes.StatusCode -eq 200) "Admin dashboard endpoint returns HTTP 200"

$adminDashJson = $adminDashRes.Content | ConvertFrom-Json
Assert-Condition ($adminDashJson.success -eq $true) "Admin dashboard response success is true"
Assert-Condition ($null -ne $adminDashJson.data.assetCounts) "Asset counts object present"
Assert-Condition ($adminDashJson.data.assetCounts.total -ge 0) "Total assets count is non-negative ($($adminDashJson.data.assetCounts.total))"
Assert-Condition ($adminDashJson.data.assetCounts.available -ge 0) "Available assets count is non-negative ($($adminDashJson.data.assetCounts.available))"
Assert-Condition ($adminDashJson.data.assetCounts.issued -ge 0) "Issued assets count is non-negative ($($adminDashJson.data.assetCounts.issued))"
Assert-Condition ($null -ne $adminDashJson.data.pendingPurchasesCount) "Pending purchases KPI present ($($adminDashJson.data.pendingPurchasesCount))"
Assert-Condition ($null -ne $adminDashJson.data.activeMaintenanceCount) "Active maintenance KPI present ($($adminDashJson.data.activeMaintenanceCount))"
Assert-Condition ($null -ne $adminDashJson.data.overdueLoansCount) "Overdue loans KPI present ($($adminDashJson.data.overdueLoansCount))"
Assert-Condition ($null -ne $adminDashJson.data.userCounts) "User accounts summary present"
Assert-Condition ($null -ne $adminDashJson.data.valuation) "Module 7 campus valuation reuse present"
Assert-Condition ($adminDashJson.data.valuation.totalPurchaseCost -ge 0) "Valuation purchase cost is non-negative (₹ $($adminDashJson.data.valuation.totalPurchaseCost))"

# -------------------------------------------------------------------------
# Section 2: Faculty Dashboard API (GET /api/dashboard/faculty)
# -------------------------------------------------------------------------
Write-Host "`n--- 2. Testing Faculty Live Dashboard API ---" -ForegroundColor Yellow

$facultySession = Login-User "faculty1" "Faculty@123"
$facultyDashRes = Invoke-WebRequest -Uri "$baseUrl/api/dashboard/faculty" -WebSession $facultySession -UseBasicParsing
Assert-Condition ($facultyDashRes.StatusCode -eq 200) "Faculty dashboard endpoint returns HTTP 200"

$facultyDashJson = $facultyDashRes.Content | ConvertFrom-Json
Assert-Condition ($facultyDashJson.success -eq $true) "Faculty dashboard response success is true"
Assert-Condition ($null -ne $facultyDashJson.data.currentlyIssuedCount) "Faculty currently issued count present ($($facultyDashJson.data.currentlyIssuedCount))"
Assert-Condition ($null -ne $facultyDashJson.data.overdueLoansCount) "Faculty overdue loans count present ($($facultyDashJson.data.overdueLoansCount))"
Assert-Condition ($null -ne $facultyDashJson.data.totalLoans) "Faculty total lifetime loans count present ($($facultyDashJson.data.totalLoans))"
Assert-Condition ($null -ne $facultyDashJson.data.activeLoans) "Faculty active loans list present"

# -------------------------------------------------------------------------
# Section 3: Technical Staff Dashboard API (GET /api/dashboard/technical)
# -------------------------------------------------------------------------
Write-Host "`n--- 3. Testing Technical Staff Live Dashboard API ---" -ForegroundColor Yellow

$techSession = Login-User "tech1" "Tech@123"
$techDashRes = Invoke-WebRequest -Uri "$baseUrl/api/dashboard/technical" -WebSession $techSession -UseBasicParsing
Assert-Condition ($techDashRes.StatusCode -eq 200) "Technical dashboard endpoint returns HTTP 200"

$techDashJson = $techDashRes.Content | ConvertFrom-Json
Assert-Condition ($techDashJson.success -eq $true) "Technical dashboard response success is true"
Assert-Condition ($null -ne $techDashJson.data.maintenance) "Maintenance metrics object present"
Assert-Condition ($techDashJson.data.maintenance.activeQueue -ge 0) "Maintenance active queue count is non-negative ($($techDashJson.data.maintenance.activeQueue))"
Assert-Condition ($techDashJson.data.maintenance.completed -ge 0) "Maintenance completed count is non-negative ($($techDashJson.data.maintenance.completed))"
Assert-Condition ($techDashJson.data.maintenance.totalCost -ge 0) "Maintenance total spend is non-negative (₹ $($techDashJson.data.maintenance.totalCost))"
Assert-Condition ($null -ne $techDashJson.data.audit) "Audit metrics object present"
Assert-Condition ($techDashJson.data.audit.verified -ge 0) "Audit verified items count is non-negative ($($techDashJson.data.audit.verified))"
Assert-Condition ($null -ne $techDashJson.data.recentMaintenance) "Recent maintenance tickets array present"
Assert-Condition ($null -ne $techDashJson.data.recentAudits) "Recent physical audits array present"

# -------------------------------------------------------------------------
# Section 4: Reports Center Analytics Summary (GET /api/reports/summary)
# -------------------------------------------------------------------------
Write-Host "`n--- 4. Testing Enterprise Reports Analytics Summary ---" -ForegroundColor Yellow

$reportRes = Invoke-WebRequest -Uri "$baseUrl/api/reports/summary" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($reportRes.StatusCode -eq 200) "Reports summary returns HTTP 200"

$reportJson = $reportRes.Content | ConvertFrom-Json
Assert-Condition ($reportJson.success -eq $true) "Reports summary response success is true"
Assert-Condition ($null -ne $reportJson.data.assetStatusSummary) "Report contains Asset Status Summary"
Assert-Condition ($null -ne $reportJson.data.valuationSummary) "Report contains Campus Valuation Summary"
Assert-Condition ($null -ne $reportJson.data.purchaseSummary) "Report contains Procurement Summary"
Assert-Condition ($null -ne $reportJson.data.circulationSummary) "Report contains Circulation & Loans Summary"
Assert-Condition ($null -ne $reportJson.data.maintenanceSummary) "Report contains Maintenance Operations Summary"
Assert-Condition ($null -ne $reportJson.data.auditSummary) "Report contains Physical Inventory Audit Summary"
Assert-Condition ($null -ne $reportJson.data.departmentBreakdown) "Report contains Department Breakdown Table"

# -------------------------------------------------------------------------
# Section 5: Reports Filtering by Department & Date Range
# -------------------------------------------------------------------------
Write-Host "`n--- 5. Testing Reports Department & Date Range Filters ---" -ForegroundColor Yellow

$deptFilterRes = Invoke-WebRequest -Uri "$baseUrl/api/reports/summary?department=Computer%20Science" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($deptFilterRes.StatusCode -eq 200) "Department-filtered report returns HTTP 200"
$deptFilterJson = $deptFilterRes.Content | ConvertFrom-Json
Assert-Condition ($deptFilterJson.success -eq $true) "Department-filtered report success is true"

$dateFilterRes = Invoke-WebRequest -Uri "$baseUrl/api/reports/summary?startDate=2020-01-01&endDate=2030-12-31" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($dateFilterRes.StatusCode -eq 200) "Date-filtered report returns HTTP 200"
$dateFilterJson = $dateFilterRes.Content | ConvertFrom-Json
Assert-Condition ($dateFilterJson.success -eq $true) "Date-filtered report success is true"

# Department Breakdown direct endpoint
$breakdownRes = Invoke-WebRequest -Uri "$baseUrl/api/reports/department-breakdown" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($breakdownRes.StatusCode -eq 200) "Direct department breakdown endpoint returns HTTP 200"
$breakdownJson = $breakdownRes.Content | ConvertFrom-Json
Assert-Condition ($breakdownJson.data.Count -gt 0) "Department breakdown contains campus departments ($($breakdownJson.data.Count) found)"

# -------------------------------------------------------------------------
# Section 6: CSV Export Endpoint (GET /api/reports/export/csv)
# -------------------------------------------------------------------------
Write-Host "`n--- 6. Testing CSV Report Export Endpoint ---" -ForegroundColor Yellow

$csvRes = Invoke-WebRequest -Uri "$baseUrl/api/reports/export/csv" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($csvRes.StatusCode -eq 200) "CSV export returns HTTP 200"
Assert-Condition ($csvRes.Headers["Content-Type"] -match "text/csv") "CSV Content-Type is text/csv"
Assert-Condition ($csvRes.Headers["Content-Disposition"] -match "attachment; filename=") "CSV attachment header present"
Assert-Condition ($csvRes.Content -match "# CAMS Institutional Asset & Operations Report") "CSV contains report title header"
Assert-Condition ($csvRes.Content -match "Department Breakdown") "CSV contains Department Breakdown section"
Assert-Condition ($csvRes.Content -match "Asset Status Summary") "CSV contains Asset Status section"

# -------------------------------------------------------------------------
# Section 6B: Live Overdue Loan Detection & Dashboard Alerting Verification
# -------------------------------------------------------------------------
Write-Host "`n--- 6B. Testing Live Overdue Loan Detection & Flagging ---" -ForegroundColor Yellow

$ovdAssetId = "AST-OVD-" + (Get-Random -Minimum 1000 -Maximum 9999)
$createOvdBody = @{
    assetId = $ovdAssetId
    assetName = "Overdue Microscope Test Unit"
    category = "Computer"
    department = "CSE"
    purchaseDate = "2026-01-01"
    purchaseCost = 50000.00
    warrantyMonths = 36
    location = "CSE Lab 1"
} | ConvertTo-Json

$createAssetRes = Invoke-WebRequest -Uri "$baseUrl/api/assets" -Method Post -Body $createOvdBody -ContentType "application/json" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($createAssetRes.StatusCode -eq 200 -or $createAssetRes.StatusCode -eq 201) "Created test asset for overdue verification: $ovdAssetId"

# Faculty issues the asset with an expected return date 5 days in the past
$pastDueDate = (Get-Date).AddDays(-5).ToString("yyyy-MM-dd")
$issueOvdBody = @{
    assetId = $ovdAssetId
    issuedToDepartment = "CSE"
    issueDate = (Get-Date).AddDays(-10).ToString("yyyy-MM-dd")
    expectedReturnDate = $pastDueDate
    remarks = "Test overdue equipment loan verification"
} | ConvertTo-Json

$issueOvdRes = Invoke-WebRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issueOvdBody -ContentType "application/json" -WebSession $facultySession -UseBasicParsing
$issueOvdJson = $issueOvdRes.Content | ConvertFrom-Json
$ovdIssueId = $issueOvdJson.data.issueId
Assert-Condition ($ovdIssueId -ne $null -and $ovdIssueId.StartsWith("ISS-")) "Overdue test issue created successfully: $ovdIssueId"

# Query Admin Dashboard and verify overdueLoansCount is at least 1
$adminOvdDash = (Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -WebSession $adminSession -UseBasicParsing).Content | ConvertFrom-Json
Assert-Condition ($adminOvdDash.data.overdueLoansCount -ge 1) "Admin dashboard actively detects overdue loan (count: $($adminOvdDash.data.overdueLoansCount))"

# Query Faculty Dashboard and verify overdue loans count and isOverdue flag
$facOvdDash = (Invoke-WebRequest -Uri "$baseUrl/api/dashboard/faculty" -WebSession $facultySession -UseBasicParsing).Content | ConvertFrom-Json
Assert-Condition ($facOvdDash.data.overdueLoansCount -ge 1) "Faculty dashboard actively detects overdue loan (count: $($facOvdDash.data.overdueLoansCount))"

$activeItem = $facOvdDash.data.activeLoans | Where-Object { $_.issueId -eq $ovdIssueId }
Assert-Condition ($null -ne $activeItem -and $activeItem.isOverdue -eq $true) "Faculty loan is flagged with isOverdue = true"

# Return the asset
$returnBody = @{
    conditionOnReturn = "GOOD"
    returnRemarks = "Completed overdue test loan return"
} | ConvertTo-Json
$returnRes = Invoke-WebRequest -Uri "$baseUrl/api/issues/$ovdIssueId/return" -Method Put -Body $returnBody -ContentType "application/json" -WebSession $facultySession -UseBasicParsing
Assert-Condition ($returnRes.StatusCode -eq 200) "Overdue test loan returned successfully (HTTP 200)"

# Verify Admin dashboard overdue count decrements
$adminAfterDash = (Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -WebSession $adminSession -UseBasicParsing).Content | ConvertFrom-Json
Assert-Condition ($adminAfterDash.data.overdueLoansCount -eq ($adminOvdDash.data.overdueLoansCount - 1)) "Admin overdue loan count accurately decremented after return"

# -------------------------------------------------------------------------
# Section 7: RBAC Security Enforcement Across Dashboards & Reports
# -------------------------------------------------------------------------
Write-Host "`n--- 7. Testing RBAC Security Enforcement ---" -ForegroundColor Yellow

# Unauthenticated access to /api/dashboard/admin
try {
    Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -UseBasicParsing
    Assert-Condition $false "Unauthenticated access to /api/dashboard/admin should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 401) "Unauthenticated /api/dashboard/admin returns HTTP 401 Unauthorized ($code)"
}

# Faculty probe /api/dashboard/admin -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -WebSession $facultySession -UseBasicParsing
    Assert-Condition $false "Faculty probe /api/dashboard/admin should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Faculty accessing /api/dashboard/admin returns HTTP 403 Forbidden ($code)"
}

# Technical Staff probe /api/dashboard/admin -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/dashboard/admin" -WebSession $techSession -UseBasicParsing
    Assert-Condition $false "Technical Staff probe /api/dashboard/admin should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Technical Staff accessing /api/dashboard/admin returns HTTP 403 Forbidden ($code)"
}

# Faculty probe /api/reports/summary -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/reports/summary" -WebSession $facultySession -UseBasicParsing
    Assert-Condition $false "Faculty probe /api/reports/summary should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Faculty accessing /api/reports/summary returns HTTP 403 Forbidden ($code)"
}

# Technical Staff probe /api/reports/summary -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/reports/summary" -WebSession $techSession -UseBasicParsing
    Assert-Condition $false "Technical Staff probe /api/reports/summary should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Technical Staff accessing /api/reports/summary returns HTTP 403 Forbidden ($code)"
}

# Faculty probe CSV Export -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/reports/export/csv" -WebSession $facultySession -UseBasicParsing
    Assert-Condition $false "Faculty probe /api/reports/export/csv should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Faculty accessing /api/reports/export/csv returns HTTP 403 Forbidden ($code)"
}

# Technical Staff probe CSV Export -> 403 Forbidden
try {
    Invoke-WebRequest -Uri "$baseUrl/api/reports/export/csv" -WebSession $techSession -UseBasicParsing
    Assert-Condition $false "Technical Staff probe /api/reports/export/csv should fail"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($code -eq 403) "Technical Staff accessing /api/reports/export/csv returns HTTP 403 Forbidden ($code)"
}

# Admin can view Technical Dashboard for campus oversight
$adminTechRes = Invoke-WebRequest -Uri "$baseUrl/api/dashboard/technical" -WebSession $adminSession -UseBasicParsing
Assert-Condition ($adminTechRes.StatusCode -eq 200) "Admin has authorization to view /api/dashboard/technical"

# -------------------------------------------------------------------------
# Section 8: UI Pages Availability & Integrity
# -------------------------------------------------------------------------
Write-Host "`n--- 8. Testing UI Pages HTTP 200 Availability ---" -ForegroundColor Yellow

$uiPages = @(
    "/pages/admin/reports.html",
    "/pages/admin/dashboard.html",
    "/pages/faculty/dashboard.html",
    "/pages/technical/dashboard.html"
)

foreach ($page in $uiPages) {
    $uiRes = Invoke-WebRequest -Uri "$baseUrl$page" -UseBasicParsing
    Assert-Condition ($uiRes.StatusCode -eq 200) "UI page $page is available (HTTP 200)"
}

# -------------------------------------------------------------------------
# Summary
# -------------------------------------------------------------------------
Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host " MODULE 10 TEST RESULTS SUMMARY: $passCount PASSED, $failCount FAILED" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "==================================================================" -ForegroundColor Cyan

if ($failCount -gt 0) {
    exit 1
}
