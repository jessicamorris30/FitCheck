<#
.SYNOPSIS
  Creates the identity FitCheck uses to send email through Azure Communication Services (SMTP).
  Safe to run again: it reuses what already exists.

.DESCRIPTION
  Azure Communication Services' SMTP server doesn't have its own user accounts. It accepts an
  "app registration" from your Microsoft Entra directory instead:
    username = <Communication Services name>.<app id>.<directory id>
    password = a client secret of that app (created at deploy time, see infra/README.md)
  This script creates that app and gives it ONE permission: sending email through FitCheck's
  Communication Services resource, nothing else in your subscription.

  It does not create or print any secret.
#>
$ErrorActionPreference = "Stop"
$resourceGroup = "rg-fitcheck"
$appName = "FitCheck SMTP sender"
$roleName = "FitCheck SMTP Sender"

$acs = az resource list -g $resourceGroup --resource-type "Microsoft.Communication/communicationServices" --query "[0].{id:id, name:name}" -o json | ConvertFrom-Json
if (-not $acs) { throw "No Communication Services resource in $resourceGroup. Run the infra deployment first." }
$subscriptionId = az account show --query id -o tsv
$tenantId = az account show --query tenantId -o tsv

# 1. The app registration (an identity for a program, not a person)
$appId = az ad app list --display-name $appName --query "[0].appId" -o tsv
if (-not $appId) {
    $appId = az ad app create --display-name $appName --sign-in-audience AzureADMyOrg --query appId -o tsv
    Write-Host "Created app registration '$appName'"
}
# Its "service principal" is what permissions are given to
if (-not (az ad sp list --filter "appId eq '$appId'" --query "[0].id" -o tsv)) {
    az ad sp create --id $appId | Out-Null
    Write-Host "Created service principal"
}

# 2. A custom role with only the permissions SMTP sending needs (per Microsoft's SMTP docs)
if (-not (az role definition list --custom-role-only true --name $roleName --query "[0].id" -o tsv)) {
    $role = @{
        Name             = $roleName
        Description      = "Send email through Azure Communication Services SMTP (FitCheck)."
        Actions          = @(
            "Microsoft.Communication/CommunicationServices/Read",
            "Microsoft.Communication/CommunicationServices/Write",
            "Microsoft.Communication/EmailServices/Write"
        )
        AssignableScopes = @("/subscriptions/$subscriptionId/resourceGroups/$resourceGroup")
    } | ConvertTo-Json
    $file = New-TemporaryFile
    Set-Content -Path $file -Value $role -Encoding ascii
    az role definition create --role-definition "@$file" | Out-Null
    Remove-Item $file
    Write-Host "Created role '$roleName'"
}

# 3. Give the app that role on FitCheck's Communication Services resource only.
#    (A new role can take a minute to become usable, so retry a few times.)
$spId = az ad sp list --filter "appId eq '$appId'" --query "[0].id" -o tsv
for ($i = 1; $i -le 10; $i++) {
    $existing = az role assignment list --assignee $spId --scope $acs.id --query "[?roleDefinitionName=='$roleName'] | length(@)" -o tsv 2>$null
    if ($existing -eq "1") { break }
    az role assignment create --assignee-object-id $spId --assignee-principal-type ServicePrincipal `
        --role $roleName --scope $acs.id 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "Assigned the role"; break }
    Write-Host "Role not ready yet, retrying in 15 s ($i/10)..."; Start-Sleep 15
}

Write-Host ""
Write-Host "SMTP host:     smtp.azurecomm.net (port 587, STARTTLS)"
Write-Host "SMTP username: $($acs.name).$appId.$tenantId"
Write-Host "SMTP password: a client secret of app $appId (created at deploy time, never stored)"
