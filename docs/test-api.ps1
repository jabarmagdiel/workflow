$apiUrl = "http://localhost:8080/api/auth/login"
$loginBody = @{
    email = "admin@bpflow.com"
    password = "Admin@1234"
} | ConvertTo-Json

Write-Host "Revisando disponibilidad del sistema..." -ForegroundColor Cyan

do {
    try {
        $response = Invoke-RestMethod -Uri $apiUrl -Method Post -Body $loginBody -ContentType "application/json" -ErrorAction Stop
        Write-Host "✅ ¡Sistema Online! Autenticado como Admin." -ForegroundColor Green
        Write-Host "Token: $($response.accessToken.Substring(0, 10))..."
        break
    } catch {
        Write-Host "Esperando a que el backend inicie... (Reintentando en 5s)" -ForegroundColor Yellow
        Start-Sleep -Seconds 5
    }
} while ($true)
