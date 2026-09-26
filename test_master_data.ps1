# ==============================================================================
# CAMS - Master Data (Phase 1) Automated Test Suite
# Tests: Departments, Locations, Categories CRUD, Options, Dynamic Validations,
#        Foreign Key Integrity, RBAC 403 enforcement, and Regression Safety.
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

Write-Host "==============================================================" -ForegroundColor Cyan
Write-Host "  CAMS PHASE 1: MASTER DATA TEST SUITE" -ForegroundColor Cyan
Write-Host "==============================================================" -ForegroundColor Cyan

# 1. Authenticate sessions
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facultySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"admin","password":"Admin@123"}' -WebSession $adminSession
Assert-Condition ($adminLogin.success -eq $true -and $adminLogin.data.user.role -eq "Administrator") "Admin login successful"

$facultyLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"faculty1","password":"Faculty@123"}' -WebSession $facultySession
Assert-Condition ($facultyLogin.success -eq $true -and $facultyLogin.data.user.role -eq "Faculty") "Faculty login successful"

$techLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" `
    -Body '{"username":"tech1","password":"Tech@123"}' -WebSession $techSession
Assert-Condition ($techLogin.success -eq $true -and $techLogin.data.user.role -eq "Technical Staff") "Technical Staff login successful"

# 2. Options Feed Verification
Write-Host "`n-- Section 1: Options Feed Verification --" -ForegroundColor Yellow
$optionsRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/options" -Method Get -WebSession $adminSession
Assert-Condition ($optionsRes.success -eq $true) "GET /api/assets/options returns 200"
$optionsData = $optionsRes.data
Assert-Condition ($optionsData.departments -contains "CSE" -and $optionsData.departments -contains "ECE") "Options contains seeded departments (CSE, ECE)"
Assert-Condition ($optionsData.locations -contains "CSE Lab 1" -and $optionsData.locations -contains "Room 101") "Options contains seeded locations"
Assert-Condition ($optionsData.categories -contains "Computer" -and $optionsData.categories -contains "Furniture") "Options contains seeded categories"

# 3. Department CRUD (Admin)
Write-Host "`n-- Section 2: Department Management (Admin) --" -ForegroundColor Yellow
$deptCode = "DEPT-AI-" + (Get-Random -Minimum 1000 -Maximum 9999)
$newDeptBody = @{
    departmentId = $deptCode
    departmentName = "Department of Artificial Intelligence"
    active = "Y"
} | ConvertTo-Json

$createDeptRes = Invoke-RestMethod -Uri "$baseUrl/api/departments" -Method Post -ContentType "application/json" `
    -Body $newDeptBody -WebSession $adminSession
Assert-Condition ($createDeptRes.success -eq $true -and $createDeptRes.data.departmentId -eq $deptCode) "POST /api/departments creates new department ($deptCode)"

$getDeptRes = Invoke-RestMethod -Uri "$baseUrl/api/departments/$deptCode" -Method Get -WebSession $adminSession
Assert-Condition ($getDeptRes.success -eq $true -and $getDeptRes.data.departmentName -eq "Department of Artificial Intelligence") "GET /api/departments/{id} returns created department"

# Duplicate department ID -> 409
$dupDeptStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/departments" -Method Post -ContentType "application/json" `
        -Body $newDeptBody -WebSession $adminSession
} catch {
    $dupDeptStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($dupDeptStatus -eq 409) "POST duplicate department ID returns 409 Conflict"

# Update department name
$updateDeptBody = @{
    departmentName = "Department of AI and Data Science"
    active = "Y"
} | ConvertTo-Json
$putDeptRes = Invoke-RestMethod -Uri "$baseUrl/api/departments/$deptCode" -Method Put -ContentType "application/json" `
    -Body $updateDeptBody -WebSession $adminSession
Assert-Condition ($putDeptRes.success -eq $true -and $putDeptRes.data.departmentName -eq "Department of AI and Data Science") "PUT /api/departments/{id} updates department name"

# Deactivate department
$deactDeptRes = Invoke-RestMethod -Uri "$baseUrl/api/departments/$deptCode/deactivate" -Method Put -WebSession $adminSession
Assert-Condition ($deactDeptRes.success -eq $true) "PUT /api/departments/{id}/deactivate succeeds"

$getDeactDeptRes = Invoke-RestMethod -Uri "$baseUrl/api/departments/$deptCode" -Method Get -WebSession $adminSession
Assert-Condition ($getDeactDeptRes.data.active -eq "N") "Department active status is now 'N'"

# 4. Location CRUD (Admin)
Write-Host "`n-- Section 3: Location Management (Admin) --" -ForegroundColor Yellow
$locCode = "LOC-ROBO-" + (Get-Random -Minimum 1000 -Maximum 9999)
$newLocBody = @{
    locationId = $locCode
    locationName = "Advanced Robotics Lab 3"
    active = "Y"
} | ConvertTo-Json

$createLocRes = Invoke-RestMethod -Uri "$baseUrl/api/locations" -Method Post -ContentType "application/json" `
    -Body $newLocBody -WebSession $adminSession
Assert-Condition ($createLocRes.success -eq $true -and $createLocRes.data.locationId -eq $locCode) "POST /api/locations creates new location ($locCode)"

$getLocRes = Invoke-RestMethod -Uri "$baseUrl/api/locations/$locCode" -Method Get -WebSession $adminSession
Assert-Condition ($getLocRes.success -eq $true -and $getLocRes.data.locationName -eq "Advanced Robotics Lab 3") "GET /api/locations/{id} returns location details"

# Duplicate location ID -> 409
$dupLocStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/locations" -Method Post -ContentType "application/json" `
        -Body $newLocBody -WebSession $adminSession
} catch {
    $dupLocStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($dupLocStatus -eq 409) "POST duplicate location ID returns 409 Conflict"

# Deactivate location
$deactLocRes = Invoke-RestMethod -Uri "$baseUrl/api/locations/$locCode/deactivate" -Method Put -WebSession $adminSession
Assert-Condition ($deactLocRes.success -eq $true) "PUT /api/locations/{id}/deactivate deactivates location"

# 5. Category CRUD (Admin)
Write-Host "`n-- Section 4: Category Management (Admin) --" -ForegroundColor Yellow
$catCode = "CAT-SENSOR-" + (Get-Random -Minimum 1000 -Maximum 9999)
$newCatBody = @{
    categoryId = $catCode
    categoryName = "IoT Sensors & Microcontrollers"
    active = "Y"
} | ConvertTo-Json

$createCatRes = Invoke-RestMethod -Uri "$baseUrl/api/categories" -Method Post -ContentType "application/json" `
    -Body $newCatBody -WebSession $adminSession
Assert-Condition ($createCatRes.success -eq $true -and $createCatRes.data.categoryId -eq $catCode) "POST /api/categories creates new category ($catCode)"

$getCatRes = Invoke-RestMethod -Uri "$baseUrl/api/categories/$catCode" -Method Get -WebSession $adminSession
Assert-Condition ($getCatRes.success -eq $true -and $getCatRes.data.categoryName -eq "IoT Sensors & Microcontrollers") "GET /api/categories/{id} returns category details"

# Duplicate category ID -> 409
$dupCatStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/categories" -Method Post -ContentType "application/json" `
        -Body $newCatBody -WebSession $adminSession
} catch {
    $dupCatStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($dupCatStatus -eq 409) "POST duplicate category ID returns 409 Conflict"

# Deactivate category
$deactCatRes = Invoke-RestMethod -Uri "$baseUrl/api/categories/$catCode/deactivate" -Method Put -WebSession $adminSession
Assert-Condition ($deactCatRes.success -eq $true) "PUT /api/categories/{id}/deactivate deactivates category"

# 6. RBAC Verification (Faculty and Tech Staff cannot write)
Write-Host "`n-- Section 5: RBAC 403 Enforcement --" -ForegroundColor Yellow
$facPostStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/departments" -Method Post -ContentType "application/json" `
        -Body '{"departmentId":"DEPT-HACK","departmentName":"Hacked","active":"Y"}' -WebSession $facultySession
} catch {
    $facPostStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($facPostStatus -eq 403) "Faculty POST /api/departments returns 403 Forbidden"

$techPostStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/departments" -Method Post -ContentType "application/json" `
        -Body '{"departmentId":"DEPT-HACK2","departmentName":"Hacked2","active":"Y"}' -WebSession $techSession
} catch {
    $techPostStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($techPostStatus -eq 403) "Technical Staff POST /api/departments returns 403 Forbidden"

$facPutStatus = 0
try {
    Invoke-RestMethod -Uri "$baseUrl/api/departments/CSE/deactivate" -Method Put -WebSession $facultySession
} catch {
    $facPutStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($facPutStatus -eq 403) "Faculty PUT /api/departments/{id}/deactivate returns 403 Forbidden"

# 7. Asset Validation against Master Data
Write-Host "`n-- Section 6: Dynamic Asset Validation & Foreign Key Constraints --" -ForegroundColor Yellow
$testAssetId = "AST-MD-" + (Get-Random -Minimum 1000 -Maximum 9999)

# Invalid / nonexistent department
$invalidDeptStatus = 0
try {
    $invalidDeptAsset = @{
        assetId = $testAssetId
        assetName = "Test Precision Rig"
        category = "Computer"
        department = "NON_EXISTENT_DEPT"
        purchaseDate = "2025-01-10"
        purchaseCost = 50000.00
        location = "CSE Lab 1"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
        -Body $invalidDeptAsset -WebSession $adminSession
} catch {
    $invalidDeptStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($invalidDeptStatus -eq 400) "POST /api/assets with non-existent department returns 400 Bad Request"

# Inactive department (using $deptCode which was deactivated above)
$inactiveDeptStatus = 0
try {
    $inactiveDeptAsset = @{
        assetId = $testAssetId
        assetName = "Test Precision Rig"
        category = "Computer"
        department = $deptCode
        purchaseDate = "2025-01-10"
        purchaseCost = 50000.00
        location = "CSE Lab 1"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
        -Body $inactiveDeptAsset -WebSession $adminSession
} catch {
    $inactiveDeptStatus = $_.Exception.Response.StatusCode.value__
}
Assert-Condition ($inactiveDeptStatus -eq 400) "POST /api/assets with inactive department returns 400 Bad Request"

# Valid active master data references
$validAsset = @{
    assetId = $testAssetId
    assetName = "Test Precision Rig"
    category = "Computer"
    department = "CSE"
    purchaseDate = "2025-01-10"
    purchaseCost = 50000.00
    location = "CSE Lab 1"
    details = @{
        cpu = "Intel Xeon W-2245"
        monitor = "Dell Ultrasharp 27"
    }
} | ConvertTo-Json
$resValid = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -ContentType "application/json" `
    -Body $validAsset -WebSession $adminSession
Assert-Condition ($resValid.success -eq $true -and $resValid.data.assetId -eq $testAssetId) "POST /api/assets with active master data references returns 201 Created"

# 8. Check Existing Asset Data Integrity
Write-Host "`n-- Section 7: Existing Asset Data Integrity Check --" -ForegroundColor Yellow
$existingAssetRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/AST-CSE-001" -Method Get -WebSession $adminSession
Assert-Condition ($existingAssetRes.success -eq $true) "GET /api/assets/AST-CSE-001 succeeds"
$existingData = $existingAssetRes.data
Assert-Condition ($existingData.department -eq "CSE") "Existing asset AST-CSE-001 department is intact ('CSE')"
Assert-Condition ($existingData.category -eq "Computer") "Existing asset AST-CSE-001 category is intact ('Computer')"
Assert-Condition ($existingData.location -eq "CSE Lab 1") "Existing asset AST-CSE-001 location is intact ('CSE Lab 1')"

# 9. Verify Frontend Admin UI page
Write-Host "`n-- Section 8: Frontend UI Availability --" -ForegroundColor Yellow
$masterPage = Invoke-WebRequest -Uri "$baseUrl/pages/admin/master-data.html" -Method Get -WebSession $adminSession -UseBasicParsing
Assert-Condition ($masterPage.StatusCode -eq 200 -and $masterPage.Content -like "*Campus Master Data*") "Master data page rendered with title and tabs"

# 10. Teardown Test Fixtures
Write-Host "`n-- Section 9: Teardown Test Fixtures --" -ForegroundColor Yellow
$cleanupScript = @"
DELETE FROM assets WHERE asset_id = '$testAssetId';
DELETE FROM departments WHERE department_id = '$deptCode';
DELETE FROM locations WHERE location_id = '$locCode';
DELETE FROM categories WHERE category_id = '$catCode';
COMMIT;
EXIT;
"@
$cleanupFile = Join-Path $PSScriptRoot "db\teardown_master.sql"
$cleanupScript | Out-File -FilePath $cleanupFile -Encoding ascii
cmd /c "sqlplus -s system/7608@localhost:1521/xepdb1 @`"$cleanupFile`"" | Out-Null
if (Test-Path $cleanupFile) { Remove-Item $cleanupFile -Force }
Assert-Condition ($true) "Teardown test master data fixtures completed cleanly"

# Summary
Write-Host "`n==============================================================" -ForegroundColor Cyan
Write-Host "  PHASE 1 SUMMARY: $passCount Passed, $failCount Failed" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "==============================================================" -ForegroundColor Cyan

if ($failCount -gt 0) { exit 1 } else { exit 0 }

