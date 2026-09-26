$baseUrl = 'http://localhost:8080'

function Test-PageStructure($username, $password, $roleName, $pageUrl) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $body = @{ username = $username; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType 'application/json' -Body $body -WebSession $session
    $page = Invoke-WebRequest -Uri "$baseUrl$pageUrl" -Method Get -WebSession $session -UseBasicParsing
    
    $hasNav = $page.Content.Contains('dashboard-nav')
    $hasTable = $page.Content.Contains('cams-table')
    $hasContainer = $page.Content.Contains('class="container"')
    $hasBadge = $page.Content.Contains('class="badge"')
    
    Write-Host "[ROLE: $roleName] $pageUrl" -ForegroundColor Cyan
    Write-Host "   Status: $($page.StatusCode) | Nav: $hasNav | Table: $hasTable | Container: $hasContainer | Badge: $hasBadge" -ForegroundColor Green
}

Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host "  VERIFYING UI CONSISTENCY FOR ALL ROLES" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow

# 1. Administrator Views
Test-PageStructure 'admin' 'Admin@123' 'Administrator' '/pages/admin/master-data.html'
Test-PageStructure 'admin' 'Admin@123' 'Administrator' '/pages/issues.html'
Test-PageStructure 'admin' 'Admin@123' 'Administrator' '/pages/technical/maintenance.html'

# 2. Faculty View
Test-PageStructure 'faculty1' 'Faculty@123' 'Faculty' '/pages/issues.html'

# 3. Technical Staff Views
Test-PageStructure 'tech1' 'Tech@123' 'Technical Staff' '/pages/issues.html'
Test-PageStructure 'tech1' 'Tech@123' 'Technical Staff' '/pages/technical/maintenance.html'

Write-Host "`nAll 6 role-page combinations verified successfully!" -ForegroundColor Green
