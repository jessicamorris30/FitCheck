<#
.SYNOPSIS
  Deploys (or updates) the whole FitCheck setup in Azure, including the app container.

.DESCRIPTION
  Runs infra/main.bicep with every setting the app needs. You type two secrets, hidden:
    - the database password (from your password manager)
    - the Claude API key of the "FitCheck prod" workspace
  The script makes the rest itself:
    - a new JWT signing key (people stay signed in: their refresh tokens don't depend on it)
    - a new SMTP password for the "FitCheck SMTP sender" app (the old one stops working)
  Secrets go to Azure through a temporary file that is deleted right after; nothing is printed.

.EXAMPLE
  .\infra\deploy-app.ps1                    # deploy the image of the latest commit on GitHub
  .\infra\deploy-app.ps1 -ImageTag 1fffe6a… # deploy a specific commit's image (e.g. to roll back)
#>
param([string]$ImageTag)

$ErrorActionPreference = "Continue" # az prints warnings on stderr; exit codes are checked instead
Set-Location (Split-Path $PSScriptRoot -Parent)

$resourceGroup = "rg-fitcheck"
$domain = "fitcheckwardrobe.com"
$owner = "jessicamorris30"
$image = "ghcr.io/$owner/fitcheck"

function Stop-WithError($message) { Write-Host "ERROR: $message" -ForegroundColor Red; exit 1 }

function Read-Secret($prompt) {
    # Hidden input. Tip: if Ctrl+V doesn't paste here, RIGHT-CLICK in the window to paste.
    $secure = Read-Host "$prompt (hidden; right-click to paste)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)).Trim() } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function New-RandomBase64($bytes) {
    $buffer = [byte[]]::new($bytes)
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    [Convert]::ToBase64String($buffer)
}

# ---- 1. Which image? Default: the newest commit on GitHub's main branch. ----
if (-not $ImageTag) {
    git fetch -q origin main
    $ImageTag = git rev-parse origin/main
}
Write-Host "Image: ${image}:$ImageTag"
try {
    $token = (Invoke-RestMethod "https://ghcr.io/token?scope=repository:$owner/fitcheck:pull").token
    $accept = "application/vnd.oci.image.index.v1+json, application/vnd.oci.image.manifest.v1+json, application/vnd.docker.distribution.manifest.v2+json"
    Invoke-WebRequest -UseBasicParsing -Method Head "https://ghcr.io/v2/$owner/fitcheck/manifests/$ImageTag" `
        -Headers @{ Authorization = "Bearer $token"; Accept = $accept } | Out-Null
} catch {
    Stop-WithError "That image isn't published (yet). Wait until the GitHub Actions run for this commit is green, or pass -ImageTag <commit id>."
}

# ---- 2. Secrets you type (hidden) ----
$dbPassword = Read-Secret "Database password (from your password manager)"
$claudeKey = Read-Secret "Claude API key (FitCheck prod workspace)"
if (-not $dbPassword) { Stop-WithError "Nothing was received for the database password. Try right-click to paste." }
if (-not $claudeKey) { Stop-WithError "Nothing was received for the Claude key. Try right-click to paste." }
if (-not $claudeKey.StartsWith("sk-ant-")) {
    # Show only harmless details (length + first 3 characters) to help spot a bad paste.
    Write-Host "The Claude key received is $($claudeKey.Length) characters and starts with '$($claudeKey.Substring(0, [Math]::Min(3, $claudeKey.Length)))...'." -ForegroundColor Yellow
    Write-Host "Claude keys normally start with 'sk-ant-' and are about 100 characters." -ForegroundColor Yellow
    if ((Read-Host "Use it anyway? (y/n)") -ne "y") { exit 1 }
}

# ---- 3. Secrets made here ----
$jwtSecret = New-RandomBase64 48

$appId = az ad app list --display-name "FitCheck SMTP sender" --query "[0].appId" -o tsv --only-show-errors
if (-not $appId) { Stop-WithError "Run .\infra\setup-email-sender.ps1 first." }
$tenantId = az account show --query tenantId -o tsv
$acsName = az resource list -g $resourceGroup --resource-type "Microsoft.Communication/communicationServices" --query "[0].name" -o tsv
$smtpUsername = "$acsName.$appId.$tenantId"
$smtpPassword = az ad app credential reset --id $appId --display-name "smtp" --years 1 --query password -o tsv --only-show-errors
if ($LASTEXITCODE -ne 0 -or -not $smtpPassword) { Stop-WithError "Couldn't create the SMTP password." }

# ---- 4. Deploy ----
$parameters = @{
    '$schema'      = "https://schema.management.azure.com/schemas/2019-04-01/deploymentParameters.json#"
    contentVersion = "1.0.0.0"
    parameters     = @{
        postgresAdminPassword = @{ value = $dbPassword }
        emailDomain           = @{ value = $domain }
        emailDomainVerified   = @{ value = $true }
        appImage              = @{ value = "${image}:$ImageTag" }
        siteUrl               = @{ value = "https://$domain" }
        jwtSecret             = @{ value = $jwtSecret }
        anthropicApiKey       = @{ value = $claudeKey }
        smtpUsername          = @{ value = $smtpUsername }
        smtpPassword          = @{ value = $smtpPassword }
    }
}
$parameterFile = Join-Path $env:TEMP "fitcheck-deploy-$([guid]::NewGuid()).json"
try {
    # UTF-8 without a byte-order mark (Windows PowerShell's "utf8" adds one, which az can't read)
    [IO.File]::WriteAllText($parameterFile, ($parameters | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
    Write-Host "Deploying (a few minutes)..."
    $appUrl = az deployment group create -g $resourceGroup -n main -f infra/main.bicep -p "@$parameterFile" `
        --query properties.outputs.appUrl.value -o tsv --only-show-errors
    if ($LASTEXITCODE -ne 0) { Stop-WithError "The deployment failed (see the message above)." }
} finally {
    Remove-Item $parameterFile -ErrorAction SilentlyContinue
    $dbPassword = $claudeKey = $jwtSecret = $smtpPassword = $null
}

# ---- 5. Wait for the app to start ----
Write-Host "Deployed. Waiting for the app to start at $appUrl (Java takes up to ~2 minutes)..."
for ($i = 0; $i -lt 30; $i++) {
    try {
        if ((Invoke-WebRequest -UseBasicParsing "$appUrl/actuator/health/readiness" -TimeoutSec 20).StatusCode -eq 200) {
            Write-Host "FitCheck is up: $appUrl" -ForegroundColor Green; exit 0
        }
    } catch {}
    Start-Sleep 10
}
Write-Host "Not ready after 5 minutes. Logs: az containerapp logs show -g $resourceGroup -n fitcheck --tail 50" -ForegroundColor Yellow
