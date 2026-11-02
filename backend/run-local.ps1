$securePassword = Read-Host "Password dell'utente PostgreSQL postgres" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$previousJwtSecret = $env:JWT_SECRET

try {
    if (-not $env:JWT_SECRET) {
        $jwtBytes = New-Object byte[] 32
        $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($jwtBytes) } finally { $rng.Dispose() }
        $env:JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
        Write-Host 'Chiave JWT temporanea generata: al riavvio sarà necessario accedere di nuovo.'
    }
    $env:DB_URL = "jdbc:postgresql://localhost:5432/nutritional_expert"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    Push-Location $PSScriptRoot
    try { .\mvnw.cmd spring-boot:run } finally { Pop-Location }
}
finally {
    $env:JWT_SECRET = $previousJwtSecret
    $env:DB_PASSWORD = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
