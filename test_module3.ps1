# ==============================================================================
# CAMS Module 3 Acceptance Test Suite
# Tests all 9 acceptance criteria for Vendor & Purchase Management
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

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  CAMS Module 3 & Bug Fix Test Suite" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# 0. Sessions setup
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facultySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# Login as Admin
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"admin","password":"Admin@123"}' -WebSession $adminSession
Assert-Condition ($adminLogin.success -eq $true -and $adminLogin.data.user.role -eq "Administrator") "Admin login successful"

# Login as Faculty
$facultyLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"faculty1","password":"Faculty@123"}' -WebSession $facultySession
Assert-Condition ($facultyLogin.success -eq $true -and $facultyLogin.data.user.role -eq "Faculty") "Faculty login successful"

# Login as Technical Staff
$techLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"tech1","password":"Tech@123"}' -WebSession $techSession
Assert-Condition ($techLogin.success -eq $true -and $techLogin.data.user.role -eq "Technical Staff") "Technical Staff login successful"

Write-Host "`n--- Bug Fix Verification: Technical Staff Asset Details Access ---" -ForegroundColor Yellow

# Bug fix verification: tech staff can GET /api/assets, /api/assets/options, /api/assets/{id}
$techAssets = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Get -WebSession $techSession
Assert-Condition ($techAssets.success -eq $true -and $techAssets.data.items.Count -gt 0) "Technical Staff can GET /api/assets"

$sampleAssetId = $techAssets.data.items[0].assetId
$techAssetDetail = Invoke-RestMethod -Uri "$baseUrl/api/assets/$sampleAssetId" -Method Get -WebSession $techSession
Assert-Condition ($techAssetDetail.success -eq $true -and $techAssetDetail.data.assetId -eq $sampleAssetId) "Technical Staff can GET /api/assets/{id} ($sampleAssetId)"

$techPage = Invoke-WebRequest -Uri "$baseUrl/pages/technical/dashboard.html" -Method Get -WebSession $techSession -UseBasicParsing
Assert-Condition ($techPage.StatusCode -eq 200 -and $techPage.Content -like "*Campus Asset Registry & Specifications*") "Technical dashboard contains Asset Registry workspace card"

Write-Host "`n--- Criterion 1: Create vendor -> appears in list ---" -ForegroundColor Yellow
$newVendorId = "VND-TEST-" + (Get-Random -Minimum 1000 -Maximum 9999)
$vendorBody = @{
    vendorId = $newVendorId
    vendorName = "Apex Laboratory Instruments Ltd"
    contact = "+91-9876543299"
    email = "sales@apexlab.in"
    address = "12 Industrial Hub, Electronic City, Bangalore"
} | ConvertTo-Json

$createdVendor = Invoke-RestMethod -Uri "$baseUrl/api/vendors" -Method Post -ContentType "application/json" `
    -Body $vendorBody -WebSession $adminSession
Assert-Condition ($createdVendor.success -eq $true -and $createdVendor.data.vendorId -eq $newVendorId) "Vendor created successfully ($newVendorId)"

$vendorList = Invoke-RestMethod -Uri "$baseUrl/api/vendors?search=$newVendorId" -Method Get -WebSession $adminSession
$foundVendor = $vendorList.data.items | Where-Object { $_.vendorId -eq $newVendorId }
Assert-Condition ($null -ne $foundVendor -and $foundVendor.vendorName -eq "Apex Laboratory Instruments Ltd") "Created vendor appears in list search"

Write-Host "`n--- Criterion 2: Create purchase against an existing unpurchased asset -> PENDING ---" -ForegroundColor Yellow
# First create a brand new asset for clean procurement testing
$testAssetId = "AST-PUR-" + (Get-Random -Minimum 1000 -Maximum 9999)
$assetBody = @{
    assetId = $testAssetId
    assetName = "Spectrophotometer Pro X"
    category = "Laboratory Equipment"
    department = "ECE"
    location = "ECE Lab 1"
    purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    purchaseCost = 120000.00
    warrantyExpiry = (Get-Date).AddYears(2).ToString("yyyy-MM-dd")
} | ConvertTo-Json

$createdAsset = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
    -Body $assetBody -WebSession $adminSession
Assert-Condition ($createdAsset.success -eq $true) "Created test asset ($testAssetId)"

# Create Purchase
$purchaseBody1 = @{
    assetId = $testAssetId
    vendorId = $newVendorId
    invoiceNumber = "INV-APEX-001"
    purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    cost = 120000.00
} | ConvertTo-Json

$purchaseRes1 = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Post -ContentType "application/json" `
    -Body $purchaseBody1 -WebSession $adminSession
Assert-Condition ($purchaseRes1.success -eq $true -and $purchaseRes1.data.status -eq "PENDING") "Purchase created with status PENDING"
$testPurchaseId = $purchaseRes1.data.purchaseId

Write-Host "`n--- Criterion 3: Create a second purchase against the same asset -> 409, clean message ---" -ForegroundColor Yellow
try {
    $dupPurchaseBody = @{
        assetId = $testAssetId
        vendorId = $newVendorId
        invoiceNumber = "INV-APEX-002"
        purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
        cost = 120000.00
    } | ConvertTo-Json

    $dupRes = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Post -ContentType "application/json" `
        -Body $dupPurchaseBody -WebSession $adminSession
    Assert-Condition ($false) "Second purchase should have failed with 409"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errJson = $streamReader.ReadToEnd() | ConvertFrom-Json
    Assert-Condition ($statusCode -eq 409 -and $errJson.message -like "*already has an associated purchase record*") "Second purchase rejected with 409 and clean message: $($errJson.message)"
}

Write-Host "`n--- Criterion 4: Approve a purchase -> status APPROVED, and asset now shows vendor linked ---" -ForegroundColor Yellow
$approveRes = Invoke-RestMethod -Uri "$baseUrl/api/purchases/$testPurchaseId/approve" -Method Put -WebSession $adminSession
Assert-Condition ($approveRes.success -eq $true -and $approveRes.data.status -eq "APPROVED" -and $approveRes.data.approvedBy -eq "admin") "Purchase approved successfully"

# Verify asset now shows the vendor linked atomically
$linkedAsset = Invoke-RestMethod -Uri "$baseUrl/api/assets/$testAssetId" -Method Get -WebSession $adminSession
Assert-Condition ($linkedAsset.data.vendorId -eq $newVendorId) "Asset $testAssetId now linked to vendor $newVendorId via atomic transaction"

Write-Host "`n--- Criterion 5: Reject a purchase -> status REJECTED, asset's vendor untouched ---" -ForegroundColor Yellow
# Create another asset to test rejection
$testAssetId2 = "AST-REJ-" + (Get-Random -Minimum 1000 -Maximum 9999)
$assetBody2 = @{
    assetId = $testAssetId2
    assetName = "Classroom Smart Podium"
    category = "Classroom Asset"
    department = "MECH"
    location = "Room 101"
    purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    purchaseCost = 45000.00
} | ConvertTo-Json

$createdAsset2 = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
    -Body $assetBody2 -WebSession $adminSession

$purchaseBody2 = @{
    assetId = $testAssetId2
    vendorId = $newVendorId
    invoiceNumber = "INV-APEX-003"
    purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
    cost = 45000.00
} | ConvertTo-Json

$purchaseRes2 = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Post -ContentType "application/json" `
    -Body $purchaseBody2 -WebSession $adminSession
$testPurchaseId2 = $purchaseRes2.data.purchaseId

# Reject purchase
$rejectBody = @{ reason = "Exceeded departmental budget quota for Q3" } | ConvertTo-Json
$rejectRes = Invoke-RestMethod -Uri "$baseUrl/api/purchases/$testPurchaseId2/reject" -Method Put -ContentType "application/json" `
    -Body $rejectBody -WebSession $adminSession
Assert-Condition ($rejectRes.success -eq $true -and $rejectRes.data.status -eq "REJECTED") "Purchase rejected successfully"

# Verify asset's vendor was NOT touched (remains null)
$untouchedAsset = Invoke-RestMethod -Uri "$baseUrl/api/assets/$testAssetId2" -Method Get -WebSession $adminSession
Assert-Condition ($null -eq $untouchedAsset.data.vendorId -or $untouchedAsset.data.vendorId -eq "") "Asset $testAssetId2 vendor remains untouched (null)"

Write-Host "`n--- Criterion 6: Approving an already-approved purchase -> 409 ---" -ForegroundColor Yellow
try {
    $alreadyApprovedRes = Invoke-RestMethod -Uri "$baseUrl/api/purchases/$testPurchaseId/approve" -Method Put -WebSession $adminSession
    Assert-Condition ($false) "Approving already-approved purchase should fail with 409"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errJson = $streamReader.ReadToEnd() | ConvertFrom-Json
    Assert-Condition ($statusCode -eq 409 -and $errJson.message -like "*already APPROVED*") "Already approved purchase returns 409: $($errJson.message)"
}

Write-Host "`n--- Criterion 7: Deactivate a vendor -> disappears from active dropdown but displays on old records ---" -ForegroundColor Yellow
$deactRes = Invoke-RestMethod -Uri "$baseUrl/api/vendors/$newVendorId/deactivate" -Method Put -WebSession $adminSession
Assert-Condition ($deactRes.success -eq $true) "Vendor $newVendorId deactivated successfully"

# Verify vendor no longer appears in active-only list
$activeVendors = Invoke-RestMethod -Uri "$baseUrl/api/vendors?activeOnly=true" -Method Get -WebSession $adminSession
$isActivePresent = ($activeVendors.data | Where-Object { $_.vendorId -eq $newVendorId })
Assert-Condition ($null -eq $isActivePresent) "Deactivated vendor $newVendorId is not present in active vendors dropdown"

# Verify historical purchase still displays vendor correctly
$histPurchase = Invoke-RestMethod -Uri "$baseUrl/api/purchases/$testPurchaseId" -Method Get -WebSession $adminSession
Assert-Condition ($histPurchase.data.vendorId -eq $newVendorId -and $histPurchase.data.vendorName -eq "Apex Laboratory Instruments Ltd") "Historical purchase still displays deactivated vendor: $($histPurchase.data.vendorName)"

Write-Host "`n--- Criterion 8: Non-admin hitting any /api/vendors or /api/purchases -> 403 ---" -ForegroundColor Yellow
# Faculty test
try {
    $facVendors = Invoke-RestMethod -Uri "$baseUrl/api/vendors" -Method Get -WebSession $facultySession
    Assert-Condition ($false) "Faculty accessing /api/vendors should fail with 403"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($statusCode -eq 403) "Faculty accessing /api/vendors receives 403 Forbidden"
}

try {
    $facPurchases = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Get -WebSession $facultySession
    Assert-Condition ($false) "Faculty accessing /api/purchases should fail with 403"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($statusCode -eq 403) "Faculty accessing /api/purchases receives 403 Forbidden"
}

# Technical Staff test
try {
    $techVendors = Invoke-RestMethod -Uri "$baseUrl/api/vendors" -Method Get -WebSession $techSession
    Assert-Condition ($false) "Technical Staff accessing /api/vendors should fail with 403"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($statusCode -eq 403) "Technical Staff accessing /api/vendors receives 403 Forbidden"
}

try {
    $techPurchases = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Get -WebSession $techSession
    Assert-Condition ($false) "Technical Staff accessing /api/purchases should fail with 403"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Assert-Condition ($statusCode -eq 403) "Technical Staff accessing /api/purchases receives 403 Forbidden"
}

Write-Host "`n--- Criterion 9: Future-dated purchase_date -> 400 ---" -ForegroundColor Yellow
try {
    $testAssetId3 = "AST-FUT-" + (Get-Random -Minimum 1000 -Maximum 9999)
    $assetBody3 = @{
        assetId = $testAssetId3
        assetName = "Future Oscilloscope"
        category = "Laboratory Equipment"
        department = "EEE"
        location = "ECE Lab 1"
        purchaseDate = (Get-Date).ToString("yyyy-MM-dd")
        purchaseCost = 50000.00
    } | ConvertTo-Json
    $null = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
        -Body $assetBody3 -WebSession $adminSession

    $futureDate = (Get-Date).AddDays(15).ToString("yyyy-MM-dd")
    $futurePurchaseBody = @{
        assetId = $testAssetId3
        vendorId = "VND-DELL-01"
        invoiceNumber = "INV-FUT-001"
        purchaseDate = $futureDate
        cost = 50000.00
    } | ConvertTo-Json

    $futRes = Invoke-RestMethod -Uri "$baseUrl/api/purchases" -Method Post -ContentType "application/json" `
        -Body $futurePurchaseBody -WebSession $adminSession
    Assert-Condition ($false) "Future-dated purchase should fail with 400"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $streamReader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errJson = $streamReader.ReadToEnd() | ConvertFrom-Json
    Assert-Condition ($statusCode -eq 400 -and $errJson.message -like "*future*") "Future-dated purchase rejected with 400: $($errJson.message)"
}

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  Test Summary: $passCount PASSED, $failCount FAILED" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Cyan
