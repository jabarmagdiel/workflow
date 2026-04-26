$baseUrl = "http://localhost:8080/api"
$loginUrl = "$baseUrl/auth/login"
$workflowUrl = "$baseUrl/workflows"

# 1. Login
$loginBody = @{ email = "admin@bpflow.com"; password = "Admin@1234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri $loginUrl -Method Post -Body $loginBody -ContentType "application/json"
$token = $auth.accessToken

$headers = @{ "Authorization" = "Bearer $token"; "Content-Type" = "application/json" }

# 2. Crear Workflow
$workflow = @{
    name        = "Aprobación de Crédito Hipotecario"
    description = "Proceso estándar para evaluación y firma de créditos para vivienda."
    category    = "Finanzas"
    nodes       = @(
        @{ id = "start-1"; label = "Inicio"; type = "START"; startNode = $true; x = 100; y = 100 },
        @{ id = "node-risk"; label = "Evaluación de Riesgo"; type = "TASK"; assignedRole = "ADMIN"; slaHours = 24; x = 300; y = 100 },
        @{ id = "node-legal"; label = "Aprobación Legal"; type = "TASK"; assignedRole = "MANAGER"; slaHours = 48; x = 500; y = 100 },
        @{ id = "end-1"; label = "Crédito Otorgado"; type = "END"; endNode = $true; x = 750; y = 100 }
    )
    edges       = @(
        @{ sourceNodeId = "start-1"; targetNodeId = "node-risk"; type = "SEQUENCE" },
        @{ sourceNodeId = "node-risk"; targetNodeId = "node-legal"; type = "SEQUENCE"; condition = "approved" },
        @{ sourceNodeId = "node-legal"; targetNodeId = "end-1"; type = "SEQUENCE"; condition = "approved" }
    )
} | ConvertTo-Json -Depth 10

$res = Invoke-RestMethod -Uri $workflowUrl -Method Post -Body $workflow -Headers $headers
Write-Host "✅ Workflow creado: $($res.name)" -ForegroundColor Green

# 3. Publicar
Invoke-RestMethod -Uri "$workflowUrl/$($res.id)/publish" -Method Post -Headers $headers
Write-Host "🚀 Workflow publicado y listo para ejecución." -ForegroundColor Cyan
