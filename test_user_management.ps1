# ==============================================================
# CAMS MODULE 9: USER MANAGEMENT TEST SUITE
# ==============================================================
$baseUrl = "http://localhost:8080"
$passCount = 0
$failCount = 0

function Assert-Test([string]$testName, [bool]$condition, [string]$details = "") {
    if ($condition) {
        Write-Host "  [PASS] $testName" -ForegroundColor Green
        $script:passCount++
    } else {
        Write-Host "  [FAIL] $testName" -ForegroundColor Red
        if ($details) { Write-Host "         $details" -ForegroundColor DarkRed }
        $script:failCount++
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "  Starting CAMS Module 9: User Management Test Suite      " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

# -- Step 1: Authentication & Role Sessions --
Write-Host "`n-- Step 1: Authentication & Role Sessions --" -ForegroundColor Yellow

$adminSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$adminLoginBody = @{ username = "admin"; password = "Admin@123" } | ConvertTo-Json
$adminRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $adminLoginBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "Admin authenticated" ($adminRes.success -eq $true -and $adminRes.data.user.role -eq "Administrator")
$currentAdminId = $adminRes.data.user.userId

$facultySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$facultyLoginBody = @{ username = "faculty1"; password = "Faculty@123" } | ConvertTo-Json
$facultyRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $facultyLoginBody -ContentType "application/json" -WebSession $facultySession
Assert-Test "Faculty authenticated" ($facultyRes.success -eq $true)

$techSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$techLoginBody = @{ username = "tech1"; password = "Tech@123" } | ConvertTo-Json
$techRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $techLoginBody -ContentType "application/json" -WebSession $techSession
Assert-Test "Technical Staff authenticated" ($techRes.success -eq $true)

# -- Step 2: RBAC Policy Enforcement (Admin-Only API) --
Write-Host "`n-- Step 2: RBAC Policy Enforcement (Admin-Only) --" -ForegroundColor Yellow

# Faculty GET /api/users
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users" -Method Get -WebSession $facultySession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Faculty GET /api/users blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Faculty GET /api/users blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Faculty POST /api/users
try {
    $dummy = @{ name = "Test"; username = "dummy"; role = "Faculty"; department = "CSE"; password = "Password@123" } | ConvertTo-Json
    Invoke-WebRequest -Uri "$baseUrl/api/users" -Method Post -Body $dummy -ContentType "application/json" -WebSession $facultySession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Faculty POST /api/users blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Faculty POST /api/users blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Faculty PUT /api/users/1/deactivate
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users/1/deactivate" -Method Put -WebSession $facultySession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Faculty PUT /api/users/1/deactivate blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Faculty PUT /api/users/1/deactivate blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Faculty PUT /api/users/1/reset-password
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users/1/reset-password" -Method Put -WebSession $facultySession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Faculty PUT /api/users/1/reset-password blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Faculty PUT /api/users/1/reset-password blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Technical Staff GET /api/users
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users" -Method Get -WebSession $techSession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Technical Staff GET /api/users blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Technical Staff GET /api/users blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Technical Staff POST /api/users
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users" -Method Post -Body $dummy -ContentType "application/json" -WebSession $techSession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Technical Staff POST /api/users blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Technical Staff POST /api/users blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Technical Staff PUT /api/users/1/deactivate
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users/1/deactivate" -Method Put -WebSession $techSession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Technical Staff PUT /api/users/1/deactivate blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Technical Staff PUT /api/users/1/deactivate blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# Technical Staff PUT /api/users/1/reset-password
try {
    Invoke-WebRequest -Uri "$baseUrl/api/users/1/reset-password" -Method Put -WebSession $techSession -UseBasicParsing -ErrorAction Stop
    Assert-Test "Technical Staff PUT /api/users/1/reset-password blocked with 403" $false "Expected 403"
} catch {
    Assert-Test "Technical Staff PUT /api/users/1/reset-password blocked with 403" ($_.Exception.Response.StatusCode.value__ -eq 403)
}

# -- Step 3: Account Creation Across All 3 Roles --
Write-Host "`n-- Step 3: Account Creation Across All 3 Roles --" -ForegroundColor Yellow

$randSuffix = Get-Random -Minimum 1000 -Maximum 9999
$facultyUsername = "cbabbage_$randSuffix"
$techUsername = "alovelace_$randSuffix"
$adminUsername = "ghopper_$randSuffix"

# Create Faculty
$facBody = @{
    name = "Prof. Charles Babbage"
    username = $facultyUsername
    role = "Faculty"
    department = "Computer Science"
    password = "FacultyPassword@123"
} | ConvertTo-Json

$facCreateRes = Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $facBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "Faculty user created successfully (HTTP 201)" ($facCreateRes.success -eq $true -and $facCreateRes.data.userId -gt 0)
Assert-Test "Created user role is 'Faculty'" ($facCreateRes.data.role -eq "Faculty")
Assert-Test "Created user active status is 'Y'" ($facCreateRes.data.active -eq "Y")
Assert-Test "Password hash is strictly omitted in response" ($null -eq $facCreateRes.data.password)
$testFacultyUserId = $facCreateRes.data.userId

# Create Technical Staff
$techBody = @{
    name = "Ada Lovelace"
    username = $techUsername
    role = "Technical Staff"
    department = "Hardware & Maintenance"
    password = "TechPassword@123"
} | ConvertTo-Json

$techCreateRes = Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $techBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "Technical Staff user created successfully (HTTP 201)" ($techCreateRes.success -eq $true)
Assert-Test "Created user role is 'Technical Staff'" ($techCreateRes.data.role -eq "Technical Staff")
$testTechUserId = $techCreateRes.data.userId

# Create Administrator
$admBody = @{
    name = "Grace Hopper"
    username = $adminUsername
    role = "Administrator"
    department = "IT Infrastructure"
    password = "AdminPassword@123"
} | ConvertTo-Json

$admCreateRes = Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $admBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "Administrator user created successfully (HTTP 201)" ($admCreateRes.success -eq $true)
Assert-Test "Created user role is 'Administrator'" ($admCreateRes.data.role -eq "Administrator")
$testAdminUserId = $admCreateRes.data.userId

# -- Step 4: Duplicate Username Rejection --
Write-Host "`n-- Step 4: Duplicate Username Rejection --" -ForegroundColor Yellow

# Exact duplicate
try {
    Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $facBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Exact duplicate username rejected with 409 Conflict" $false "Expected 409"
} catch {
    Assert-Test "Exact duplicate username rejected with 409 Conflict" ($_.Exception.Response.StatusCode.value__ -eq 409)
}

# Case-variant duplicate (e.g. UPPERCASE)
$upperBody = @{
    name = "Charles Babbage Duplicate"
    username = $facultyUsername.ToUpper()
    role = "Faculty"
    department = "Computer Science"
    password = "FacultyPassword@123"
} | ConvertTo-Json

try {
    Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $upperBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Case-variant duplicate username rejected with 409 Conflict" $false "Expected 409"
} catch {
    Assert-Test "Case-variant duplicate username rejected with 409 Conflict" ($_.Exception.Response.StatusCode.value__ -eq 409)
}

# -- Step 5: Input Validation & Constraints --
Write-Host "`n-- Step 5: Input Validation & Constraints --" -ForegroundColor Yellow

# Blank name
try {
    $badBody = @{ name = ""; username = "validuser_$randSuffix"; role = "Faculty"; department = "CSE"; password = "Password@123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $badBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Blank full name rejected with 400 Bad Request" $false
} catch {
    Assert-Test "Blank full name rejected with 400 Bad Request" ($_.Exception.Response.StatusCode.value__ -eq 400)
}

# Invalid role
try {
    $badRoleBody = @{ name = "Test User"; username = "badrole_$randSuffix"; role = "Superuser"; department = "CSE"; password = "Password@123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $badRoleBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Invalid role rejected with 400 Bad Request" $false
} catch {
    Assert-Test "Invalid role rejected with 400 Bad Request" ($_.Exception.Response.StatusCode.value__ -eq 400)
}

# Short password (< 6 chars)
try {
    $shortPassBody = @{ name = "Test User"; username = "shortpass_$randSuffix"; role = "Faculty"; department = "CSE"; password = "123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/users" -Method Post -Body $shortPassBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Short password (< 6 chars) rejected with 400 Bad Request" $false
} catch {
    Assert-Test "Short password (< 6 chars) rejected with 400 Bad Request" ($_.Exception.Response.StatusCode.value__ -eq 400)
}

# -- Step 6: Search & Filter Verification --
Write-Host "`n-- Step 6: Search & Filter Verification --" -ForegroundColor Yellow

# Search by username substring
$searchRes = Invoke-RestMethod -Uri "$baseUrl/api/users?search=$facultyUsername" -Method Get -WebSession $adminSession
Assert-Test "Search by username substring returns matching user" ($searchRes.success -eq $true -and $searchRes.data.users.Count -ge 1)
Assert-Test "Search result matches expected username" ($searchRes.data.users[0].username -eq $facultyUsername)

# Filter by role = Technical Staff
$techFilterRes = Invoke-RestMethod -Uri "$baseUrl/api/users?role=Technical+Staff" -Method Get -WebSession $adminSession
$allTech = ($techFilterRes.data.users | Where-Object { $_.role -ne "Technical Staff" }).Count -eq 0
Assert-Test "Filter by role='Technical Staff' returns only Technical Staff" ($techFilterRes.success -eq $true -and $allTech)

# Filter by active = Y
$activeFilterRes = Invoke-RestMethod -Uri "$baseUrl/api/users?active=Y" -Method Get -WebSession $adminSession
$allActive = ($activeFilterRes.data.users | Where-Object { $_.active -ne "Y" }).Count -eq 0
Assert-Test "Filter by active='Y' returns only active users" ($activeFilterRes.success -eq $true -and $allActive)

# -- Step 7: Single User Lookup & Profile Updates --
Write-Host "`n-- Step 7: Single User Lookup & Profile Updates --" -ForegroundColor Yellow

$singleUser = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId" -Method Get -WebSession $adminSession
Assert-Test "GET /api/users/{id} retrieves user profile" ($singleUser.success -eq $true -and $singleUser.data.user.userId -eq $testFacultyUserId)

# Update name and department
$updateBody = @{
    name = "Prof. Sir Charles Babbage FRS"
    role = "Faculty"
    department = "Applied Mathematics & CS"
} | ConvertTo-Json
$updateRes = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId" -Method Put -Body $updateBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "PUT /api/users/{id} updates profile successfully" ($updateRes.success -eq $true -and $updateRes.data.name -eq "Prof. Sir Charles Babbage FRS")

# Non-existent user update returns 404
try {
    Invoke-RestMethod -Uri "$baseUrl/api/users/999999" -Method Put -Body $updateBody -ContentType "application/json" -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Non-existent user update returns 404" $false
} catch {
    Assert-Test "Non-existent user update returns 404" ($_.Exception.Response.StatusCode.value__ -eq 404)
}

# -- Step 8: Self-Deactivation Protection --
Write-Host "`n-- Step 8: Self-Deactivation Protection --" -ForegroundColor Yellow

try {
    Invoke-RestMethod -Uri "$baseUrl/api/users/$currentAdminId/deactivate" -Method Put -WebSession $adminSession -ErrorAction Stop
    Assert-Test "Admin deactivating self blocked with 409 Conflict" $false "Self-deactivation should be blocked"
} catch {
    Assert-Test "Admin deactivating self blocked with 409 Conflict" ($_.Exception.Response.StatusCode.value__ -eq 409)
}

# -- Step 9: Soft-Delete Deactivation --
Write-Host "`n-- Step 9: Soft-Delete Deactivation --" -ForegroundColor Yellow

$deactRes = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId/deactivate" -Method Put -WebSession $adminSession
Assert-Test "Admin deactivated target user (HTTP 200)" ($deactRes.success -eq $true)

$deactCheck = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId" -Method Get -WebSession $adminSession
Assert-Test "Target user active status is now 'N'" ($deactCheck.data.user.active -eq "N")

# -- Step 10: Module 1 Cross-Verification: Deactivated User Cannot Log In --
Write-Host "`n-- Step 10: Module 1 Cross-Verification: Deactivated Account Login Blocked --" -ForegroundColor Yellow

try {
    $deactLoginBody = @{ username = $facultyUsername; password = "FacultyPassword@123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $deactLoginBody -ContentType "application/json" -ErrorAction Stop
    Assert-Test "Deactivated user login rejected with 401 Unauthorized" $false "Deactivated account was allowed to log in!"
} catch {
    $loginErr = $_.Exception.Response.StatusCode.value__ -eq 401
    Assert-Test "Deactivated user login rejected with 401 Unauthorized" $loginErr
}

# -- Step 11: Account Reactivation --
Write-Host "`n-- Step 11: Account Reactivation --" -ForegroundColor Yellow

$reactRes = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId/activate" -Method Put -WebSession $adminSession
Assert-Test "Admin reactivated target user (HTTP 200)" ($reactRes.success -eq $true)

$reactLoginBody = @{ username = $facultyUsername; password = "FacultyPassword@123" } | ConvertTo-Json
$reactLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $reactLoginBody -ContentType "application/json"
Assert-Test "Reactivated user can log in successfully" ($reactLoginRes.success -eq $true)

# -- Step 12: Password Reset Flow & must_change_password Mitigation --
Write-Host "`n-- Step 12: Password Reset Flow & must_change_password Mitigation --" -ForegroundColor Yellow

# Custom temporary password reset
$resetCustomBody = @{ temporaryPassword = "CustomTemp@999" } | ConvertTo-Json
$resetRes1 = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId/reset-password" -Method Put -Body $resetCustomBody -ContentType "application/json" -WebSession $adminSession
Assert-Test "Password reset with custom temporary password succeeded (HTTP 200)" ($resetRes1.success -eq $true)
Assert-Test "Temporary password returned in response" ($resetRes1.data.temporaryPassword -eq "CustomTemp@999")
Assert-Test "mustChangePassword flag is true in reset response" ($resetRes1.data.mustChangePassword -eq $true)

# Old password no longer works
try {
    $oldPassBody = @{ username = $facultyUsername; password = "FacultyPassword@123" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $oldPassBody -ContentType "application/json" -ErrorAction Stop
    Assert-Test "Old password rejected with 401 after reset" $false
} catch {
    Assert-Test "Old password rejected with 401 after reset" ($_.Exception.Response.StatusCode.value__ -eq 401)
}

# User logs in with temporary password -> Login succeeds and flags mustChangePassword
$tempPassLoginBody = @{ username = $facultyUsername; password = "CustomTemp@999" } | ConvertTo-Json
$tempLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $tempPassLoginBody -ContentType "application/json"
Assert-Test "User logs in with temporary password successfully" ($tempLoginRes.success -eq $true)
Assert-Test "Login response signals mustChangePassword is required" ($tempLoginRes.data.mustChangePassword -eq $true -or $tempLoginRes.data.user.mustChangePassword -eq "Y")

# Auto-generated temporary password reset (no custom body provided)
$resetRes2 = Invoke-RestMethod -Uri "$baseUrl/api/users/$testFacultyUserId/reset-password" -Method Put -Body "{}" -ContentType "application/json" -WebSession $adminSession
Assert-Test "Password reset with auto-generated temporary password succeeded" ($resetRes2.success -eq $true -and $resetRes2.data.temporaryPassword.Length -ge 8)
$autoGenPass = $resetRes2.data.temporaryPassword

# Login with auto-generated password
$autoGenLoginBody = @{ username = $facultyUsername; password = $autoGenPass } | ConvertTo-Json
$autoGenLoginRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $autoGenLoginBody -ContentType "application/json"
Assert-Test "User logs in with auto-generated temporary password successfully" ($autoGenLoginRes.success -eq $true)

# -- Step 13: UI Page Availability --
Write-Host "`n-- Step 13: UI Page Availability --" -ForegroundColor Yellow

$uiRes = Invoke-WebRequest -Uri "$baseUrl/pages/admin/users.html" -Method Get -WebSession $adminSession -UseBasicParsing
Assert-Test "Administrator can access /pages/admin/users.html (HTTP 200)" ($uiRes.StatusCode -eq 200)

Write-Host "`n==========================================================" -ForegroundColor Cyan
Write-Host "  MODULE 9 TEST RESULTS: $passCount PASSED, $failCount FAILED   " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if ($failCount -gt 0) {
    exit 1
} else {
    exit 0
}
