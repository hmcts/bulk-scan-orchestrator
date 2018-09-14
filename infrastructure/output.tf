output "microserviceName" {
  value = "${var.component}"
}

// region: settings for functional tests

output "QUEUE_READ_CONN_STRING" {
  value = "${data.terraform_remote_state.shared_infra.queue_primary_listen_connection_string}"
}

output "QUEUE_WRITE_CONN_STRING" {
  value = "${data.terraform_remote_state.shared_infra.queue_primary_send_connection_string}"
}

// endregion
