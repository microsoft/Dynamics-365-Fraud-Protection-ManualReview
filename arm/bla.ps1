Connect-AzAccount -Tenant 'ce2526f8-5105-481b-8e16-b2eb098b32bb' -SubscriptionId '57a7e2c4-bc8e-4dde-81f6-8722809f4b1a'
$c_app_name = "dev-dfp-mr" 
$c_app_role_name = "sandbox_risk_api" 
$app_name = "Dynamics 365 Fraud Protection" 
$sp = Get-AzureADServicePrincipal -Filter "displayName eq '$app_name'" 
$c_appRole = $sp.AppRoles | Where-Object { $_.DisplayName -eq $c_app_role_name } 
$c_sp = Get-AzureADServicePrincipal -Filter "displayName eq '$c_app_name'" 
New-AzureADServiceAppRoleAssignment -ObjectId $c_sp.ObjectId -PrincipalId $c_sp.ObjectId -ResourceId $sp.ObjectId -Id $c_appRole.Id