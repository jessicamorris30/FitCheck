<#
.SYNOPSIS
  Builds the FitCheck Docker image and runs it on your computer, exactly as Azure will run it
  (production profile, one container serving website + API), at http://localhost:8080.

.DESCRIPTION
  Uses the local docker compose services (Postgres, Azurite, Mailpit) in place of the Azure ones.
  Run "docker compose up -d" first. The values passed here are LOCAL TEST values only; on Azure the
  same settings come from Container Apps secrets (step 5.8).

.EXAMPLE
  .\scripts\run-prod-local.ps1            # build + run
  .\scripts\run-prod-local.ps1 -NoBuild   # run the last built image
#>
param([switch]$NoBuild)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

if (-not $NoBuild) {
    Write-Host "Building image 'fitcheck' (first build takes a few minutes)..."
    docker build -t fitcheck .
    if ($LASTEXITCODE -ne 0) { throw "docker build failed" }
}

# A fresh random JWT key for this local run (never reuse the dev key in prod mode).
$jwt = [Convert]::ToBase64String((1..48 | ForEach-Object { [byte](Get-Random -Maximum 256) }))

# Inside a container, "localhost" is the container itself. host.docker.internal is Docker
# Desktop's name for YOUR computer, where docker compose exposes Postgres/Azurite/Mailpit.
$azurite = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;" +
    "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;" +
    "BlobEndpoint=http://host.docker.internal:10000/devstoreaccount1;"

# Pass the Claude key through only if it's in .env (it stays out of the image either way).
$anthropic = ""
if (Test-Path .env) {
    $line = Select-String -Path .env -Pattern '^ANTHROPIC_API_KEY=(.+)$' | Select-Object -First 1
    if ($line) { $anthropic = $line.Matches[0].Groups[1].Value }
}

# The settings go into a temporary "env file" that Docker reads directly. (Passing them as
# command-line arguments breaks values with quotes or semicolons in Windows PowerShell.)
$envFile = Join-Path $env:TEMP "fitcheck-prod-local.env"
@(
    "DB_URL=jdbc:postgresql://host.docker.internal:5432/fitcheck"
    "POSTGRES_USER=fitcheck"
    "POSTGRES_PASSWORD=fitcheck"
    "JWT_SECRET=$jwt"
    "AZURE_STORAGE_CONNECTION_STRING=$azurite"
    # The app reaches Azurite at host.docker.internal, but your browser reaches it at localhost.
    "AZURE_STORAGE_PUBLIC_ENDPOINT=http://localhost:10000/devstoreaccount1"
    "MAIL_HOST=host.docker.internal"
    "MAIL_PORT=1025"
    "MAIL_USERNAME=local"
    "MAIL_PASSWORD=local"
    "MAIL_FROM=FitCheck <no-reply@fitcheck.local>"
    "FRONTEND_URL=http://localhost:8080"
    "CORS_ALLOWED_ORIGINS=http://localhost:8080"
    # Mailpit has no TLS or login, unlike a real email provider: relax those for this local run only.
    'SPRING_APPLICATION_JSON={"spring":{"mail":{"properties":{"mail.smtp.auth":false,"mail.smtp.starttls.enable":false,"mail.smtp.starttls.required":false}}}}'
    "ANTHROPIC_API_KEY=$anthropic"
) | Set-Content -Encoding ascii $envFile

# Remove a container left over from a previous run (only if there is one).
if (docker ps -aq --filter "name=^fitcheck-prod-local$") { docker rm -f fitcheck-prod-local | Out-Null }
try {
    docker run -d --name fitcheck-prod-local -p 8080:8080 --env-file $envFile fitcheck | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "docker run failed" }
} finally {
    Remove-Item $envFile -ErrorAction SilentlyContinue # it contains the JWT key and maybe the Claude key
}

Write-Host "Started container 'fitcheck-prod-local'. Waiting for it to be ready..."
for ($i = 0; $i -lt 60; $i++) {
    if ((docker inspect -f "{{.State.Running}}" fitcheck-prod-local) -ne "true") {
        Write-Host "The container stopped. Last log lines:"; docker logs --tail 20 fitcheck-prod-local; exit 1
    }
    try { if ((Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health/readiness -TimeoutSec 2).StatusCode -eq 200) { break } } catch {}
    Start-Sleep 2
}
Write-Host "Open http://localhost:8080   (logs: docker logs -f fitcheck-prod-local   stop: docker rm -f fitcheck-prod-local)"
