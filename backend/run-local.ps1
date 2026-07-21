$securePassword = Read-Host "Password dell'utente PostgreSQL postgres" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)

try {
    $env:DB_URL = "jdbc:postgresql://localhost:5432/nutritional_expert"
    $env:DB_USERNAME = "postgres"
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)

    .\mvnw.cmd spring-boot:run
}
finally {
    $env:DB_PASSWORD = $null
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}
