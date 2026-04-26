$baseUrl = "http://localhost:8080/api"
$loginBody = @{ email = "admin@bpflow.com"; password = "Admin@1234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ "Authorization" = "Bearer $($auth.accessToken)" }

Write-Host "---"
Write-Host "Consultando estado del sistema..." -ForegroundColor Cyan

$alerts = Invoke-RestMethod -Uri "$baseUrl/fraud/alerts" -Method Get -Headers $headers
if ($alerts) {
    Write-Host "Alertas de Fraude Detectadas:" -ForegroundColor Red
    $alerts | Format-Table id, alertType, description, riskScore -AutoSize
}
else {
    Write-Host "No hay alertas de fraude activas." -ForegroundColor Green
}

Write-Host "Resumen de Actividad:" -ForegroundColor Yellow
$stats = Invoke-RestMethod -Uri "$baseUrl/analytics/dashboard" -Method Get -Headers $headers
$stats.instances | ConvertTo-Json
