output "microserviceName" {
  value = "${var.component}"
}

// region: settings for functional tests

output "ENVELOPES_QUEUE_WRITE_CONN_STRING" {
  value = "${data.azurerm_key_vault_secret.envelopes_queue_send_conn_str.value}"
}

output "ENVELOPES_QUEUE_READ_CONN_STRING" {
  value = "${data.azurerm_key_vault_secret.envelopes_queue_listen_conn_str.value}"
}

output "core_case_data_api_url" {
  value = "${local.ccdApiUrl}"
}

output "idam_api_url" {
  value = "${var.idam_api_url}"
}

output "s2s_url" {
  value = "${local.s2s_url}"
}

output "s2s_name" {
  value = "${var.s2s_name}"
}

output "idam_user_name" {
  value = "${local.users_usernames_settings["IDAM_USERS_BULKSCAN_USERNAME"]}"
}

output "idam_client_redirect_uri" {
  value = "${var.idam_client_redirect_uri}"
}

output "document_management_url" {
 value = "${local.dm_store_api_url}"
}

// endregion

