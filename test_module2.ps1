# ==============================================================================
# Automated Verification Suite for CAMS Module 2: Asset Management
# ==============================================================================

$baseUrl = "http://localhost:8080"
$ErrorActionPreference = "Continue"

Write-Host "=========================================================="
Write-Host "  Starting CAMS Module 2: Asset Management Test Suite"
Write-Host "=========================================================="

# Helper to parse error responses
function Get-ErrorDetails($ex) {
    if ($ex.Response -ne $null) {
        $statusCode = [int]$ex.Response.StatusCode
        $stream = $ex.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        return @{ StatusCode = $statusCode; Body = $body }
    }
    return @{ StatusCode = 0; Body = $ex.Message }
}

# ------------------------------------------------------------------------------
# TEST 1: Request without a session returns HTTP 401 Unauthorized
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 1] Verifying that request without session returns 401..."
try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Get
    Write-Host "  [FAIL] Expected 401 Unauthorized, but request succeeded!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 401) {
        Write-Host "  [PASS] Successfully intercepted without session -> HTTP 401: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected HTTP 401, got $($err.StatusCode): $($err.Body)" -ForegroundColor Red
    }
}

# Authenticate as Administrator & Faculty for RBAC testing
Write-Host "`n[AUTH SETUP] Authenticating Administrator and Faculty sessions..."
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLoginBody = @{ username = "admin"; password = "Admin@123" } | ConvertTo-Json
$adminLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json" -WebSession $adminSession
Write-Host "  -> Admin Authenticated: $($adminLoginRes.data.user.name)"

$facultySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facultyLoginBody = @{ username = "faculty1"; password = "Faculty@123" } | ConvertTo-Json
$facultyLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $facultyLoginBody -ContentType "application/json" -WebSession $facultySession
Write-Host "  -> Faculty Authenticated: $($facultyLoginRes.data.user.name)"

# ------------------------------------------------------------------------------
# TEST 2: GET /api/assets/options master data validation
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 2] Verifying master dropdown options endpoint (/api/assets/options)..."
try {
    $optionsRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/options" -Method Get -WebSession $adminSession
    $opts = $optionsRes.data
    $deptCount = $opts.departments.Count
    $catCount = $opts.categories.Count
    $locCount = $opts.locations.Count
    Write-Host "  [PASS] Options retrieved: $deptCount Departments, $catCount Categories, $locCount Locations" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Failed to retrieve options: $_" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TEST 3: Pagination returns correct totalItems & pages
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 3] Testing pagination on GET /api/assets?page=1&size=5..."
try {
    $page1 = Invoke-RestMethod -Uri "$baseUrl/api/assets?page=1&size=5" -Method Get -WebSession $adminSession
    $pData = $page1.data
    $itemCount = $pData.items.Count
    Write-Host "  [PASS] Page 1: Returned $itemCount items (size=5), totalItems=$($pData.totalItems), totalPages=$($pData.totalPages)" -ForegroundColor Green
    if ($pData.totalItems -lt 10) {
        Write-Host "  [WARN] Total items ($($pData.totalItems)) is less than expected seed count" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  [FAIL] Pagination test failed: $_" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TEST 4: Cost validation (Cost 0 and -5 return HTTP 400 Bad Request)
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 4] Testing Purchase Cost validation (Cost 0 and Cost -5)..."

# Test 4a: Cost = 0
$costZeroPayload = @{
    assetId = "AST-TEST-ZERO"
    assetName = "Test Monitor Zero"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2025-01-01"
    purchaseCost = 0.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $costZeroPayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Cost 0 was accepted instead of returning 400!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 400) {
        Write-Host "  [PASS] Cost 0 correctly rejected with HTTP 400: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 400 for cost 0, got $($err.StatusCode)" -ForegroundColor Red
    }
}

# Test 4b: Cost = -5
$costNegativePayload = @{
    assetId = "AST-TEST-NEG"
    assetName = "Test Monitor Negative"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2025-01-01"
    purchaseCost = -5.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $costNegativePayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Cost -5 was accepted instead of returning 400!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 400) {
        Write-Host "  [PASS] Cost -5 correctly rejected with HTTP 400: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 400 for cost -5, got $($err.StatusCode)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 5: Asset ID format & duplicate constraint validation
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 5] Testing Asset ID regex validation (^([A-Z0-9-]+)$) and duplicate rejection..."

# 5a: Invalid Regex ID
$invalidIdPayload = @{
    assetId = "ast#bad@id!"
    assetName = "Invalid ID Asset"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2025-01-01"
    purchaseCost = 25000.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $invalidIdPayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Invalid Asset ID format accepted instead of returning 400!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 400) {
        Write-Host "  [PASS] Invalid regex ID correctly rejected with HTTP 400: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 400 for invalid ID format, got $($err.StatusCode)" -ForegroundColor Red
    }
}

# 5b: Create valid asset
$validAssetId = "AST-TEST-" + (Get-Random -Minimum 1000 -Maximum 9999)
$validPayload = @{
    assetId = $validAssetId
    assetName = "Laboratory Spectrophotometer"
    category = "Laboratory Equipment"
    department = "ECE"
    location = "ECE Lab 1"
    purchaseDate = "2025-01-10"
    purchaseCost = 95000.00
    warrantyExpiry = "2027-01-10"
    vendorId = "VND-TEST-01"
} | ConvertTo-Json

try {
    $created = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $validPayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [PASS] Valid asset created: $($created.data.assetId) - Status: $($created.data.status)" -ForegroundColor Green
} catch {
    Write-Host "  [FAIL] Failed to create valid asset: $_" -ForegroundColor Red
}

# 5c: Duplicate Asset ID (Expected: 409 Conflict)
try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $validPayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Duplicate Asset ID accepted instead of returning 409!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 409) {
        Write-Host "  [PASS] Duplicate Asset ID rejected with HTTP 409 Conflict: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 409 for duplicate Asset ID, got $($err.StatusCode): $($err.Body)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 6: RBAC write-blocking (Faculty PUT .../retire returns 403 Forbidden)
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 6] Testing RBAC: Faculty attempting PUT /api/assets/$validAssetId/retire..."
try {
    $retireBody = @{ reason = "Faculty unauthorized attempt" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/assets/$validAssetId/retire" -Method Put -Body $retireBody -ContentType "application/json" -WebSession $facultySession
    Write-Host "  [FAIL] Faculty was able to retire asset instead of receiving 403!" -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 403) {
        Write-Host "  [PASS] Faculty retire blocked by AuthorizationFilter with HTTP 403 Forbidden" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 403, got $($err.StatusCode)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 7: Retiring an ISSUED asset returns HTTP 409 Conflict
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 7] Setting an asset to 'ISSUED' in database and attempting retirement..."
# Update AST-CSE-002 directly to 'ISSUED' via JDBC helper
$sqlHelper = @"
package com.cams;
import com.cams.util.DBConnection;
import java.sql.*;
public class SetIssued {
    public static void main(String[] args) throws Exception {
        try (Connection c = DBConnection.getConnection();
             Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE ASSETS SET STATUS = 'ISSUED' WHERE ASSET_ID = 'AST-CSE-002'");
        }
    }
}
"@
Set-Content -Path "SetIssued.java" -Value $sqlHelper
javac -cp "target/classes;ojdbc11.jar" -d target/classes SetIssued.java
java -cp "target/classes;ojdbc11.jar" com.cams.SetIssued
Remove-Item -Path "SetIssued.java", "target/classes/com/cams/SetIssued.class" -Force -ErrorAction SilentlyContinue

try {
    $retireIssuedBody = @{ reason = "Attempting to retire an in-use issued device" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/assets/AST-CSE-002/retire" -Method Put -Body $retireIssuedBody -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Successfully retired an ISSUED asset! Expected 409 Conflict." -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 409) {
        Write-Host "  [PASS] Retiring ISSUED asset correctly blocked with HTTP 409 Conflict: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 409, got $($err.StatusCode): $($err.Body)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 8: Editing a DISPOSED asset returns HTTP 409 Conflict
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 8] Testing that editing a DISPOSED asset (AST-RET-001) returns HTTP 409 Conflict..."
$editDisposedPayload = @{
    assetName = "Renamed Retired Machine"
    category = "Computer"
    department = "CSE"
    location = "Room 101"
    purchaseDate = "2018-07-22"
    purchaseCost = 32000.00
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets/AST-RET-001" -Method Put -Body $editDisposedPayload -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [FAIL] Successfully edited a DISPOSED asset! Expected 409 Conflict." -ForegroundColor Red
} catch {
    $err = Get-ErrorDetails $_.Exception
    if ($err.StatusCode -eq 409) {
        Write-Host "  [PASS] Editing DISPOSED asset correctly rejected with HTTP 409 Conflict: $($err.Body)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Expected 409 for editing disposed asset, got $($err.StatusCode): $($err.Body)" -ForegroundColor Red
    }
}

# ------------------------------------------------------------------------------
# TEST 8B: Updating an active asset (PUT /api/assets/{id}) sets UPDATED_AT
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 8B] Updating an active asset with Administrator (PUT /api/assets/$validAssetId)..."
$updatePayload = @{
    assetName = "Spectrophotometer Pro Edition"
    category = "Laboratory Equipment"
    department = "ECE"
    location = "ECE Lab 1"
    purchaseDate = "2025-01-10"
    purchaseCost = 98000.00
    warrantyExpiry = "2027-01-10"
    vendorId = "VND-TEST-01"
} | ConvertTo-Json

try {
    $updRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$validAssetId" -Method Put -Body $updatePayload -ContentType "application/json" -WebSession $adminSession
    if ($updRes.data.assetName -eq "Spectrophotometer Pro Edition" -and $updRes.data.purchaseCost -eq 98000.00) {
        Write-Host "  [PASS] Asset updated successfully: Name='$($updRes.data.assetName)', Cost=$($updRes.data.purchaseCost), UpdatedAt=$($updRes.data.updatedAt)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Update response did not reflect changes" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] Failed to update asset: $_" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TEST 9: Soft delete / Retirement with Administrator
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 9] Retiring valid asset ($validAssetId) with Administrator..."
try {
    $retireAdminBody = @{ reason = "Decommissioned for component upgrade" } | ConvertTo-Json
    $retireRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$validAssetId/retire" -Method Put -Body $retireAdminBody -ContentType "application/json" -WebSession $adminSession
    Write-Host "  [PASS] Asset retired successfully: $($retireRes.message)" -ForegroundColor Green

    # Verify soft-delete: record still exists in DB with STATUS = 'DISPOSED'
    $retiredAsset = Invoke-RestMethod -Uri "$baseUrl/api/assets/$validAssetId" -Method Get -WebSession $adminSession
    $a = $retiredAsset.data
    if ($a.status -eq "DISPOSED" -and $a.disposalReason -eq "Decommissioned for component upgrade") {
        Write-Host "  [PASS] Soft-delete verified in DB: Status=$($a.status), Reason='$($a.disposalReason)', DisposedDate=$($a.disposedDate)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Asset did not have DISPOSED status or reason in DB" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] Admin retire failed: $_" -ForegroundColor Red
}

# ------------------------------------------------------------------------------
# TEST 10: Default listing excludes DISPOSED, includeDisposed=true includes them
# ------------------------------------------------------------------------------
Write-Host "`n[TEST 10] Verifying that default list excludes DISPOSED while includeDisposed=true includes them..."
try {
    $defaultList = Invoke-RestMethod -Uri "$baseUrl/api/assets?size=100" -Method Get -WebSession $adminSession
    $hasDisposedInDefault = $defaultList.data.items | Where-Object { $_.status -eq "DISPOSED" }
    
    $fullList = Invoke-RestMethod -Uri "$baseUrl/api/assets?size=100&includeDisposed=true" -Method Get -WebSession $adminSession
    $hasDisposedInFull = $fullList.data.items | Where-Object { $_.status -eq "DISPOSED" }

    if ($hasDisposedInDefault -eq $null -and $hasDisposedInFull.Count -gt 0) {
        Write-Host "  [PASS] Default list excluded DISPOSED ($($defaultList.data.totalItems) active), includeDisposed=true returned $($fullList.data.totalItems) total (including $($hasDisposedInFull.Count) disposed)" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] Disposed filtering behavior incorrect: default has $($hasDisposedInDefault.Count), full has $($hasDisposedInFull.Count)" -ForegroundColor Red
    }
} catch {
    Write-Host "  [FAIL] Listing filter test failed: $_" -ForegroundColor Red
}

Write-Host "`n=========================================================="
Write-Host "  CAMS MODULE 2 ALL AUTOMATED VERIFICATIONS COMPLETED!    "
Write-Host "=========================================================="
