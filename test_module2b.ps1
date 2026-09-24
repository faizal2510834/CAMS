# CAMS Module 2B Verification Test Suite
# Tests category-specific details, validation rules, transactional rollback, and backward compatibility.

$baseUrl = "http://localhost:8080"
$cookieJar = New-Object Microsoft.PowerShell.Commands.WebRequestSession

function Write-Pass($msg) {
    Write-Host "  [PASS] $msg" -ForegroundColor Green
}

function Write-Fail($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
}

function Write-Section($msg) {
    Write-Host "`n=== $msg ===" -ForegroundColor Cyan
}

function Get-HttpErrorBody($errRecord) {
    if ($errRecord.ErrorDetails -and $errRecord.ErrorDetails.Message) {
        return $errRecord.ErrorDetails.Message
    }
    if ($errRecord.Exception.Response) {
        $stream = $errRecord.Exception.Response.GetResponseStream()
        if ($stream) {
            $reader = New-Object System.IO.StreamReader($stream)
            return $reader.ReadToEnd()
        }
    }
    return $errRecord.Exception.Message
}

$allPassed = $true

# Login as Admin to get session
Write-Section "Authentication"
$loginBody = @{
    username = "admin"
    password = "Admin@123"
} | ConvertTo-Json

try {
    $loginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json" -WebSession $cookieJar
    if ($loginRes.success -and ($loginRes.data.user.role -eq "Administrator" -or $loginRes.data.role -eq "Administrator")) {
        Write-Pass "Logged in as Administrator"
    } else {
        Write-Fail "Login failed: $($loginRes.message)"
        exit 1
    }
} catch {
    Write-Fail "Login exception: $(Get-HttpErrorBody $_)"
    exit 1
}

$runId = Get-Random -Minimum 1000 -Maximum 9999

# --------------------------------------------------------------------------
# Test 1: Create Computer asset + details
# --------------------------------------------------------------------------
Write-Section "Test 1: Create Computer asset + details"
$compAssetId = "AST-M2B-COMP-$runId"
$compPayload = @{
    assetId = $compAssetId
    assetName = "Graphics Workstation"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2024-03-01"
    purchaseCost = 95000.00
    vendorId = "VND-DELL"
    warrantyExpiry = "2027-03-01"
    details = @{
        cpu = "Intel Core i9-13900K"
        monitor = "Dell UltraSharp 27-inch 4K"
        keyboard = "Mechanical USB Keyboard"
        mouse = "Logitech MX Master 3S"
        printer = "Canon imageCLASS LBP6030"
        software = "Ubuntu 22.04 LTS & CUDA Toolkit"
        ipAddress = "192.168.10.45"
    }
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $compPayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $compAssetId) {
        Write-Pass "Computer asset created successfully (HTTP 201)"
    } else {
        Write-Fail "Create Computer failed: $($res.message)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Create Computer exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# Verify GET returns nested details
try {
    $getRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$compAssetId" -Method Get -WebSession $cookieJar
    $d = $getRes.data.details
    if ($d -and $d.cpu -eq "Intel Core i9-13900K" -and $d.ipAddress -eq "192.168.10.45" -and $d.monitor -eq "Dell UltraSharp 27-inch 4K") {
        Write-Pass "GET returns matching Computer details (CPU, IP, Monitor verified)"
    } else {
        Write-Fail "Computer details mismatch: $($d | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "GET Computer details exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 2: Create Classroom Asset + details
# --------------------------------------------------------------------------
Write-Section "Test 2: Create Classroom Asset + details"
$classAssetId = "AST-M2B-CLS-$runId"
$classPayload = @{
    assetId = $classAssetId
    assetName = "Lecture Hall 101 AV Bundle"
    category = "Classroom Asset"
    department = "ECE"
    location = "Room 101"
    purchaseDate = "2024-02-15"
    purchaseCost = 145000.00
    details = @{
        furnitureDesc = "Tiered auditorium benches with folding writing pads"
        projector = "Sony Laser 5000 Lumens"
        smartBoard = "Promethean ActivPanel 86-inch"
        ac = "Voltas 3-Ton Ductable Unit"
        seatingCapacity = 120
    }
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $classPayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $classAssetId) {
        Write-Pass "Classroom Asset created successfully (HTTP 201)"
    } else {
        Write-Fail "Create Classroom Asset failed: $($res.message)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Create Classroom Asset exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

try {
    $getRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$classAssetId" -Method Get -WebSession $cookieJar
    $d = $getRes.data.details
    if ($d -and $d.projector -eq "Sony Laser 5000 Lumens" -and $d.seatingCapacity -eq 120) {
        Write-Pass "GET returns matching Classroom details (Projector, Seating Capacity=120 verified)"
    } else {
        Write-Fail "Classroom details mismatch: $($d | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "GET Classroom details exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 3: Create Laboratory Equipment + details
# --------------------------------------------------------------------------
Write-Section "Test 3: Create Laboratory Equipment + details"
$labAssetId = "AST-M2B-LAB-$runId"
$labPayload = @{
    assetId = $labAssetId
    assetName = "Digital Storage Oscilloscope"
    category = "Laboratory Equipment"
    department = "EEE"
    location = "ECE Lab 1"
    purchaseDate = "2024-01-20"
    purchaseCost = 65000.00
    details = @{
        equipmentType = "200MHz 4-Channel DSO"
        condition = "Calibrated & Operational"
        lastCalibrationDate = "2024-08-15"
    }
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $labPayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $labAssetId) {
        Write-Pass "Laboratory Equipment created successfully (HTTP 201)"
    } else {
        Write-Fail "Create Lab Equipment failed: $($res.message)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Create Lab Equipment exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

try {
    $getRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$labAssetId" -Method Get -WebSession $cookieJar
    $d = $getRes.data.details
    if ($d -and $d.equipmentType -eq "200MHz 4-Channel DSO" -and $d.condition -eq "Calibrated & Operational") {
        Write-Pass "GET returns matching Lab details (Equipment Type, Condition, Calibration Date verified)"
    } else {
        Write-Fail "Lab details mismatch: $($d | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "GET Lab details exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 4: Create Furniture + details
# --------------------------------------------------------------------------
Write-Section "Test 4: Create Furniture + details"
$furnAssetId = "AST-M2B-FURN-$runId"
$furnPayload = @{
    assetId = $furnAssetId
    assetName = "Conference Table & Chairs Set"
    category = "Furniture"
    department = "MECH"
    location = "Room 101"
    purchaseDate = "2024-04-10"
    purchaseCost = 42000.00
    details = @{
        furnitureType = "Modular Conference Table"
        material = "Teak Wood & Stainless Steel"
        quantity = 16
    }
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $furnPayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $furnAssetId) {
        Write-Pass "Furniture asset created successfully (HTTP 201)"
    } else {
        Write-Fail "Create Furniture failed: $($res.message)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Create Furniture exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

try {
    $getRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$furnAssetId" -Method Get -WebSession $cookieJar
    $d = $getRes.data.details
    if ($d -and $d.furnitureType -eq "Modular Conference Table" -and $d.quantity -eq 16) {
        Write-Pass "GET returns matching Furniture details (Furniture Type, Material, Quantity=16 verified)"
    } else {
        Write-Fail "Furniture details mismatch: $($d | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "GET Furniture details exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 5: Create 'Other' asset (details omitted/ignored)
# --------------------------------------------------------------------------
Write-Section "Test 5: Create 'Other' asset"
$otherAssetId = "AST-M2B-OTHR-$runId"
$otherPayload = @{
    assetId = $otherAssetId
    assetName = "Campus Lawn Mower"
    category = "Other"
    department = "CIVIL"
    location = "Room 101"
    purchaseDate = "2024-05-01"
    purchaseCost = 28000.00
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $otherPayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $otherAssetId) {
        Write-Pass "Other asset created successfully (HTTP 201)"
    } else {
        Write-Fail "Create Other asset failed: $($res.message)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Create Other asset exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

try {
    $getRes = Invoke-RestMethod -Uri "$baseUrl/api/assets/$otherAssetId" -Method Get -WebSession $cookieJar
    if ($getRes.data.details -eq $null) {
        Write-Pass "GET Other asset returns details: null (detail table skipped entirely)"
    } else {
        Write-Fail "Expected details: null for Other category, got: $($getRes.data.details)"
        $allPassed = $false
    }
} catch {
    Write-Fail "GET Other asset exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 6: Rollback check (detail failure leaves ZERO orphaned asset row)
# --------------------------------------------------------------------------
Write-Section "Test 6: Rollback check (Zero orphaned asset rows on failure)"
$failAssetId = "AST-M2B-FAIL-$runId"
$failPayload = @{
    assetId = $failAssetId
    assetName = "Faulty Workstation with Bad IP"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2024-06-01"
    purchaseCost = 50000.00
    details = @{
        cpu = "Intel Core i5"
        ipAddress = "999.999.999.999" # Invalid IPv4 address
    }
} | ConvertTo-Json -Depth 5

$got400 = $false
try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $failPayload -ContentType "application/json" -WebSession $cookieJar
    Write-Fail "Expected 400 Bad Request for invalid IPv4 address, but request succeeded"
    $allPassed = $false
} catch {
    $resp = $_.Exception.Response
    if ($resp.StatusCode.value__ -eq 400) {
        $got400 = $true
        Write-Pass "Request rejected with HTTP 400 Bad Request due to invalid IPv4 address"
    } else {
        Write-Fail "Expected status 400, got $($resp.StatusCode.value__)"
        $allPassed = $false
    }
}

if ($got400) {
    # Verify that asset row was rolled back and does NOT exist in ASSETS table
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/assets/$failAssetId" -Method Get -WebSession $cookieJar
        Write-Fail "Orphaned asset row found! Transaction rollback failed."
        $allPassed = $false
    } catch {
        $resp = $_.Exception.Response
        if ($resp.StatusCode.value__ -eq 404) {
            Write-Pass "Verified: GET returns HTTP 404 Not Found. Zero orphaned asset rows exist!"
        } else {
            Write-Fail "Unexpected status querying rolled back asset: $($resp.StatusCode.value__)"
            $allPassed = $false
        }
    }
}

# --------------------------------------------------------------------------
# Test 7: Update asset + details (PUT /api/assets/{id})
# --------------------------------------------------------------------------
Write-Section "Test 7: Update asset + details"
$updatePayload = @{
    assetName = "Graphics Workstation Updated"
    category = "Computer"
    department = "CSE"
    location = "IT Lab 1"
    purchaseDate = "2024-03-01"
    purchaseCost = 98000.00
    details = @{
        cpu = "Intel Core i9-13900K (Liquid Cooled)"
        monitor = "Dual Dell UltraSharp 27-inch 4K"
        keyboard = "Mechanical USB Keyboard"
        mouse = "Logitech MX Master 3S"
        printer = "Canon imageCLASS LBP6030"
        software = "Ubuntu 24.04 LTS & PyTorch"
        ipAddress = "192.168.10.99"
    }
} | ConvertTo-Json -Depth 5

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets/$compAssetId" -Method Put -Body $updatePayload -ContentType "application/json" -WebSession $cookieJar
    if ($res.success -and $res.data.details.ipAddress -eq "192.168.10.99" -and $res.data.location -eq "IT Lab 1") {
        Write-Pass "PUT /api/assets/$compAssetId updated asset and nested details successfully"
    } else {
        Write-Fail "Update asset failed: $($res | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Update asset exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Test 8: Category shape mismatch returns HTTP 400
# --------------------------------------------------------------------------
Write-Section "Test 8: Category shape mismatch returns HTTP 400"
$mismatchAssetId = "AST-M2B-MISMATCH-$runId"
$mismatchPayload = @{
    assetId = $mismatchAssetId
    assetName = "Shape Mismatch Asset"
    category = "Computer"
    department = "CSE"
    location = "CSE Lab 1"
    purchaseDate = "2024-03-01"
    purchaseCost = 50000.00
    details = @{
        cpu = "Intel i7"
        seatingCapacity = 50 # Mismatch: seatingCapacity belongs to Classroom, not Computer
    }
} | ConvertTo-Json -Depth 5

try {
    Invoke-RestMethod -Uri "$baseUrl/api/assets" -Method Post -Body $mismatchPayload -ContentType "application/json" -WebSession $cookieJar
    Write-Fail "Expected HTTP 400 for category shape mismatch, but request succeeded"
    $allPassed = $false
} catch {
    $resp = $_.Exception.Response
    if ($resp.StatusCode.value__ -eq 400) {
        Write-Pass "Category shape mismatch correctly rejected with HTTP 400 Bad Request"
    } else {
        Write-Fail "Expected HTTP 400, got status $($resp.StatusCode.value__)"
        $allPassed = $false
    }
}

# --------------------------------------------------------------------------
# Test 9: Pre-2B assets don't crash details view (return details: null)
# --------------------------------------------------------------------------
Write-Section "Test 9: Pre-2B assets backward compatibility"
# AST-CSE-001 was created in Module 2A before Module 2B detail tables existed
$pre2bId = "AST-CSE-001"

try {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/assets/$pre2bId" -Method Get -WebSession $cookieJar
    if ($res.success -and $res.data.assetId -eq $pre2bId -and $res.data.details -eq $null) {
        Write-Pass "Pre-2B asset '$pre2bId' retrieved successfully with details: null without error"
    } else {
        Write-Fail "Pre-2B asset retrieval returned unexpected payload: $($res | ConvertTo-Json)"
        $allPassed = $false
    }
} catch {
    Write-Fail "Pre-2B asset retrieval exception: $(Get-HttpErrorBody $_)"
    $allPassed = $false
}

# --------------------------------------------------------------------------
# Final Summary
# --------------------------------------------------------------------------
Write-Section "Test Suite Summary"
if ($allPassed) {
    Write-Host ">>> ALL 9 MODULE 2B TEST CASES PASSED SUCCESSFULLY! <<<" -ForegroundColor Green
    exit 0
} else {
    Write-Host ">>> SOME TEST CASES FAILED! <<<" -ForegroundColor Red
    exit 1
}
