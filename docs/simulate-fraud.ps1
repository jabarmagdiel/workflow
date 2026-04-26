$baseUrl = "http://localhost:8080/api"
$loginBody = @{ email = "admin@bpflow.com"; password = "Admin@1234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "$baseUrl/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$headers = @{ "Authorization" = "Bearer $($auth.accessToken)"; "Content-Type" = "application/json" }

$workflows = Invoke-RestMethod -Uri "$baseUrl/workflows" -Method Get -Headers $headers
$targetWf = $workflows | Where-Object { $_.name -like "*Hipotecario*" -and $_.status -eq "PUBLISHED" } | Select-Object -Last 1
$wfId = $targetWf.id

Write-Host "Iniciando instancia..."
$startBody = @{ workflowId = $wfId } | ConvertTo-Json
$instance = Invoke-RestMethod -Uri "$baseUrl/instances/start" -Method Post -Body $startBody -Headers $headers
$instanceId = $instance.id

Start-Sleep -Seconds 1
$tasks = Invoke-RestMethod -Uri "$baseUrl/tasks/my" -Method Get -Headers $headers
$targetTask = ($tasks | Where-Object { $_.instanceId -eq $instanceId })[0]
$taskId = $targetTask.id

# MARCAR INICIO DE TAREA (Para que el cronometro empiece)
Invoke-RestMethod -Uri "$baseUrl/tasks/$taskId/start" -Method Post -Headers $headers
Write-Host "Tarea iniciada. Esperando 100ms para simular 'Super-Flash'..."
Start-Sleep -m 100

# COMPLETAR TAREA
$completeBody = @{ action = "APPROVE"; formData = @{ aprobado = $true } } | ConvertTo-Json
Invoke-RestMethod -Uri "$baseUrl/tasks/$taskId/complete" -Method Post -Body $completeBody -Headers $headers
Write-Host "Tarea completada en tiempo record."
