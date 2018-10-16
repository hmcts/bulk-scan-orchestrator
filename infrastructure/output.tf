output "microserviceName" {
  value = "${var.component}"
}

output "vaultName" {
  value = "${local.vaultName}"
}

output "vaultUri" {
  value = "${data.azurerm_key_vault.key_vault.vault_uri}"
}

// region: settings for functional tests

output "QUEUE_READ_CONN_STRING" {
  value = "${data.terraform_remote_state.shared_infra.queue_primary_listen_connection_string}"
}

output "QUEUE_WRITE_CONN_STRING" {
  value = "${data.terraform_remote_state.shared_infra.queue_primary_send_connection_string}"
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

output "idam_redirect_uri" {
  value = "${var.idam_redirect_uri}"
}

// endregion

