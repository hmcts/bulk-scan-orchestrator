provider "azurerm" {
  version = "=1.22.1"
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = "${var.location_app}"
}

locals {
  ase_name            = "core-compute-${var.env}"
  is_preview          = "${(var.env == "preview" || var.env == "spreview")}"
  previewVaultName    = "${var.raw_product}-aat"
  nonPreviewVaultName = "${var.raw_product}-${var.env}"
  vaultName           = "${local.is_preview ? local.previewVaultName : local.nonPreviewVaultName}"
  local_env           = "${local.is_preview ? "aat" : var.env}"
  local_ase           = "${(var.env == "preview" || var.env == "spreview") ? (var.env == "preview" ) ? "core-compute-aat" : "core-compute-saat" : local.ase_name}"

  sku_size = "${var.env == "prod" || var.env == "sprod" || var.env == "aat" ? "I2" : "I1"}"

  ccdApiUrl = "http://ccd-data-store-api-${local.local_env}.service.${local.local_ase}.internal"
  dm_store_api_url = "http://dm-store-${local.local_env}.service.${local.local_ase}.internal"

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

  users_usernames_settings = "${zipmap(
                                    formatlist("IDAM_USERS_%s_USERNAME", keys(local.supported_users)),
                                    data.azurerm_key_vault_secret.idam_users_usernames.*.value
                                )}"

  users_passwords_settings = "${zipmap(
                                    formatlist("IDAM_USERS_%s_PASSWORD", keys(local.supported_users)),
                                    data.azurerm_key_vault_secret.idam_users_passwords.*.value
                                )}"

  s2s_rg  = "rpe-service-auth-provider-${local.local_env}"
  s2s_url = "http://${local.s2s_rg}.service.core-compute-${local.local_env}.internal"

  core_app_settings = {
    S2S_URL     = "${local.s2s_url}"
    S2S_NAME    = "${var.s2s_name}"
    S2S_SECRET  = "${data.azurerm_key_vault_secret.s2s_secret.value}"

    ENVELOPES_QUEUE_CONNECTION_STRING           = "${data.azurerm_key_vault_secret.envelopes_queue_listen_conn_str.value}"

    // max delivery count for application is smaller than the actual queue setting,
    // because we want to make sure it's the app that dead-letters the message
    ENVELOPES_QUEUE_MAX_DELIVERY_COUNT          = "${data.azurerm_key_vault_secret.envelopes_queue_max_delivery_count.value - 5}"
    PROCESSED_ENVELOPES_QUEUE_CONNECTION_STRING = "${data.azurerm_key_vault_secret.processed_envelopes_queue_send_conn_str.value}"

    PAYMENTS_QUEUE_CONNECTION_STRING = "${data.azurerm_key_vault_secret.payments_queue_send_conn_str.value}"

    IDAM_API_URL              = "${var.idam_api_url}"
    IDAM_CLIENT_SECRET        = "${data.azurerm_key_vault_secret.idam_client_secret.value}"
    IDAM_CLIENT_REDIRECT_URI  = "${var.idam_client_redirect_uri}"
    CORE_CASE_DATA_API_URL    = "${local.ccdApiUrl}"
    DOCUMENT_MANAGEMENT_URL   = "${local.dm_store_api_url}"

    DELETE_ENVELOPES_DLQ_MESSAGES_ENABLED = "${var.delete_envelopes_dlq_messages_enabled}"
    DELETE_ENVELOPES_DLQ_MESSAGES_CRON    = "${var.delete_envelopes_dlq_messages_cron}"
    DELETE_ENVELOPES_DLQ_MESSAGES_TTL     = "${var.delete_envelopes_dlq_messages_ttl}"

    // region transformation URLs
    TRANSFORMATION_URL_BULKSCAN = "${var.transformation_url_bulkscan}"
    TRANSFORMATION_URL_PROBATE  = "${var.transformation_url_probate}"
    // endregion
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
  java_container_version          = "9.0"

  app_settings = "${merge(local.core_app_settings, local.users_usernames_settings, local.users_passwords_settings)}"
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

data "azurerm_key_vault_secret" "envelopes_queue_send_conn_str" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "envelopes-queue-send-connection-string"
}

data "azurerm_key_vault_secret" "envelopes_queue_listen_conn_str" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "envelopes-queue-listen-connection-string"
}

data "azurerm_key_vault_secret" "envelopes_queue_max_delivery_count" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "envelopes-queue-max-delivery-count"
}

data "azurerm_key_vault_secret" "processed_envelopes_queue_send_conn_str" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "processed-envelopes-queue-send-connection-string"
}

data "azurerm_key_vault_secret" "payments_queue_send_conn_str" {
  key_vault_id = "${data.azurerm_key_vault.key_vault.id}"
  name         = "payments-queue-send-connection-string"
}
