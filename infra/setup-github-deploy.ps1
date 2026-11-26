<#
.SYNOPSIS
  Lets GitHub Actions deploy new FitCheck images to Azure, without storing any password in GitHub.
  Safe to run again: it reuses what already exists.

.DESCRIPTION
  Uses "workload identity federation" (OIDC):
    1. An app registration "FitCheck GitHub deploy" is the identity the workflow signs in as.
    2. A federated credential says: trust GitHub's sign-in tokens, but ONLY those issued to
       jobs of jessicamorris30/FitCheck that deploy to its "production" environment.
    3. A custom role lets that identity update Container Apps in rg-fitcheck and nothing else
       (no database, no storage keys, no other resource groups).
  Prints the three IDs the workflow needs (IDs, not secrets).
#>
$ErrorActionPreference = "Continue"
$resourceGroup = "rg-fitcheck"
$repo = "jessicamorris30/FitCheck"
$environment = "production"
$appName = "FitCheck GitHub deploy"
$roleName = "FitCheck Deployer"

$subscriptionId = az account show --query id -o tsv
$tenantId = az account show --query tenantId -o tsv
$scope = "/subscriptions/$subscriptionId/resourceGroups/$resourceGroup"

# 1. App registration + service principal
$appId = az ad app list --display-name $appName --query "[0].appId" -o tsv --only-show-errors
if (-not $appId) {
    $appId = az ad app create --display-name $appName --sign-in-audience AzureADMyOrg --query appId -o tsv --only-show-errors
    Write-Host "Created app registration '$appName'"
}
if (-not (az ad sp list --filter "appId eq '$appId'" --query "[0].id" -o tsv --only-show-errors)) {
    az ad sp create --id $appId --only-show-errors | Out-Null
    Write-Host "Created service principal"
}
$spId = az ad sp list --filter "appId eq '$appId'" --query "[0].id" -o tsv --only-show-errors

# 2. Federated credential: which GitHub jobs may sign in as this app.
#    GitHub's tokens name the repo with its permanent ID numbers too (owner@id/repo@id), so a
#    renamed or re-created repo with the same name can't match. The IDs come from GitHub's API.
$info = Invoke-RestMethod "https://api.github.com/repos/$repo"
$owner, $name = $repo.Split("/")
$subject = "repo:$owner@$($info.owner.id)/$name@$($info.id):environment:$environment"
# Remove credentials in the older format (without IDs): GitHub no longer sends that.
az ad app federated-credential list --id $appId --query "[?subject!='$subject'].id" -o tsv --only-show-errors |
    Where-Object { $_ } | ForEach-Object {
        az ad app federated-credential delete --id $appId --federated-credential-id $_ --only-show-errors
        Write-Host "Removed an outdated GitHub trust rule"
    }
$existing = az ad app federated-credential list --id $appId --query "[?subject=='$subject'] | length(@)" -o tsv --only-show-errors
if ($existing -ne "1") {
    $credential = @{
        name        = "github-$environment-ids"
        issuer      = "https://token.actions.githubusercontent.com"
        subject     = $subject
        audiences   = @("api://AzureADTokenExchange")
        description = "GitHub Actions deploys of $repo to $environment"
    } | ConvertTo-Json
    $file = New-TemporaryFile
    [IO.File]::WriteAllText($file, $credential, [Text.UTF8Encoding]::new($false))
    az ad app federated-credential create --id $appId --parameters "@$file" --only-show-errors | Out-Null
    Remove-Item $file
    Write-Host "Trusted GitHub tokens for: $subject"
}

# 3. A role that can only manage Container Apps (update the image, check revisions)
if (-not (az role definition list --custom-role-only true --name $roleName --query "[0].id" -o tsv --only-show-errors)) {
    $role = @{
        Name             = $roleName
        Description      = "Deploy new images to FitCheck's Container App."
        Actions          = @(
            "Microsoft.App/containerApps/*",
            "Microsoft.App/managedEnvironments/read",
            "Microsoft.App/managedEnvironments/join/action",
            "Microsoft.App/locations/*/read",
            "Microsoft.Resources/subscriptions/resourceGroups/read"
        )
        AssignableScopes = @($scope)
    } | ConvertTo-Json
    $file = New-TemporaryFile
    [IO.File]::WriteAllText($file, $role, [Text.UTF8Encoding]::new($false))
    az role definition create --role-definition "@$file" --only-show-errors | Out-Null
    Remove-Item $file
    Write-Host "Created role '$roleName'"
}
for ($i = 1; $i -le 10; $i++) {
    $has = az role assignment list --assignee $spId --scope $scope --query "[?roleDefinitionName=='$roleName'] | length(@)" -o tsv --only-show-errors
    if ($has -eq "1") { break }
    az role assignment create --assignee-object-id $spId --assignee-principal-type ServicePrincipal `
        --role $roleName --scope $scope --only-show-errors 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { Write-Host "Assigned the role on $resourceGroup"; break }
    Write-Host "Role not ready yet, retrying in 15 s ($i/10)..."; Start-Sleep 15
}

Write-Host ""
Write-Host "For .github/workflows/ci.yml (IDs, not secrets):"
Write-Host "  AZURE_CLIENT_ID:       $appId"
Write-Host "  AZURE_TENANT_ID:       $tenantId"
Write-Host "  AZURE_SUBSCRIPTION_ID: $subscriptionId"
