# ==============================================================================
# CAMS Module 8: Physical Inventory Audit Test Suite
# Tests:
# 1. RBAC: Faculty strictly blocked (403 on GET & POST)
# 2. RBAC: Technical Staff has write access (POST) and read access (GET)
# 3. RBAC: Administrator has full read/reporting access (GET)
# 4. Status recording: VERIFIED, MISSING, MISLOCATED
# 5. Validation: Non-existent asset returns 404, invalid status returns 400
# 6. Filterability: Search by asset ID, filter by status, filter by department
# 7. Aggregate summary counters: Total, Verified, Missing, Mislocated
# ==============================================================================

$baseUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

$passed = 0
$failed = 0

function Assert-Test([string]$desc, [bool]$condition, [string]$detail = "") {
    if ($condition) {
        Write-Host "  [PASS] $desc" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "  [FAIL] $desc $detail" -ForegroundColor Red
        $script:failed++
    }
}

function Invoke-ApiRequest {
    param(
        [string]$Uri,
        [string]$Method = "Get",
        [string]$Body = $null,
        [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession = $null
    )
    try {
        $params = @{
            Uri = $Uri
            Method = $Method
            UseBasicParsing = $true
            ContentType = "application/json"
        }
        if ($WebSession) { $params.WebSession = $WebSession }
        if ($Body) { $params.Body = $Body }
        
        $response = Invoke-WebRequest @params
        $json = $null
        if ($response.Content) {
            $json = $response.Content | ConvertFrom-Json
        }
        return @{
            StatusCode = $response.StatusCode
            Data = $json
            Raw = $response
        }
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $status = [int]$resp.StatusCode
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $content = $reader.ReadToEnd()
        $json = $null
        try { $json = $content | ConvertFrom-Json } catch {}
        return @{
            StatusCode = $status
            Data = $json
            Raw = $content
        }
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Starting CAMS Module 8: Inventory Audit Test Suite      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# Create Sessions
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facultySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Authenticate Roles
Write-Host "`n-- Step 1: Authentication --" -ForegroundColor Yellow
$loginAdmin = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"admin","password":"Admin@123"}' -WebSession $adminSession
Assert-Test "Admin authenticated" ($loginAdmin.StatusCode -eq 200 -and $loginAdmin.Data.success)

$loginFaculty = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"faculty1","password":"Faculty@123"}' -WebSession $facultySession
Assert-Test "Faculty authenticated" ($loginFaculty.StatusCode -eq 200 -and $loginFaculty.Data.success)

$loginTech = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"tech1","password":"Tech@123"}' -WebSession $techSession
Assert-Test "Technical Staff authenticated" ($loginTech.StatusCode -eq 200 -and $loginTech.Data.success)

# Section 2: RBAC Enforcement
Write-Host "`n-- Step 2: RBAC Policy Enforcement --" -ForegroundColor Yellow
$fPost = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body '{"assetId":"AST-CSE-001","status":"VERIFIED"}' -WebSession $facultySession
Assert-Test "Faculty blocked from POST /api/audits with 403 Forbidden" ($fPost.StatusCode -eq 403)

$fGet = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -WebSession $facultySession
Assert-Test "Faculty blocked from GET /api/audits with 403 Forbidden" ($fGet.StatusCode -eq 403)

$fSum = Invoke-ApiRequest -Uri "$baseUrl/api/audits/summary" -WebSession $facultySession
Assert-Test "Faculty blocked from GET /api/audits/summary with 403 Forbidden" ($fSum.StatusCode -eq 403)

$tGet = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -WebSession $techSession
Assert-Test "Technical Staff allowed GET /api/audits (HTTP 200)" ($tGet.StatusCode -eq 200)

$aGet = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -WebSession $adminSession
Assert-Test "Administrator allowed GET /api/audits (HTTP 200)" ($aGet.StatusCode -eq 200)

# Section 3: Verification Recording by Technical Staff
Write-Host "`n-- Step 3: Conducting Physical Verifications --" -ForegroundColor Yellow

# Test 3.1: Record VERIFIED Status
$bodyVer = @{
    assetId = "AST-CSE-001"
    status = "VERIFIED"
    remarks = "Physical barcode scanned in CSE Lab 1. Asset verified in good order."
} | ConvertTo-Json
$rVer = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $bodyVer -WebSession $techSession
Assert-Test "Technical Staff recorded VERIFIED audit (HTTP 201)" ($rVer.StatusCode -eq 201)
$verData = $rVer.Data.data
Assert-Test "Audit ID generated with prefix 'AUD-'" ($verData.auditId -match "^AUD-")
Assert-Test "Verified status is 'VERIFIED'" ($verData.status -eq "VERIFIED")
Assert-Test "Verified by matches 'tech1'" ($verData.verifiedBy -eq "tech1")
Assert-Test "Joined Asset details populated (AST-CSE-001)" ($verData.assetName -ne $null -and $verData.department -eq "CSE")

# Test 3.2: Record MISSING Status
$bodyMiss = @{
    assetId = "AST-CSE-002"
    status = "MISSING"
    remarks = "Asset tag not located during physical floor sweep of CSE department."
} | ConvertTo-Json
$rMiss = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $bodyMiss -WebSession $techSession
Assert-Test "Technical Staff recorded MISSING audit (HTTP 201)" ($rMiss.StatusCode -eq 201)
$missData = $rMiss.Data.data
Assert-Test "Audit ID generated with prefix 'AUD-'" ($missData.auditId -match "^AUD-")
Assert-Test "Recorded status is 'MISSING'" ($missData.status -eq "MISSING")
Assert-Test "Remarks captured correctly" ($missData.remarks -match "floor sweep")

# Test 3.3: Record MISLOCATED Status
$bodyMisloc = @{
    assetId = "AST-ECE-001"
    status = "MISLOCATED"
    remarks = "Asset found physically in Mechanical Workshop instead of ECE Lab."
} | ConvertTo-Json
$rMisloc = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $bodyMisloc -WebSession $techSession
Assert-Test "Technical Staff recorded MISLOCATED audit (HTTP 201)" ($rMisloc.StatusCode -eq 201)
$mislocData = $rMisloc.Data.data
Assert-Test "Recorded status is 'MISLOCATED'" ($mislocData.status -eq "MISLOCATED")
Assert-Test "Remarks captured correctly" ($mislocData.remarks -match "Mechanical Workshop")

# Section 4: Validation & Error Handling
Write-Host "`n-- Step 4: Validation & Error Handling --" -ForegroundColor Yellow

# Test 4.1: Non-existent asset ID returns 404
$body404 = @{
    assetId = "AST-GHOST-NON-EXISTENT"
    status = "VERIFIED"
    remarks = "Phantom equipment"
} | ConvertTo-Json
$r404 = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $body404 -WebSession $techSession
Assert-Test "Non-existent asset ID rejected with HTTP 404 Not Found" ($r404.StatusCode -eq 404)

# Test 4.2: Invalid status returns 400 Bad Request
$bodyBadStatus = @{
    assetId = "AST-CSE-001"
    status = "DAMAGED_OR_UNKNOWN"
    remarks = "Invalid status enum"
} | ConvertTo-Json
$rBadStatus = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $bodyBadStatus -WebSession $techSession
Assert-Test "Invalid status rejected with HTTP 400 Bad Request" ($rBadStatus.StatusCode -eq 400)

# Test 4.3: Missing asset ID returns 400 Bad Request
$bodyEmptyAsset = @{
    assetId = ""
    status = "VERIFIED"
} | ConvertTo-Json
$rEmpty = Invoke-ApiRequest -Uri "$baseUrl/api/audits" -Method Post -Body $bodyEmptyAsset -WebSession $techSession
Assert-Test "Empty asset ID rejected with HTTP 400 Bad Request" ($rEmpty.StatusCode -eq 400)

# Section 5: History Filtering & Reporting
Write-Host "`n-- Step 5: History Filtering & Reporting --" -ForegroundColor Yellow

# Test 5.1: Filter by status=VERIFIED
$fVer = Invoke-ApiRequest -Uri "$baseUrl/api/audits?status=VERIFIED" -WebSession $adminSession
Assert-Test "Filter by status=VERIFIED returns results" ($fVer.StatusCode -eq 200 -and $fVer.Data.data.items.Count -ge 1)
$allVerified = $true
foreach ($item in $fVer.Data.data.items) {
    if ($item.status -ne "VERIFIED") { $allVerified = $false }
}
Assert-Test "All filtered items have status 'VERIFIED'" $allVerified

# Test 5.2: Filter by status=MISSING
$fMiss = Invoke-ApiRequest -Uri "$baseUrl/api/audits?status=MISSING" -WebSession $adminSession
Assert-Test "Filter by status=MISSING returns results" ($fMiss.StatusCode -eq 200 -and $fMiss.Data.data.items.Count -ge 1)
$allMissing = $true
foreach ($item in $fMiss.Data.data.items) {
    if ($item.status -ne "MISSING") { $allMissing = $false }
}
Assert-Test "All filtered items have status 'MISSING'" $allMissing

# Test 5.3: Filter by assetId
$fAsset = Invoke-ApiRequest -Uri "$baseUrl/api/audits?assetId=AST-CSE-001" -WebSession $techSession
Assert-Test "Filter by assetId returns matching record" ($fAsset.StatusCode -eq 200 -and $fAsset.Data.data.items.Count -ge 1)
Assert-Test "First matching item has assetId 'AST-CSE-001'" ($fAsset.Data.data.items[0].assetId -eq "AST-CSE-001")

# Test 5.4: Filter by department=CSE
$fDept = Invoke-ApiRequest -Uri "$baseUrl/api/audits?department=CSE" -WebSession $adminSession
Assert-Test "Filter by department=CSE returns matching records" ($fDept.StatusCode -eq 200 -and $fDept.Data.data.items.Count -ge 1)
$allCse = $true
foreach ($item in $fDept.Data.data.items) {
    if ($item.department -ne "CSE") { $allCse = $false }
}
Assert-Test "All filtered items belong to CSE department" $allCse

# Section 6: Audit Summary Counters
Write-Host "`n-- Step 6: Summary Counters Verification --" -ForegroundColor Yellow
$sumRes = Invoke-ApiRequest -Uri "$baseUrl/api/audits/summary" -WebSession $adminSession
Assert-Test "Admin GET /api/audits/summary returns HTTP 200" ($sumRes.StatusCode -eq 200)
$sData = $sumRes.Data.data
Assert-Test "Total audits count >= 3" ($sData.totalAudits -ge 3)
Assert-Test "Verified count >= 1" ($sData.verifiedCount -ge 1)
Assert-Test "Missing count >= 1" ($sData.missingCount -ge 1)
Assert-Test "Mislocated count >= 1" ($sData.mislocatedCount -ge 1)

# Section 7: Frontend UI Availability
Write-Host "`n-- Step 7: UI Page Availability --" -ForegroundColor Yellow
$uiTech = Invoke-WebRequest -Uri "$baseUrl/pages/technical/audit.html" -WebSession $techSession -UseBasicParsing
Assert-Test "Technical Staff can access /pages/technical/audit.html (HTTP 200)" ($uiTech.StatusCode -eq 200)

$uiAdmin = Invoke-WebRequest -Uri "$baseUrl/pages/technical/audit.html" -WebSession $adminSession -UseBasicParsing
Assert-Test "Administrator can access /pages/technical/audit.html (HTTP 200)" ($uiAdmin.StatusCode -eq 200)

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  MODULE 8 TEST RESULTS: $passed PASSED, $failed FAILED   " -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Cyan

if ($failed -gt 0) {
    exit 1
}
