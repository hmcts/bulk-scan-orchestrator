provider "azurerm" {}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name            = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  is_preview          = "${(var.env == "preview" || var.env == "spreview")}"
  previewVaultName    = "${var.raw_product}-aat"
  nonPreviewVaultName = "${var.raw_product}-${var.env}"
  vaultName           = "${local.is_preview ? local.previewVaultName : local.nonPreviewVaultName}"
  local_env           = "${local.is_preview ? "aat" : var.env}"
  local_ase = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "core-compute-aat" : "core-compute-saat" : local.ase_name}"

  sku_size = "${var.env == "prod" || var.env == "sprod" || var.env == "aat" ? "I2" : "I1"}"

  ccdApiUrl = "http://ccd-data-store-api-${local.local_env}.service.${local.local_ase}.internal"

  users = {
    // configures a user for a jurisdiction
    // add secrets to all bulk-scan vaults in the form idam-users-<jurisdiction>-username idam-users-<jurisdiction>-password
    SSCS = "idam-users-sscs"
  }

  users_secret_names = "${values(local.users)}"

  users_usernames_settings = "${zipmap(
                                    formatlist("IDAM_USERS_%s_USERNAME", keys(local.users)),
                                    data.azurerm_key_vault_secret.idam_users_usernames.*.value
                                )}"

  users_passwords_settings = "${zipmap(
                                    formatlist("IDAM_USERS_%s_PASSWORD", keys(local.users)),
                                    data.azurerm_key_vault_secret.idam_users_passwords.*.value
                                )}"

  core_app_settings = {
    LOGBACK_REQUIRE_ALERT_LEVEL = false
    LOGBACK_REQUIRE_ERROR_CODE  = false

    S2S_URL = "http://rpe-service-auth-provider-${local.local_env}.service.core-compute-${local.local_env}.internal"

    QUEUE_CONNECTION_STRING = "${data.terraform_remote_state.shared_infra.queue_primary_listen_connection_string}"

    IDAM_API_URL = "${var.idam_api_url}"
    IDAM_CLIENT_SECRET = "${data.azurerm_key_vault_secret.idam_client_secret.value}"
    IDAM_REDIRECT_URI = "${var.idam_redirect_uri}"
    CORE_CASE_DATA_API_URL = "${local.ccdApiUrl}"
  }
}

module "bulk-scan-orchestrator" {
  source                          = "git@github.com:hmcts/cnp-module-webapp?ref=master"
  product                         = "${var.product}-${var.component}"
  location                        = "${var.location_app}"
  env                             = "${var.env}"
  ilbIp                           = "${var.ilbIp}"
  resource_group_name             = "${azurerm_resource_group.rg.name}"
  subscription                    = "${var.subscription}"
  capacity                        = "${var.capacity}"
  common_tags                     = "${var.common_tags}"
  appinsights_instrumentation_key = "${var.appinsights_instrumentation_key}"
  asp_name                        = "${var.product}-${var.env}"
  asp_rg                          = "${var.product}-${var.env}"
  instance_size                   = "${local.sku_size}"

  app_settings = "${merge(local.core_app_settings, local.users_usernames_settings, local.users_passwords_settings)}"
}

data "azurerm_key_vault" "key_vault" {
  name                = "${local.vaultName}"
  resource_group_name = "${local.vaultName}"
}

data "azurerm_key_vault_secret" "idam_users_usernames" {
  name      = "${local.users_secret_names[count.index]}-username"
  vault_uri = "${data.azurerm_key_vault.key_vault.vault_uri}"
  count     = "${length(local.users_secret_names)}"
}

data "azurerm_key_vault_secret" "idam_users_passwords" {
  name      = "${local.users_secret_names[count.index]}-password"
  vault_uri = "${data.azurerm_key_vault.key_vault.vault_uri}"
  count     = "${length(local.users_secret_names)}"
}

data "azurerm_key_vault_secret" "idam_client_secret" {
  name      = "idam-client-secret"
  vault_uri = "${data.azurerm_key_vault.key_vault.vault_uri}"
}
