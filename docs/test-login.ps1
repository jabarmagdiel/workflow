$loginUrl = "http://localhost/api/auth/login"
$body = @{
    email    = "manager@bpflow.com"
    password = "admin123"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri $loginUrl -Method Post -Body $body -ContentType "application/json"
    Write-Host "SUCCESS: Logged in as $($response.user.email)"
    Write-Host "Token: $($response.accessToken.Substring(0, 10))..."
}
catch {
    Write-Host "FAILED: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        Write-Host "Error Body: $errorBody"
    }
}
