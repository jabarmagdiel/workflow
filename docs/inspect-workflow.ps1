$baseUrl = "http://localhost:8080/api"
$loginBody = @{ email = "admin@bpflow.com"; password = "Admin@1234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ "Authorization" = "Bearer $($auth.accessToken)" }

$wfs = Invoke-RestMethod -Uri "$baseUrl/workflows" -Method Get -Headers $headers
$lastWf = $wfs[-1]

Write-Host "Inspeccionando Workflow: $($lastWf.name)" -ForegroundColor Cyan
Write-Host "Status Actual: $($lastWf.status)"

foreach ($node in $lastWf.nodes) {
    Write-Host "Nodo: $($node.label) - Type: $($node.type) - Start: $($node.startNode) - End: $($node.endNode)"
}
