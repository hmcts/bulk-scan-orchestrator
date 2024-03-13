# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location_app
}

data "azurerm_key_vault" "key_vault" {
  name                = "${var.raw_product}-${var.env}"
  resource_group_name = "${var.raw_product}-${var.env}"
}

data "azurerm_key_vault" "s2s_key_vault" {
  name                = "s2s-${var.env}"
  resource_group_name = "rpe-service-auth-provider-${var.env}"
}


data "azurerm_key_vault_secret" "s2s_secret" {
  key_vault_id = data.azurerm_key_vault.s2s_key_vault.id
  name         = "microservicekey-bulk-scan-orchestrator"
}

# Copy orchestrator s2s secret from s2s key vault to bulkscan key vault
resource "azurerm_key_vault_secret" "bulk_scan_orchestrator_app_s2s_secret" {
  name         = "s2s-secret-bulk-scan-orchestrator"
  value        = data.azurerm_key_vault_secret.s2s_secret.value
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

# Create secrets for Launch darkly - values manually populated
data "azurerm_key_vault_secret" "launch_darkly_sdk_key" {
  name         = "launch-darkly-sdk-key"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

data "azurerm_key_vault_secret" "launch_darkly_offline_mode" {
  name         = "launch-darkly-offline-mode"
  key_vault_id = data.azurerm_key_vault.key_vault.id
}
