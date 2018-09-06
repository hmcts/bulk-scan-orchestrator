provider "azurerm" {}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name            = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  is_preview          = "${(var.env == "preview" || var.env == "spreview")}"
  previewVaultName    = "${var.product}-bsp"
  nonPreviewVaultName = "${var.product}-bsp-${var.env}"
  vaultName           = "${local.is_preview ? local.previewVaultName : local.nonPreviewVaultName}"
  keyVaultUri         = "https://rpe-bsp-${var.env}.vault.azure.net/"
  local_env           = "${local.is_preview ? "aat" : var.env}"
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

  app_settings = {
    LOGBACK_REQUIRE_ALERT_LEVEL = false
    LOGBACK_REQUIRE_ERROR_CODE  = false
    QUEUE_CONNECTION_STRING     = "${data.terraform_remote_state.shared_infra.queue_primary_listen_connection_string}"
  }
}
