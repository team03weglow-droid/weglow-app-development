# Creates confirmed test users only in the disposable WeGlow staging project.
# Credentials are prompted locally and are never written to a file.
$ErrorActionPreference = 'Stop'
$stagingUrl = 'https://tkljmhlrmutcsciqimlq.supabase.co'
$emails = @('kanakarathneasher@gmail.com', 'ashereduc@gmail.com')

$secureKey = Read-Host 'WEGLOW-STAGING secret or service_role key' -AsSecureString
$securePassword = Read-Host 'Shared password for the two disposable test accounts' -AsSecureString

try {
    $adminKey = [System.Net.NetworkCredential]::new('', $secureKey).Password
    $testPassword = [System.Net.NetworkCredential]::new('', $securePassword).Password
    if ([string]::IsNullOrWhiteSpace($adminKey) -or [string]::IsNullOrWhiteSpace($testPassword)) {
        throw 'Both a staging admin key and test password are required.'
    }

    $headers = @{ apikey = $adminKey }
    # Legacy service_role keys are JWTs; modern secret keys use apikey only.
    if ($adminKey -match '^eyJ') {
        $headers.Authorization = "Bearer $adminKey"
    }

    foreach ($email in $emails) {
        $body = @{
            email = $email
            password = $testPassword
            email_confirm = $true
        } | ConvertTo-Json

        try {
            $created = Invoke-RestMethod -Method Post `
                -Uri "$stagingUrl/auth/v1/admin/users" `
                -Headers $headers -ContentType 'application/json' `
                -Body $body -TimeoutSec 30
        }
        catch {
            # Do not print request headers, passwords or response tokens.
            $status = 'unavailable'
            if ($_.Exception.Response) {
                $status = [int]$_.Exception.Response.StatusCode
            }
            throw "Creation failed for $email (HTTP $status). Check staging Authentication > Users before retrying. Existing accounts are never reset by this script."
        }

        if (-not $created.id -or $created.email -ne $email) {
            throw "Unexpected creation response for $email. Inspect staging Authentication > Users."
        }
        Write-Host "Created: $email (ID: $($created.id))"
    }
}
finally {
    Remove-Variable adminKey, testPassword, headers, body, created -ErrorAction SilentlyContinue
    if ($secureKey) { $secureKey.Dispose() }
    if ($securePassword) { $securePassword.Dispose() }
    Remove-Variable secureKey, securePassword -ErrorAction SilentlyContinue
}
