# ==============================================================================
# CAMS - Issue & Return Management (Phase 2) Automated Test Suite
# Tests:
#   1. Authentication & RBAC (Faculty self-service, Admin full, Tech Staff 403)
#   2. Validations & Department FK checks
#   3. Ownership enforcement (Faculty isolated to own transactions, 403 on others)
#   4. Rollback safety (status rolls back to AVAILABLE if insert fails)
#   5. Concurrent double-issue prevention (atomic 201 vs 409 race)
#   6. Concurrent double-return prevention (atomic 200 vs 409 race)
#   7. Return condition branching (GOOD -> AVAILABLE, DAMAGED -> UNDER_MAINTENANCE)
#   8. Retire protection on ISSUED assets (409 Conflict)
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
Write-Host "  CAMS PHASE 2: ISSUE & RETURN TEST SUITE" -ForegroundColor Cyan
Write-Host "==============================================================" -ForegroundColor Cyan

# ------------------------------------------------------------------------------
# 1. Login Sessions
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 1: Authentication & Role Sessions --" -ForegroundColor Yellow
$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$fac1Session  = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$fac2Session  = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techSession  = New-Object Microsoft.PowerShell.Commands.WebRequestSession

$lAdmin = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"admin","password":"Admin@123"}' -WebSession $adminSession
Assert-Condition ($lAdmin.StatusCode -eq 200 -and $lAdmin.Data.data.user.role -eq "Administrator") "Admin login successful"

$lFac1 = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"faculty1","password":"Faculty@123"}' -WebSession $fac1Session
Assert-Condition ($lFac1.StatusCode -eq 200 -and $lFac1.Data.data.user.role -eq "Faculty") "Faculty1 login successful"

$lFac2 = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"faculty2","password":"Faculty@123"}' -WebSession $fac2Session
Assert-Condition ($lFac2.StatusCode -eq 200 -and $lFac2.Data.data.user.role -eq "Faculty") "Faculty2 login successful"

$lTech = Invoke-ApiRequest -Uri "$baseUrl/api/auth/login" -Method Post -Body '{"username":"tech1","password":"Tech@123"}' -WebSession $techSession
Assert-Condition ($lTech.StatusCode -eq 200 -and $lTech.Data.data.user.role -eq "Technical Staff") "Technical Staff login successful"

# ------------------------------------------------------------------------------
# 2. Asset Preparation
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 2: Asset Test Fixture Preparation --" -ForegroundColor Yellow
function Create-TestAsset($id, $name) {
    $body = @{
        assetId = $id
        assetName = $name
        category = "Computer"
        department = "CSE"
        purchaseDate = "2026-01-10"
        purchaseCost = 1200.00
        warrantyMonths = 24
        location = "CSE Lab 1"
    } | ConvertTo-Json
    $res = Invoke-ApiRequest -Uri "$baseUrl/api/assets" -Method Post -Body $body -WebSession $adminSession
    return $res
}

$astPrefix = "AST-IR-" + (Get-Random -Minimum 1000 -Maximum 9999)
$ast1 = "$astPrefix-01"
$ast2 = "$astPrefix-02"
$ast3 = "$astPrefix-03"
$astRaceIssue = "$astPrefix-R1"
$astRaceReturn = "$astPrefix-R2"
$astRollback = "$astPrefix-RB"
$astCondDamaged = "$astPrefix-CD"

$c1 = Create-TestAsset $ast1 "Laptop Dell Latitude 7420"
Assert-Condition ($c1.StatusCode -eq 201 -or $c1.StatusCode -eq 200) "Test asset $ast1 created"

$c2 = Create-TestAsset $ast2 "MacBook Pro M2"
Assert-Condition ($c2.StatusCode -eq 201 -or $c2.StatusCode -eq 200) "Test asset $ast2 created"

$c3 = Create-TestAsset $ast3 "ThinkPad T14"
Assert-Condition ($c3.StatusCode -eq 201 -or $c3.StatusCode -eq 200) "Test asset $ast3 created"

$cRaceIssue = Create-TestAsset $astRaceIssue "Race Target Asset Issue"
Assert-Condition ($cRaceIssue.StatusCode -eq 201 -or $cRaceIssue.StatusCode -eq 200) "Test asset $astRaceIssue created"

$cRaceReturn = Create-TestAsset $astRaceReturn "Race Target Asset Return"
Assert-Condition ($cRaceReturn.StatusCode -eq 201 -or $cRaceReturn.StatusCode -eq 200) "Test asset $astRaceReturn created"

$cRollback = Create-TestAsset $astRollback "Rollback Test Asset"
Assert-Condition ($cRollback.StatusCode -eq 201 -or $cRollback.StatusCode -eq 200) "Test asset $astRollback created"

$cDamaged = Create-TestAsset $astCondDamaged "Damage Branching Target Asset"
Assert-Condition ($cDamaged.StatusCode -eq 201 -or $cDamaged.StatusCode -eq 200) "Test asset $astCondDamaged created"

# ------------------------------------------------------------------------------
# 3. Faculty Equipment Issuance (Self-Service)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 3: Faculty Equipment Issuance --" -ForegroundColor Yellow
$todayStr = (Get-Date).ToString("yyyy-MM-dd")
$expStr = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")

$issueBody1 = @{
    assetId = $ast1
    issuedToDepartment = "CSE"
    issueDate = $todayStr
    expectedReturnDate = $expStr
    remarks = "Issued for Machine Learning research seminar"
} | ConvertTo-Json

$issueRes1 = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issueBody1 -WebSession $fac1Session
Assert-Condition (($issueRes1.StatusCode -eq 201 -or $issueRes1.StatusCode -eq 200) -and $issueRes1.Data.success -eq $true) "Faculty1 successfully issued $ast1 (HTTP 201)"
$issueId1 = $issueRes1.Data.data.issueId
Assert-Condition ($issueId1 -ne $null -and $issueId1.StartsWith("ISS-")) "Issue ID generated correctly: $issueId1"

# Verify asset status updated to ISSUED
$checkAsset1 = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$ast1" -Method Get -WebSession $fac1Session
Assert-Condition ($checkAsset1.Data.data.status -eq "ISSUED") "Asset $ast1 status transitioned to ISSUED"

# ------------------------------------------------------------------------------
# 4. Department Foreign Key Validation
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 4: Department FK Validation --" -ForegroundColor Yellow
$invalidDeptBody = @{
    assetId = $ast2
    issuedToDepartment = "NON_EXISTENT_DEPT_CODE_999"
    issueDate = $todayStr
} | ConvertTo-Json

$invDeptRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $invalidDeptBody -WebSession $fac1Session
Assert-Condition ($invDeptRes.StatusCode -eq 400) "Issuing with non-existent department rejected with HTTP 400"

# ------------------------------------------------------------------------------
# 5. Technical Staff RBAC (Write Access Blocked)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 5: Technical Staff RBAC Enforcement --" -ForegroundColor Yellow
$techIssueBody = @{
    assetId = $ast2
    issuedToDepartment = "CSE"
} | ConvertTo-Json

$techIssueRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $techIssueBody -WebSession $techSession
Assert-Condition ($techIssueRes.StatusCode -eq 403) "Technical Staff POST /api/issues blocked with HTTP 403"

$techReturnRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId1/return" -Method Put -Body '{"conditionOnReturn":"GOOD"}' -WebSession $techSession
Assert-Condition ($techReturnRes.StatusCode -eq 403) "Technical Staff PUT /api/issues/{id}/return blocked with HTTP 403"

# Technical staff CAN view list (read-only audit)
$techListRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Get -WebSession $techSession
Assert-Condition ($techListRes.StatusCode -eq 200 -and $techListRes.Data.success -eq $true) "Technical Staff GET /api/issues permitted (HTTP 200 Read-only)"

# ------------------------------------------------------------------------------
# 6. Ownership Isolation (Faculty cannot view or return another's issue)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 6: Ownership Isolation Between Faculty --" -ForegroundColor Yellow
# Faculty2 tries to view Faculty1's transaction
$fac2ViewRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId1" -Method Get -WebSession $fac2Session
Assert-Condition ($fac2ViewRes.StatusCode -eq 403) "Faculty2 cannot view Faculty1 transaction (HTTP 403 Forbidden)"

# Faculty2 tries to return Faculty1's transaction
$fac2ReturnRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId1/return" -Method Put -Body '{"conditionOnReturn":"GOOD"}' -WebSession $fac2Session
Assert-Condition ($fac2ReturnRes.StatusCode -eq 403) "Faculty2 cannot return Faculty1 transaction (HTTP 403 Forbidden)"

# Faculty2 search list does NOT include Faculty1's issue
$fac2ListRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Get -WebSession $fac2Session
$fac2IssueIds = $fac2ListRes.Data.data.items | ForEach-Object { $_.issueId }
Assert-Condition (-not ($fac2IssueIds -contains $issueId1)) "Faculty2 search list excludes Faculty1 transactions"

# Admin CAN view Faculty1's transaction
$adminViewRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId1" -Method Get -WebSession $adminSession
Assert-Condition ($adminViewRes.StatusCode -eq 200 -and $adminViewRes.Data.data.issueId -eq $issueId1) "Admin can view Faculty1 transaction (HTTP 200 OK)"

# ------------------------------------------------------------------------------
# 7. Transaction Rollback Safety
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 7: Transaction Rollback on Failure --" -ForegroundColor Yellow
# Build a remarks string exceeding DB column size (max 300 in schema)
$overlongRemarks = "A" * 350
$failInsertBody = @{
    assetId = $astRollback
    issuedToDepartment = "CSE"
    remarks = $overlongRemarks
} | ConvertTo-Json

$failInsertRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $failInsertBody -WebSession $fac1Session
Assert-Condition ($failInsertRes.StatusCode -eq 500 -or $failInsertRes.StatusCode -eq 400) "Insert with invalid overlong remarks fails as expected"

# Crucial check: verify that $astRollback is STILL 'AVAILABLE' and was rolled back
$checkRollbackAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astRollback" -Method Get -WebSession $adminSession
Assert-Condition ($checkRollbackAsset.Data.data.status -eq "AVAILABLE") "Asset status safely rolled back to AVAILABLE after insert failure"

# ------------------------------------------------------------------------------
# 8. Concurrent Double-Issue Prevention Race Test
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 8: Concurrent Double-Issue Prevention Race Test --" -ForegroundColor Yellow
$cookieFac1 = $fac1Session.Cookies.GetCookies($baseUrl)["JSESSIONID"].Value
$cookieFac2 = $fac2Session.Cookies.GetCookies($baseUrl)["JSESSIONID"].Value

$raceIssueBody1 = @{
    assetId = $astRaceIssue
    issuedToDepartment = "CSE"
    remarks = "Concurrent issue attempt by Faculty 1"
} | ConvertTo-Json

$raceIssueBody2 = @{
    assetId = $astRaceIssue
    issuedToDepartment = "IT"
    remarks = "Concurrent issue attempt by Faculty 2"
} | ConvertTo-Json

# Launch two simultaneous background requests targeting the exact same AVAILABLE asset
$jobIssue1 = Start-Job -ScriptBlock {
    param($url, $cookieVal, $body)
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Cookie", "JSESSIONID=$cookieVal")
        $wc.Headers.Add("Content-Type", "application/json")
        $resp = $wc.UploadString($url, "POST", $body)
        return @{ StatusCode = 201; Response = $resp }
    } catch [System.Net.WebException] {
        return @{ StatusCode = [int]$_.Exception.Response.StatusCode; Error = $_.Exception.Message }
    }
} -ArgumentList "$baseUrl/api/issues", $cookieFac1, $raceIssueBody1

$jobIssue2 = Start-Job -ScriptBlock {
    param($url, $cookieVal, $body)
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Cookie", "JSESSIONID=$cookieVal")
        $wc.Headers.Add("Content-Type", "application/json")
        $resp = $wc.UploadString($url, "POST", $body)
        return @{ StatusCode = 201; Response = $resp }
    } catch [System.Net.WebException] {
        return @{ StatusCode = [int]$_.Exception.Response.StatusCode; Error = $_.Exception.Message }
    }
} -ArgumentList "$baseUrl/api/issues", $cookieFac2, $raceIssueBody2

$resIssue1 = Receive-Job $jobIssue1 -Wait
$resIssue2 = Receive-Job $jobIssue2 -Wait
Remove-Job $jobIssue1, $jobIssue2

Write-Host "    Concurrent Issue Result 1: HTTP $($resIssue1.StatusCode)" -ForegroundColor Gray
Write-Host "    Concurrent Issue Result 2: HTTP $($resIssue2.StatusCode)" -ForegroundColor Gray

$issueStatusCodes = @($resIssue1.StatusCode, $resIssue2.StatusCode)
Assert-Condition ($issueStatusCodes -contains 201) "Race condition: Exactly one issue request succeeded with HTTP 201"
Assert-Condition ($issueStatusCodes -contains 409) "Race condition: Second concurrent request rejected with HTTP 409 Conflict"

# Verify asset status is now ISSUED
$checkRaceAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astRaceIssue" -Method Get -WebSession $adminSession
Assert-Condition ($checkRaceAsset.Data.data.status -eq "ISSUED") "Asset $astRaceIssue is cleanly in ISSUED status"

# ------------------------------------------------------------------------------
# 9. Concurrent Double-Return Prevention Race Test
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 9: Concurrent Double-Return Prevention Race Test --" -ForegroundColor Yellow
# First, issue $astRaceReturn cleanly so we have an active issue ID
$prepReturnBody = @{
    assetId = $astRaceReturn
    issuedToDepartment = "CSE"
    remarks = "Preparing for concurrent return race test"
} | ConvertTo-Json
$prepReturnRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $prepReturnBody -WebSession $fac1Session
$raceReturnIssueId = $prepReturnRes.Data.data.issueId
Assert-Condition ($raceReturnIssueId -ne $null) "Prepared active issue $raceReturnIssueId for return race"

$returnPayload = '{"conditionOnReturn":"GOOD","returnRemarks":"Concurrent return attempt"}'

# Launch two simultaneous background requests returning the exact same issue
$jobReturn1 = Start-Job -ScriptBlock {
    param($url, $cookieVal, $body)
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Cookie", "JSESSIONID=$cookieVal")
        $wc.Headers.Add("Content-Type", "application/json")
        $resp = $wc.UploadString($url, "PUT", $body)
        return @{ StatusCode = 200; Response = $resp }
    } catch [System.Net.WebException] {
        return @{ StatusCode = [int]$_.Exception.Response.StatusCode; Error = $_.Exception.Message }
    }
} -ArgumentList "$baseUrl/api/issues/$raceReturnIssueId/return", $cookieFac1, $returnPayload

$jobReturn2 = Start-Job -ScriptBlock {
    param($url, $cookieVal, $body)
    try {
        $wc = New-Object System.Net.WebClient
        $wc.Headers.Add("Cookie", "JSESSIONID=$cookieVal")
        $wc.Headers.Add("Content-Type", "application/json")
        $resp = $wc.UploadString($url, "PUT", $body)
        return @{ StatusCode = 200; Response = $resp }
    } catch [System.Net.WebException] {
        return @{ StatusCode = [int]$_.Exception.Response.StatusCode; Error = $_.Exception.Message }
    }
} -ArgumentList "$baseUrl/api/issues/$raceReturnIssueId/return", $cookieFac1, $returnPayload

$resReturn1 = Receive-Job $jobReturn1 -Wait
$resReturn2 = Receive-Job $jobReturn2 -Wait
Remove-Job $jobReturn1, $jobReturn2

Write-Host "    Concurrent Return Result 1: HTTP $($resReturn1.StatusCode)" -ForegroundColor Gray
Write-Host "    Concurrent Return Result 2: HTTP $($resReturn2.StatusCode)" -ForegroundColor Gray

$returnStatusCodes = @($resReturn1.StatusCode, $resReturn2.StatusCode)
Assert-Condition ($returnStatusCodes -contains 200) "Race condition: Exactly one return request succeeded with HTTP 200"
Assert-Condition ($returnStatusCodes -contains 409) "Race condition: Second concurrent return rejected with HTTP 409 Conflict"

# Verify asset status is now AVAILABLE
$checkReturnedAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astRaceReturn" -Method Get -WebSession $adminSession
Assert-Condition ($checkReturnedAsset.Data.data.status -eq "AVAILABLE") "Asset $astRaceReturn status transitioned back to AVAILABLE"

# ------------------------------------------------------------------------------
# 10. Return Condition Branching (GOOD -> AVAILABLE vs DAMAGED -> UNDER_MAINTENANCE)
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 10: Return Condition Branching --" -ForegroundColor Yellow
# Normal return of $issueId1 with condition GOOD
$retGoodBody = @{
    conditionOnReturn = "GOOD"
    returnRemarks = "Returned in excellent working condition"
} | ConvertTo-Json
$retGoodRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$issueId1/return" -Method Put -Body $retGoodBody -WebSession $fac1Session
Assert-Condition ($retGoodRes.StatusCode -eq 200) "Normal return with condition GOOD succeeded (HTTP 200)"
$checkGoodAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$ast1" -Method Get -WebSession $adminSession
Assert-Condition ($checkGoodAsset.Data.data.status -eq "AVAILABLE") "Asset $ast1 status transitioned to AVAILABLE"

# Issue $astCondDamaged and return with condition DAMAGED
$issueDamagedBody = @{
    assetId = $astCondDamaged
    issuedToDepartment = "CSE"
    remarks = "Field experiment usage"
} | ConvertTo-Json
$issueDamagedRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issueDamagedBody -WebSession $fac1Session
$damagedIssueId = $issueDamagedRes.Data.data.issueId
Assert-Condition ($damagedIssueId -ne $null) "Asset $astCondDamaged issued"

$retDamagedBody = @{
    conditionOnReturn = "DAMAGED"
    returnRemarks = "Screen cracked during field experiment"
} | ConvertTo-Json
$retDamagedRes = Invoke-ApiRequest -Uri "$baseUrl/api/issues/$damagedIssueId/return" -Method Put -Body $retDamagedBody -WebSession $fac1Session
Assert-Condition ($retDamagedRes.StatusCode -eq 200) "Return with condition DAMAGED succeeded (HTTP 200)"

# Verify asset status is UNDER_MAINTENANCE
$checkDamagedAsset = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$astCondDamaged" -Method Get -WebSession $adminSession
Assert-Condition ($checkDamagedAsset.Data.data.status -eq "UNDER_MAINTENANCE") "Asset $astCondDamaged branched to UNDER_MAINTENANCE on DAMAGED return"

# ------------------------------------------------------------------------------
# 11. Retire Protection on ISSUED Assets
# ------------------------------------------------------------------------------
Write-Host "`n-- Section 11: Retire Protection on ISSUED Assets --" -ForegroundColor Yellow
# Issue $ast3
$issue3Body = @{
    assetId = $ast3
    issuedToDepartment = "CSE"
} | ConvertTo-Json
$issue3Res = Invoke-ApiRequest -Uri "$baseUrl/api/issues" -Method Post -Body $issue3Body -WebSession $fac1Session
Assert-Condition (($issue3Res.StatusCode -eq 201 -or $issue3Res.StatusCode -eq 200) -and $issue3Res.Data.success -eq $true) "Asset $ast3 is currently ISSUED"

# Admin attempts to retire $ast3 while ISSUED -> must return 409
$retireRes = Invoke-ApiRequest -Uri "$baseUrl/api/assets/$ast3/retire" -Method Put -Body '{"reason":"Decommissioning"}' -WebSession $adminSession
Assert-Condition ($retireRes.StatusCode -eq 409) "Retiring an ISSUED asset is blocked with HTTP 409 Conflict"

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
