$ErrorActionPreference = 'Stop'

$projectDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$secretDirectory = [System.IO.Path]::GetFullPath((Join-Path $projectDirectory 'secrets'))
$secretPath = [System.IO.Path]::GetFullPath((Join-Path $secretDirectory 'security.monitoring.password'))

if (-not $secretPath.StartsWith($projectDirectory + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Secret path must stay inside the project directory'
}

if (Test-Path -LiteralPath $secretPath) {
    Write-Output 'Monitoring secret already exists; it was preserved.'
    exit 0
}

$secretBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()

try {
    $generator.GetBytes($secretBytes)
} finally {
    $generator.Dispose()
}

[System.IO.Directory]::CreateDirectory($secretDirectory) | Out-Null
$encoding = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($secretPath, [Convert]::ToBase64String($secretBytes), $encoding)
Write-Output 'Created secrets/security.monitoring.password; its value was not printed.'
