# ==============================================================================
# CAMS - Maintenance Management (Module 6) Automated Test Suite
# Tests:
#   1. Authentication & RBAC (Tech Staff & Admin access, Faculty 403 Forbidden)
#   2. Damaged return auto-creates a maintenance record in SCHEDULED status atomically
#   3. Manual scheduling on AVAILABLE asset transitions asset to UNDER_MAINTENANCE
#   4. Rejection of duplicate scheduling on asset already UNDER_MAINTENANCE with active ticket (409 Conflict)
#   5. Rejection of scheduling maintenance on an ISSUED asset (409 Conflict)
#   6. Negative cost validation (400 Bad Request)
#   7. Progress lifecycle transition (SCHEDULED -> IN_PROGRESS)
#   8. Completion & Asset Availability restoration (IN_PROGRESS -> COMPLETED -> Asset AVAILABLE)
#   9. Terminal state immutability (re-updating COMPLETED ticket returns 409 Conflict)
#   10. Admin view & Cost aggregation verification
#   11. Maintenance Frontend UI availability
# ==============================================================================

$baseUrl = "http://localhost:8080"
$passCount = 0
$failCount = 0

function Assert-Condition($condition, $message) {
    if ($condition) {
        Write-Host "  [PASS] $message" -ForegroundColor Green
        $script:passCount++
    } else {
        Write-Host "  [FAIL] $message" -ForegroundColor Red
        $script:failCount++
    }
}

function Invoke-ApiRequest {
    param(
        [string]$Uri,
        [string]$Method = "Get",
        [string]$Body = $null,
        $WebSession = $null,
        [hashtable]$Headers = @{}
    )
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            ContentType = "application/json"
        }
        if ($WebSession) { $params["WebSession"] = $WebSession }
        if ($Headers.Count -gt 0) { $params["Headers"] = $Headers }
        if ($Body) { $params["Body"] = $Body }

        $res = Invoke-RestMethod @params
        return @{ StatusCode = 200; Data = $res; Success = $true }
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $code = 500
        $errBody = ""
        if ($resp) {
            $code = [int]$resp.StatusCode
            $stream = $resp.GetResponseStream()
            if ($stream) {
                $reader = New-Object System.IO.StreamReader($stream)
                $errBody = $reader.ReadToEnd()
            }
        }
        return @{ StatusCode = $code; Error = $errBody; Success = $false }
    } catch {
        return @{ StatusCode = 500; Error = $_.Exception.Message; Success = $false }
    }
}

Write-Host "==============================================================" -ForegroundColor Cyan
Write-Host "  CAMS MODULE 6: MAINTENANCE MANAGEMENT TEST SUITE" -ForegroundColor Cyan
Write-Host "==============================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. Login Sessions
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 1: Authentication & Role Sessions --" -ForegroundColor Yellow
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facSession   = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techSession  = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$lAdmin = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"admin","password":"Admin@123"}' -WebSession $adminSession
Assert-Condition ($lAdmin.StatusCode -eq 200 -and $lAdmin.Data.data.user.role -eq "Administrator") "Admin login successful"

$lFac = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"faculty1","password":"Faculty@123"}' -WebSession $facSession
Assert-Condition ($lFac.StatusCode -eq 200 -and $lFac.Data.data.user.role -eq "Faculty") "Faculty login successful"

$lTech = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"tech1","password":"Tech@123"}' -WebSession $techSession
Assert-Condition ($lTech.StatusCode -eq 200 -and $lTech.Data.data.user.role -eq "Technical Staff") "Technical Staff login successful"

# Helper to create test assets
function Create-TestAsset($id, $name) {
    $body = @{
        assetId = $id
        assetName = $name
        category = "Computer"
        department = "CSE"
        purchaseDate = "2026-01-10"
        purchaseCost = 1500.00
        location = "CSE Lab 1"
    } | ConvertTo-Json
    return Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $body -WebSession $adminSession
}

$randId = Get-Random -Minimum 1000 -Maximum 9999
$astDamaged = "AST-MNT-DAM-$randId"
$astManual  = "AST-MNT-MAN-$randId"
$astIssued  = "AST-MNT-ISS-$randId"

$c1 = Create-TestAsset $astDamaged "Dell Precision Workstation"
Assert-Condition ($c1.StatusCode -eq 201 -or $c1.StatusCode -eq 200) "Fixture asset $astDamaged created"

$c2 = Create-TestAsset $astManual "Lenovo ThinkCentre Lab PC"
Assert-Condition ($c2.StatusCode -eq 201 -or $c2.StatusCode -eq 200) "Fixture asset $astManual created"

$c3 = Create-TestAsset $astIssued "HP EliteDesk Display Unit"
Assert-Condition ($c3.StatusCode -eq 201 -or $c3.StatusCode -eq 200) "Fixture asset $astIssued created"

# ------------------------------------------------------------------------------
# 2. RBAC Enforcement (Faculty Strictly Blocked with 403)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 2: RBAC Enforcement --" -ForegroundColor Yellow
$facGetRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Get -WebSession $facSession
Assert-Condition ($facGetRes.StatusCode -eq 403) "Faculty GET /api/maintenance blocked with HTTP 403 Forbidden"

$facPostRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Post -Body '{"assetId":"test"}' -WebSession $facSession
Assert-Condition ($facPostRes.StatusCode -eq 403) "Faculty POST /api/maintenance blocked with HTTP 403 Forbidden"

$facPutRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/MNT-FAKE" -Method Put -Body '{"status":"COMPLETED"}' -WebSession $facSession
Assert-Condition ($facPutRes.StatusCode -eq 403) "Faculty PUT /api/maintenance/{id} blocked with HTTP 403 Forbidden"

# Technical staff & Admin CAN access
$techGetRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Get -WebSession $techSession
Assert-Condition ($techGetRes.StatusCode -eq 200 -and $techGetRes.Data.success -eq $true) "Technical Staff GET /api/maintenance allowed (HTTP 200)"

# ------------------------------------------------------------------------------
# 3. Damaged Return Auto-Linkage with Maintenance
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 3: Damaged Return Auto-Linkage --" -ForegroundColor Yellow
# 1. Issue $astDamaged to Faculty
$issueBody = @{
    assetId = $astDamaged
    issuedToDepartment = "CSE"
    remarks = "Issued for Embedded Systems laboratory"
} | ConvertTo-Json
$issueRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issueBody -WebSession $facSession
$issueId = $issueRes.Data.data.issueId
Assert-Condition ($issueId -ne $null) "Asset $astDamaged issued to Faculty (Issue ID: $issueId)"

# 2. Return in DAMAGED condition with remarks
$returnBody = @{
    conditionOnReturn = "DAMAGED"
    returnRemarks = "Screen flickering and cracked outer casing from lab bench drop"
} | ConvertTo-Json
$returnRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId/return" -Method Put -Body $returnBody -WebSession $facSession
Assert-Condition ($returnRes.StatusCode -eq 200) "Equipment returned in DAMAGED condition (HTTP 200)"

# 3. Check Asset status transitioned to UNDER_MAINTENANCE
$checkAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astDamaged" -Method Get -WebSession $adminSession
Assert-Condition ($checkAsset.Data.data.status -eq "UNDER_MAINTENANCE") "Asset $astDamaged status transitioned to UNDER_MAINTENANCE"

# 4. Check that a MAINTENANCE record in SCHEDULED status was automatically created
$maintSearchRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance?assetId=$astDamaged" -Method Get -WebSession $techSession
Assert-Condition ($maintSearchRes.StatusCode -eq 200 -and $maintSearchRes.Data.data.items.Count -ge 1) "Auto-created maintenance record found for $astDamaged"
$autoMaint = $maintSearchRes.Data.data.items[0]
Assert-Condition ($autoMaint.status -eq "SCHEDULED") "Auto-created ticket status is SCHEDULED"
Assert-Condition ($autoMaint.faultDescription -like "*Screen flickering*") "Fault description matches return remarks"
Assert-Condition ($autoMaint.issueId -eq $issueId) "Maintenance ticket references origin Issue ID ($issueId)"
$autoMaintId = $autoMaint.maintenanceId

# ------------------------------------------------------------------------------
# 4. Manual Scheduling by Technical Staff
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 4: Manual Maintenance Scheduling --" -ForegroundColor Yellow
$todayStr = (Get-Date).ToString("yyyy-MM-dd")
$schedPayload = @{
    assetId = $astManual
    scheduledDate = $todayStr
    faultDescription = "Semi-annual cooling fan cleaning and thermal paste overhaul"
    technician = "Alex Rivera"
    cost = 0.00
} | ConvertTo-Json

$schedRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Post -Body $schedPayload -WebSession $techSession
Assert-Condition (($schedRes.StatusCode -eq 201 -or $schedRes.StatusCode -eq 200) -and $schedRes.Data.success -eq $true) "Technical Staff scheduled manual maintenance on $astManual (HTTP 201 Created)"
$manualMaintId = $schedRes.Data.data.maintenanceId
Assert-Condition ($manualMaintId -ne $null -and $manualMaintId.StartsWith("MNT-")) "Valid Maintenance ID generated: $manualMaintId"

# Verify asset status moved from AVAILABLE to UNDER_MAINTENANCE
$checkManualAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astManual" -Method Get -WebSession $adminSession
Assert-Condition ($checkManualAsset.Data.data.status -eq "UNDER_MAINTENANCE") "Asset $astManual status transitioned to UNDER_MAINTENANCE"

# ------------------------------------------------------------------------------
# 5. Duplicate Scheduling Prevention on Asset Already UNDER_MAINTENANCE
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 5: Duplicate Active Maintenance Prevention --" -ForegroundColor Yellow
$dupSchedRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Post -Body $schedPayload -WebSession $techSession
Assert-Condition ($dupSchedRes.StatusCode -eq 409) "Scheduling duplicate ticket on asset already UNDER_MAINTENANCE rejected with HTTP 409 Conflict"
Assert-Condition ($dupSchedRes.Error -like "*already UNDER_MAINTENANCE with an active ticket*") "Clean conflict message returned on duplicate ticket"

# ------------------------------------------------------------------------------
# 6. Scheduling on ISSUED Asset Rejection
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 6: Scheduling on ISSUED Asset Rejection --" -ForegroundColor Yellow
# Issue $astIssued
$issBody = @{
    assetId = $astIssued
    issuedToDepartment = "CSE"
} | ConvertTo-Json
$issRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issBody -WebSession $facSession
Assert-Condition ($issRes.StatusCode -eq 201 -or $issRes.StatusCode -eq 200) "Asset $astIssued is currently ISSUED"

$schedIssuedPayload = @{
    assetId = $astIssued
    scheduledDate = $todayStr
    faultDescription = "Preventive maintenance attempt on active loan"
} | ConvertTo-Json
$schedIssRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance" -Method Post -Body $schedIssuedPayload -WebSession $techSession
Assert-Condition ($schedIssRes.StatusCode -eq 409) "Scheduling maintenance on an ISSUED asset rejected with HTTP 409 Conflict"

# ------------------------------------------------------------------------------
# 7. Negative Cost Validation
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 7: Negative Cost Validation --" -ForegroundColor Yellow
$badCostPayload = @{
    status = "IN_PROGRESS"
    cost = -500.00
} | ConvertTo-Json
$badCostRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Put -Body $badCostPayload -WebSession $techSession
Assert-Condition ($badCostRes.StatusCode -eq 400) "Negative maintenance cost rejected with HTTP 400 Bad Request"

# ------------------------------------------------------------------------------
# 8. Maintenance Lifecycle Progression (SCHEDULED -> IN_PROGRESS)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 8: Status Progression (IN_PROGRESS) --" -ForegroundColor Yellow
$inProgressPayload = @{
    status = "IN_PROGRESS"
    technician = "Alex Rivera"
} | ConvertTo-Json
$progRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Put -Body $inProgressPayload -WebSession $techSession
Assert-Condition ($progRes.StatusCode -eq 200) "Ticket $manualMaintId updated to IN_PROGRESS (HTTP 200)"
$checkProg = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Get -WebSession $techSession
Assert-Condition ($checkProg.Data.data.status -eq "IN_PROGRESS") "Ticket status verified as IN_PROGRESS"

# ------------------------------------------------------------------------------
# 9. Maintenance Completion & Asset Availability Restoration
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 9: Maintenance Completion & Asset Availability Restoration --" -ForegroundColor Yellow
$completePayload = @{
    status = "COMPLETED"
    technician = "Alex Rivera"
    cost = 1450.00
    completedDate = $todayStr
    faultDescription = "Cooling fan cleaned, thermal compound replaced with Arctic MX-6"
} | ConvertTo-Json

$compRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Put -Body $completePayload -WebSession $techSession
Assert-Condition ($compRes.StatusCode -eq 200) "Maintenance ticket marked as COMPLETED (HTTP 200)"
$checkCompTicket = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Get -WebSession $techSession
Assert-Condition ($checkCompTicket.Data.data.status -eq "COMPLETED") "Ticket status verified as COMPLETED"
Assert-Condition ($checkCompTicket.Data.data.cost -eq 1450.00) "Maintenance cost recorded as 1450.00"
Assert-Condition ($checkCompTicket.Data.data.completedDate -ne $null) "Completed date populated"

# CRUCIAL: Verify asset status flipped from UNDER_MAINTENANCE back to AVAILABLE!
$checkAssetRestored = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astManual" -Method Get -WebSession $adminSession
Assert-Condition ($checkAssetRestored.Data.data.status -eq "AVAILABLE") "Asset $astManual status successfully restored to AVAILABLE!"

# ------------------------------------------------------------------------------
# 10. Terminal State Immutability (COMPLETED Ticket Cannot Be Modified)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 10: Terminal State Immutability --" -ForegroundColor Yellow
$reUpdatePayload = @{
    status = "IN_PROGRESS"
    cost = 2000.00
} | ConvertTo-Json
$reUpdateRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/$manualMaintId" -Method Put -Body $reUpdatePayload -WebSession $techSession
Assert-Condition ($reUpdateRes.StatusCode -eq 409) "Modifying an already COMPLETED maintenance ticket blocked with HTTP 409 Conflict"
Assert-Condition ($reUpdateRes.Error -like "*already COMPLETED*") "Clean terminal state error message returned"

# ------------------------------------------------------------------------------
# 11. Admin Overview & Cost Aggregation
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 11: Admin Overview & Cost Aggregation --" -ForegroundColor Yellow
$adminSummaryRes = Invoke-ApiRequest -Uri "$baseUrl/api/maintenance/summary" -Method Get -WebSession $adminSession
Assert-Condition ($adminSummaryRes.StatusCode -eq 200 -and $adminSummaryRes.Data.data.totalMaintenanceCost -ge 1450.00) "Admin summary returns valid total maintenance cost aggregate (>= 1450.00)"

# ------------------------------------------------------------------------------
# 12. Frontend UI Availability
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 12: Frontend UI Availability --" -ForegroundColor Yellow
$maintPage = Invoke-WebRequest -Uri "$baseUrl/pages/technical/maintenance.html" -Method Get -WebSession $techSession -UseBasicParsing
Assert-Condition ($maintPage.StatusCode -eq 200 -and $maintPage.Content -like "*Equipment Maintenance & Repair Management*") "Maintenance workspace page rendered successfully (HTTP 200)"

# ------------------------------------------------------------------------------
# Final Verdict
# ------------------------------------------------------------------------------
Write-Host "`n==============================================================" -ForegroundColor Cyan
Write-Host "  TEST RESULTS: $passCount PASSED, $failCount FAILED" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "==============================================================" -ForegroundColor Cyan

if ($failCount -eq 0) {
    Exit 0
} else {
    Exit 1
}
