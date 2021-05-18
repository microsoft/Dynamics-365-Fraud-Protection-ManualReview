# Copyright (c) Microsoft Corporation.
# Licensed under the MIT license.

# Requirements
# Install-Module -Name Az.ManagedServiceIdentity -AllowPrerelease

Param(
  [Parameter(Mandatory=$true)]
  [string] $config,

  [SecureString] $mailSecurePassword,

  [switch] $setMailPassword,

  [switch] $setClientSecret,

  [switch] $twoTenants,

  [string] $location = "westus"
)

# Read prefix from json
$json = Get-Content $config | ConvertFrom-Json

$prefix         = $json.parameters.prefix.value
$subscriptionId = $json.parameters.subscriptionId.value
$tenantId       = $json.parameters.tenantId.value
$envType        = $json.parameters.envType.value
$clientTenantId = $tenantId

if ($twoTenants) {
    $clientTenantId = $json.parameters.clientTenantId.value
    $clientAdApp    = @{
        "appClientId"           = $json.parameters.appClientId.value
        "appSpId"         = $json.parameters.appSpId.value
        "dfpSpId"         = $json.parameters.dfpSpId.value
        "clientTenantShortName" = $json.parameters.clientTenantShortName.value
    }
}

$deploymentResourceGroup  = $prefix + "-rg"
$deploymentStorageAccount = $prefix.ToLower().Replace("-","") + "deploysa"
$deploymentContainer      = "deploy"
$sasTokenHours            = 24 * 365      # one year
$deploymentIdentity       = $prefix + "-deploy-identity"
$keyVaultName             = $prefix.ToLower().Replace("-","") + "keyvault"
$mapAppName               = $prefix + "-map"

$baseDir = Get-Location

. $baseDir/functions.ps1

#################################################################   MAIN PROGRAM

# Read mail account password from user
if ($setMailPassword) {
    $mailSecurePassword = Read-Host "Enter the mail account password" -AsSecureString
}

# Read client secret when azure app deployed to different tenant
if ($setClientSecret) {
    $clientSecureSecret = Read-Host "Enter the Azure application secret" -AsSecureString
}

Write-Host "=== Setting Azure context..."
if (!(Set-AzContext -SubscriptionId $subscriptionId -TenantId $tenantId -ErrorAction Ignore)) {
  Write-Host "= Login to Azure"
  Write-Host "Provide credentials for PS in tenant ${tenantId} and subscription ${subscriptionId}"
  
}
$currentAzureContext = Get-AzContext

$output = az account show --subscription $subscriptionId --query 'id'
if (!($output)) {
  Write-Host "= Provide credentials for Az Cli in tenant ${tenantId} and subscription ${subscriptionId}"
  az login
  $output = az account show --subscription $subscriptionId --query 'id'
  if (!($output)) {
    throw "Please provide credentials valid tenant"
  }
}
az account set --subscription $subscriptionId


# Register providers
Write-Host "=== Register Azure resource providers"
Register-AzResourceProvider `
    -ProviderNamespace Microsoft.ContainerInstance

Write-Host "=== Cleanup artifacts temporary directory"
if (Test-Path "${baseDir}/build" -PathType container) {
    Remove-Item "${baseDir}/build" -Recurse
}
New-Item -Path "${baseDir}" -Name "build" -ItemType "directory" -ErrorAction Ignore

# Build backend applications
Write-Host "=== Build backend applications"
BuildBackend

# Build frontend application
Write-Host "=== Build frontend application"
BuildFrontend


# Create Resource Group for deployment artifacts
Write-Host "=== Create resource group for deployment"
New-AzResourceGroup `
    -Name $deploymentResourceGroup `
    -Location $location `
    -Force


# Provide key vault and generate client secret for Azure AD App
Write-Host "=== Provide Key Vault"
$vault = Get-AzKeyVault -VaultName $keyVaultName
if (!($vault)) {
    $vault = New-AzKeyVault `
        -Name $keyVaultName `
        -ResourceGroupName $deploymentResourceGroup `
        -Location $location `
        -EnabledForTemplateDeployment
}

# Store app client secret in Key Vault if it was provided
Write-Host "=== Provide Azure AD App client secret"
if (!(Get-AzKeyVaultSecret -Name "client-secret" -VaultName $keyVaultName))
{
    if (!($clientSecureSecret)) {
        if ($twoTenants) {
            Write-Host "When Azure app installed to different tenant, deployment should be done with -SetClientSecret flag"
            throw "Client secret not provided"
        }
        $clientSecret = GetRandomPassword
        $clientSecureSecret = ConvertTo-SecureString $clientSecret -AsPlainText -Force

    }
    PutSecret -vaultName $keyVaultName -secretName "client-secret" -secretSecureValue $clientSecureSecret
}
else
{
    $secret = (Get-AzKeyVaultSecret -VaultName $keyVaultName -Name "client-secret")
    $secretValueText = ExtractSecret -secret $secret.SecretValue
    $clientSecureSecret = ConvertTo-SecureString $secretValueText -AsPlainText -Force
}

# Store Map app client secret in Key Vault if it was provided
Write-Host "=== Provide Azure AD Map App client secret"
if (!(Get-AzKeyVaultSecret -Name "map-client-secret" -VaultName $keyVaultName))
{
    $mapClientSecret = GetRandomPassword
    PutSecret -vaultName $keyVaultName -secretName "map-client-secret" -secretValue $mapClientSecret
}
else
{
    $mapClientSecretKey = (Get-AzKeyVaultSecret -VaultName $keyVaultName -Name "map-client-secret")
	$mapClientSecret = ExtractSecret -secret $mapClientSecretKey.SecretValue;
}

# Store mail password in Key Vault if it was provided
if ($mailSecurePassword)
{
    PutSecret -vaultName $keyVaultName -secretName "spring-mail-password" -secretSecureValue $mailSecurePassword
}

# Register azure app if single tenant deployment
if (!($twoTenants)) {
    Write-Host "=== Register azure application"
    $clientAdApp = ./deploy_ad_app.ps1 `
        -prefix $prefix `
        -envType $envType `
        -tenantId $tenantId `
        -clientSecureSecret $clientSecureSecret `
}

# Register map application in Azure AD
Write-Host "== Create Azure application for maps"
$mapClientSecureSecret = ConvertTo-SecureString $mapClientSecret -AsPlainText -Force
$mapApp = CreateMapApp -mapAppName $mapAppName -MapAppSecurePassword $mapClientSecureSecret
Write-Host "Map application id:"$mapApp.mapAppId
Write-Host "Map service principal id:"$mapApp.mapSpId

# Prepare Storage Account
Write-Host "=== Provide storage account for deployment artifacts"
$storageAccount = Get-AzStorageAccount -name $deploymentStorageAccount -resourcegroupname $deploymentResourceGroup -ErrorAction Ignore
if (!$storageAccount) {
    $storageAccount = New-AzStorageAccount `
        -Name $deploymentStorageAccount `
        -ResourceGroupName $deploymentResourceGroup `
        -Location $location `
        -SkuName Standard_RAGRS `
        -Kind StorageV2 `
        -AllowBlobPublicAccess $false
    $ctx = $storageAccount.Context
} 
else {
    $keys = Get-AzStorageAccountKey -Name $deploymentStorageAccount -ResourceGroupName $deploymentResourceGroup
    $ctx = New-AzStorageContext -StorageAccountName $deploymentStorageAccount -StorageAccountKey $keys.Item(0).value
}


# Preparing storage container for frontend code
Write-Host "=== Create container for frontend code"
ProvideCleanContainer -storageContainer $frontendContainer -storageContext $ctx
Write-Host "=== Upload frontend static code"
Get-ChildItem -Path "build/frontend/*" -File -Recurse | Set-AzStorageBlobContent -Container $frontendContainer -Context $ctx -Force | Format-List -Property Name


# Preparing storage container for templates
Write-Host "=== Create container for templates"
ProvideCleanContainer -storageContainer $deploymentContainer -storageContext $ctx
Write-Host "=== Upload application artifacts"
foreach ($app in $applications.GetEnumerator()) {
    # Keep last two versions, remove the rest
    Get-AzStorageBlob -Container $deploymentContainer -Context $ctx `
        | Where-object { $_.Name -Match "^$($app.Name).*target.*zip" } `
        | Sort-Object LastModified -desc `
        | Select-Object -Skip 2 `
        | Remove-AzStorageBlob
    # Upload new version
    Set-AzStorageBlobContent `
        -Container $deploymentContainer `
        -Context $ctx `
        -File "build/$($app.Name)/target.zip" `
        -Blob "$($app.Name)/target-${version}.zip" `
        -Force
}
Write-Host "=== Upload templates"
Get-ChildItem -Include '*.json' -Exclude '*parameters*','*manifest*','config.json' -File -Recurse | Set-AzStorageBlobContent -Container $deploymentContainer -Context $ctx -Force | Format-List -Property Name
Get-ChildItem -Path "deploy_frontend.ps1" -File -Recurse | Set-AzStorageBlobContent -Container $deploymentContainer -Context $ctx -Force | Format-List -Property Name


# Sas token for Resource manager to get access to templates, manifests and powershell snippets
Write-Host "=== Generate SAS token for Resource Manager"
$token = New-AzStorageContainerSASToken `
    -Name $deploymentContainer `
    -Context $ctx `
    -Permission r `
    -ExpiryTime (Get-Date).AddHours($sasTokenHours)


# Managed identity used to perform powershell deployments in ARM templates            
Write-Host "=== Create managed identity for deployment"
$spId = Get-AzUserAssignedIdentity `
    -Name $deploymentIdentity `
    -ResourceGroupName $deploymentResourceGroup `
    -ErrorAction Ignore
if (!($spId))
{
  $spId = New-AzUserAssignedIdentity `
      -ResourceGroupName $deploymentResourceGroup `
      -Name $deploymentIdentity
}

Write-Host "=== Assign role to managed identity"
if (!(Get-AzRoleAssignment -ObjectId $spId.PrincipalId -ResourceGroupName $deploymentResourceGroup -ErrorAction Ignore))
{ 
    for ($i=0; $i -lt 10; $i++) {
        Start-Sleep -Seconds 5
        if (New-AzRoleAssignment -ObjectId $spId.PrincipalId -RoleDefinitionName "Contributor" -ResourceGroupName $deploymentResourceGroup -ErrorAction Ignore) {
            Write-Host "Managed Identity"$spId.Name"granted Contributor role at resource group"$deploymentResourceGroup
            break
        }
    }
}


Write-Host "=== Start deployment"
$deployment = New-AzResourceGroupDeployment `
    -ResourceGroupName $deploymentResourceGroup `
    -TemplateUri ("https://${deploymentStorageAccount}.blob.core.windows.net/deploy/main.json" + $token) `
    -TemplateParameterFile $config `
    -clientTenantId $clientTenantId `
    -appClientId $clientAdApp.appClientId `
    -appSpId $clientAdApp.appSpId `
    -dfpSpId $clientAdApp.dfpSpId `
    -mapAppId $mapApp.mapAppId `
    -mapSpId $mapApp.mapSpId `
    -deploymentIdentity $spId.Id `
    -deploymentStorageAccount $deploymentStorageAccount `
    -clientTenantShortName $clientAdApp.clientTenantShortName `
    -appQueuesPackageUrl ("https://${deploymentStorageAccount}.blob.core.windows.net/deploy/queues/target-${version}.zip" + $token) `
    -appAnalyticsPackageUrl ("https://${deploymentStorageAccount}.blob.core.windows.net/deploy/analytics/target-${version}.zip" + $token) `
    -keyVaultName $keyVaultName

Write-Host "Deployment Output"
Write-Host $deployment.OutputsString

[System.Console]::Beep()
