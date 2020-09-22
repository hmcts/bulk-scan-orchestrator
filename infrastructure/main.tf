provider "azurerm" {
  version = "=1.42.0"
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  is_preview          = "${(var.env == "preview" || var.env == "spreview")}"
  previewVaultName    = "${var.raw_product}-aat"
  nonPreviewVaultName = "${var.raw_product}-${var.env}"
  vaultName           = "${local.is_preview ? local.previewVaultName : local.nonPreviewVaultName}"
  local_env           = "${local.is_preview ? "aat" : var.env}"

  users = {
    // configures a user for a jurisdiction
    // add secrets to all bulk-scan vaults in the form idam-users-<jurisdiction>-username idam-users-<jurisdiction>-password
    SSCS = "idam-users-sscs"
    BULKSCAN = "idam-users-bulkscan"
    DIVORCE = "idam-users-div"
    PROBATE = "idam-users-probate"
    FINREM = "idam-users-finrem"
    CMC = "idam-users-cmc"
  }

  all_jurisdictions     = "${keys(local.users)}"
  supported_user_keys   = "${matchkeys(local.all_jurisdictions, local.all_jurisdictions, var.supported_jurisdictions)}"
  supported_user_values = "${matchkeys(values(local.users), local.all_jurisdictions, var.supported_jurisdictions)}"

  # a subset of local.users, limited to the supported jurisdictions
  supported_users       = "${zipmap(local.supported_user_keys, local.supported_user_values)}"

  users_secret_names = "${values(local.supported_users)}"

  s2s_rg  = "rpe-service-auth-provider-${local.local_env}"
}

data "azurerm_key_vault" "key_vault" {
  name                = "${local.vaultName}"
  resource_group_name = "${local.vaultName}"
}

data "azurerm_key_vault" "s2s_key_vault" {
  name                = "s2s-${local.local_env}"
  resource_group_name = "${local.s2s_rg}"
}

data "azurerm_key_vault_secret" "idam_users_usernames" {
  count        = "${length(local.users_secret_names)}"
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${local.users_secret_names[count.index]}-username"
}

data "azurerm_key_vault_secret" "idam_users_passwords" {
  count        = "${length(local.users_secret_names)}"
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "${local.users_secret_names[count.index]}-password"
}

data "azurerm_key_vault_secret" "idam_client_secret" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "idam-client-secret"
}

data "azurerm_key_vault_secret" "s2s_secret" {
  key_vault_id = "${data.azurerm_key_vault.s2s_key_vault.id}"
  name      = "microservicekey-bulk-scan-orchestrator"
}

# Copy orchestrator s2s secret from s2s key vault to bulkscan key vault
resource "azurerm_key_vault_secret" "bulk_scan_orchestrator_app_s2s_secret" {
  name         = "s2s-secret-bulk-scan-orchestrator"
  value        = "${data.azurerm_key_vault_secret.s2s_secret.value}"
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
}
