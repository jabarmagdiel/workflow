$baseUrl = "http://localhost:8080/api"
$loginBody = @{ email = "admin@bpflow.com"; password = "Admin@1234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ "Authorization" = "Bearer $($auth.accessToken)" }

Write-Host "DEBUG START"

$wfs = Invoke-RestMethod -Uri "$baseUrl/workflows" -Method Get -Headers $headers
Write-Host "Workflows: " $wfs.Count

$ins = Invoke-RestMethod -Uri "$baseUrl/instances" -Method Get -Headers $headers
Write-Host "Instances: " $ins.Count

$tasks = Invoke-RestMethod -Uri "$baseUrl/tasks/my" -Method Get -Headers $headers
Write-Host "Tasks: " $tasks.Count

Write-Host "DEBUG END"
