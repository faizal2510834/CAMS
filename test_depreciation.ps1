# ==============================================================================
# CAMS Module 7: Depreciation Management Test Suite
# Tests:
# 1. RBAC: Faculty and Technical Staff receive 403 on both endpoints
# 2. Fresh asset: Evaluates at near-full value (0 years elapsed)
# 3. Old asset: Evaluates at reduced book value matching straight-line rate
# 4. Fully depreciated asset: Value is floored at 0.00 (never negative)
# 5. Category rates: Same cost & date across Computer (4y) vs Furniture (10y) yields differing values
# 6. Schedule: Verifies Year 0 through Year N schedule structure and numbers
# 7. Campus summary: Aggregates total cost, current book value, and category breakdown
# 8. Non-existent asset returns 404 Not Found
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
Write-Host "  Starting CAMS Module 7: Depreciation Test Suite         " -ForegroundColor Cyan
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

# Section 2: Create Test Assets for Depreciation
Write-Host "`n-- Step 2: Provisioning Test Assets --" -ForegroundColor Yellow
$today = (Get-Date).ToString("yyyy-MM-dd")
$twoYearsAgo = (Get-Date).AddYears(-2).ToString("yyyy-MM-dd")
$tenYearsAgo = (Get-Date).AddYears(-10).ToString("yyyy-MM-dd")

# Asset 1: Fresh Computer asset (purchased today, ₹100,000)
$freshCompId = "AST-DEP-FRESH-" + (Get-Random -Minimum 1000 -Maximum 9999)
$bodyFresh = @{
    assetId = $freshCompId
    assetName = "High Performance Workstation"
    category = "Computer"
    department = "CSE"
    purchaseDate = $today
    purchaseCost = 100000.00
    location = "CSE Lab 1"
    details = @{
        cpu = "Intel Core i9-13900K"
        monitor = "Dell UltraSharp 27-inch"
    }
} | ConvertTo-Json -Depth 5
$r1 = Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $bodyFresh -WebSession $adminSession
Assert-Test "Fresh Computer asset created ($freshCompId)" ($r1.StatusCode -in @(200, 201) -and $r1.Data.success)

# Asset 2: Older Computer asset (purchased 2 years ago, ₹100,000)
$oldCompId = "AST-DEP-OLD-" + (Get-Random -Minimum 1000 -Maximum 9999)
$bodyOld = @{
    assetId = $oldCompId
    assetName = "Mid-tier Desktop PC"
    category = "Computer"
    department = "CSE"
    purchaseDate = $twoYearsAgo
    purchaseCost = 100000.00
    location = "CSE Lab 1"
    details = @{
        cpu = "Intel Core i5-11400"
        monitor = "Dell 24-inch"
    }
} | ConvertTo-Json -Depth 5
$r2 = Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $bodyOld -WebSession $adminSession
Assert-Test "2-Year-Old Computer asset created ($oldCompId)" ($r2.StatusCode -in @(200, 201) -and $r2.Data.success)

# Asset 3: Fully depreciated Computer asset (purchased 10 years ago, ₹60,000)
$ancientCompId = "AST-DEP-ANC-" + (Get-Random -Minimum 1000 -Maximum 9999)
$bodyAncient = @{
    assetId = $ancientCompId
    assetName = "Legacy Core 2 Duo Tower"
    category = "Computer"
    department = "CSE"
    purchaseDate = $tenYearsAgo
    purchaseCost = 60000.00
    location = "CSE Lab 1"
    details = @{
        cpu = "Intel Core 2 Duo"
        monitor = "17-inch CRT"
    }
} | ConvertTo-Json -Depth 5
$r3 = Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $bodyAncient -WebSession $adminSession
Assert-Test "10-Year-Old Ancient asset created ($ancientCompId)" ($r3.StatusCode -in @(200, 201) -and $r3.Data.success)

# Asset 4: Furniture asset (purchased 2 years ago, ₹100,000) for Category comparison
$furnId = "AST-DEP-FURN-" + (Get-Random -Minimum 1000 -Maximum 9999)
$bodyFurn = @{
    assetId = $furnId
    assetName = "Ergonomic Conference Table"
    category = "Furniture"
    department = "CSE"
    purchaseDate = $twoYearsAgo
    purchaseCost = 100000.00
    location = "CSE Lab 1"
    details = @{
        furnitureType = "Conference Table"
        material = "Solid Wood"
        quantity = 1
    }
} | ConvertTo-Json -Depth 5
$r4 = Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $bodyFurn -WebSession $adminSession
Assert-Test "2-Year-Old Furniture asset created ($furnId)" ($r4.StatusCode -in @(200, 201) -and $r4.Data.success)

# Section 3: RBAC Enforcement
Write-Host "`n-- Step 3: RBAC Policy Enforcement --" -ForegroundColor Yellow
$rb1 = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$freshCompId/depreciation" -WebSession $facultySession
Assert-Test "Faculty blocked from GET /api/assets/{id}/depreciation with 403" ($rb1.StatusCode -eq 403)

$rb2 = Invoke-ApiRequest -Uri "$baseUrl/api/depreciation/summary" -WebSession $facultySession
Assert-Test "Faculty blocked from GET /api/depreciation/summary with 403" ($rb2.StatusCode -eq 403)

$rb3 = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$freshCompId/depreciation" -WebSession $techSession
Assert-Test "Technical Staff blocked from GET /api/assets/{id}/depreciation with 403" ($rb3.StatusCode -eq 403)

$rb4 = Invoke-ApiRequest -Uri "$baseUrl/api/depreciation/summary" -WebSession $techSession
Assert-Test "Technical Staff blocked from GET /api/depreciation/summary with 403" ($rb4.StatusCode -eq 403)

# Section 4: Single Asset Depreciation Calculations
Write-Host "`n-- Step 4: Single Asset Depreciation Verification --" -ForegroundColor Yellow

# Test 4.1: Fresh Asset
$depFresh = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$freshCompId/depreciation" -WebSession $adminSession
Assert-Test "Admin GET /api/assets/{id}/depreciation allowed (HTTP 200)" ($depFresh.StatusCode -eq 200)
$dF = $depFresh.Data.data
Assert-Test "Fresh asset has useful life = 4 years (Computer)" ($dF.usefulLifeYears -eq 4)
Assert-Test "Fresh asset annual depreciation = 25000.00" ([math]::Abs($dF.annualDepreciation - 25000.00) -lt 0.01)
Assert-Test "Fresh asset years elapsed = 0.0" ($dF.yearsElapsed -eq 0.0)
Assert-Test "Fresh asset current value = purchaseCost (100000.00)" ([math]::Abs($dF.currentValue - 100000.00) -lt 0.01)
Assert-Test "Fresh asset accumulated depreciation = 0.00" ($dF.accumulatedDepreciation -eq 0.00)
Assert-Test "Schedule has 5 periods (Year 0 through Year 4)" ($dF.schedule.Count -eq 5)
Assert-Test "Schedule Year 0 ending book value = 100000.00" ([math]::Abs($dF.schedule[0].endingBookValue - 100000.00) -lt 0.01)
Assert-Test "Schedule Year 4 ending book value = 0.00" ([math]::Abs($dF.schedule[4].endingBookValue - 0.00) -lt 0.01)

# Test 4.2: 2-Year-Old Computer Asset
$depOld = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$oldCompId/depreciation" -WebSession $adminSession
$dO = $depOld.Data.data
Assert-Test "2-Year-old asset years elapsed ~ 2.0" ($dO.yearsElapsed -ge 1.9 -and $dO.yearsElapsed -le 2.1)
Assert-Test "2-Year-old asset current value reduced (~50,000)" ($dO.currentValue -ge 45000 -and $dO.currentValue -le 55000)
Assert-Test "2-Year-old accumulated depreciation ~ 50,000" ($dO.accumulatedDepreciation -ge 45000 -and $dO.accumulatedDepreciation -le 55000)

# Test 4.3: 10-Year-Old Ancient Asset (Never Negative)
$depAncient = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$ancientCompId/depreciation" -WebSession $adminSession
$dA = $depAncient.Data.data
Assert-Test "10-Year-old asset current value is exactly 0.00 (Never Negative)" ($dA.currentValue -eq 0.00)
Assert-Test "10-Year-old accumulated depreciation equals full cost (60,000.00)" ($dA.accumulatedDepreciation -eq 60000.00)
Assert-Test "Residual value is 0.00" ($dA.residualValue -eq 0.00)

# Test 4.4: Category Rate Divergence
Write-Host "`n-- Step 5: Category Rate Divergence (Computer vs Furniture) --" -ForegroundColor Yellow
$depFurn = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$furnId/depreciation" -WebSession $adminSession
$dFurn = $depFurn.Data.data
Assert-Test "Furniture useful life = 10 years" ($dFurn.usefulLifeYears -eq 10)
Assert-Test "Furniture annual depreciation = 10000.00" ([math]::Abs($dFurn.annualDepreciation - 10000.00) -lt 0.01)
Assert-Test "Furniture current value after 2 years ~ 80,000" ($dFurn.currentValue -ge 75000 -and $dFurn.currentValue -le 85000)
Assert-Test "Category rates differ: Furniture book value ($($dFurn.currentValue)) > Computer book value ($($dO.currentValue))" ($dFurn.currentValue -gt $dO.currentValue)

# Section 6: Non-existent asset ID
Write-Host "`n-- Step 6: Edge Cases & Validation --" -ForegroundColor Yellow
$dep404 = Invoke-ApiRequest -Uri "$baseUrl/api/assets/AST-NON-EXISTENT-XYZ/depreciation" -WebSession $adminSession
Assert-Test "Non-existent asset ID returns HTTP 404 Not Found" ($dep404.StatusCode -eq 404)

# Section 7: Campus Summary
Write-Host "`n-- Step 7: Campus-Wide Valuation Summary --" -ForegroundColor Yellow
$sumReq = Invoke-ApiRequest -Uri "$baseUrl/api/depreciation/summary" -WebSession $adminSession
Assert-Test "Admin GET /api/depreciation/summary returns HTTP 200" ($sumReq.StatusCode -eq 200)
$summary = $sumReq.Data.data
Assert-Test "Total purchase cost is positive and > 0" ($summary.totalPurchaseCost -gt 0)
Assert-Test "Total current value is positive and > 0" ($summary.totalCurrentValue -gt 0)
Assert-Test "Total accumulated depreciation is positive and > 0" ($summary.totalAccumulatedDepreciation -gt 0)
Assert-Test "Active valued asset count > 100" ($summary.assetCount -gt 100)
Assert-Test "Category breakdown contains Computer category" ([bool]($summary.categoryBreakdown | Where-Object { $_.category -match "Computer" }))
Assert-Test "Category breakdown contains Furniture category" ([bool]($summary.categoryBreakdown | Where-Object { $_.category -match "Furniture" }))

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  MODULE 7 TEST RESULTS: $passed PASSED, $failed FAILED   " -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "==========================================================" -ForegroundColor Cyan

if ($failed -gt 0) {
    exit 1
}
