$ErrorActionPreference = 'Stop'
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location "$scriptDir\backend"

$envFile = "$scriptDir\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and ($line -match '^([^=]+)=(.*)$')) {
            $name = $matches[1].Trim()
            $value = $matches[2].Trim()
            [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

$jarPath = "$scriptDir\backend\target\instagram-reel-ai-0.0.1-SNAPSHOT.jar"
Write-Host "Starting Spring Boot application with profile 'local'..."
& java "-Dspring.profiles.active=local" "-Dspring.datasource.password=" "-Dspring.datasource.username=sa" -jar $jarPath
