provider "azurerm" {}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name               = "${data.terraform_remote_state.core_apps_compute.ase_name[0]}"
  is_preview             = "${(var.env == "preview" || var.env == "spreview")}"
  previewVaultName       = "${var.product}-bsp"
  nonPreviewVaultName    = "${var.product}-bsp-${var.env}"
  vaultName              = "${local.is_preview ? local.previewVaultName : local.nonPreviewVaultName}"
  keyVaultUri            = "https://rpe-bsp-${var.env}.vault.azure.net/"
}

module "queue-namespace" {
  source              = "git@github.com:hmcts/terraform-module-servicebus-namespace.git"
  name                = "${var.product}-${var.component}-servicebus-${var.env}"
  location            = "${var.location_app}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
}

module "queue" {
  source              = "git@github.com:hmcts/terraform-module-servicebus-queue.git"
  name                = "envelopes"
  namespace_name      = "${module.queue-namespace.name}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
}

module "bulk-scan-orchestrator" {
  source              = "git@github.com:hmcts/moj-module-webapp?ref=master"
  product             = "${var.product}-${var.component}"
  location            = "${var.location_app}"
  env                 = "${var.env}"
  ilbIp               = "${var.ilbIp}"
  resource_group_name = "${azurerm_resource_group.rg.name}"
  subscription        = "${var.subscription}"
  capacity            = "${var.capacity}"
  common_tags         = "${var.common_tags}"

  app_settings = {
    LOGBACK_REQUIRE_ALERT_LEVEL = false
    LOGBACK_REQUIRE_ERROR_CODE  = false
    QUEUE_CONNECTION_STRING     = "${module.queue.primary_listen_connection_string}"
  }
}

resource "azurerm_key_vault_secret" "queue_send_connection_string" {
  name      = "envelope-queue-send-conn-string"
  value     = "${module.queue.primary_send_connection_string}"
  vault_uri = "${local.keyVaultUri}"
  count     = "${local.is_preview ? "0": "1"}"
}
