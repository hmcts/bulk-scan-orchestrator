variable "product" {}

variable "raw_product" {
  default = "bulk-scan"
}

variable "component" {
  type = "string"
}

variable "location_app" {
  type    = "string"
  default = "UK South"
}

variable "env" {
  type = "string"
}

variable "ilbIp" {}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  type        = "string"
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "subscription" {}

variable "capacity" {
  default = "1"
}

variable "common_tags" {
  type = "map"
}

variable "appinsights_instrumentation_key" {
  description = "Instrumentation key of the App Insights instance this webapp should use. Module will create own App Insights resource if this is not provided"
  default     = ""
}

variable "idam_api_url" {
  default = "http://betaDevBccidamAppLB.reform.hmcts.net"
}

variable "idam_client_redirect_uri" {
  default = "https://bulk-scan-orchestrator-sandbox.service.core-compute-sandbox.internal/oauth2/callback"
}

variable "s2s_name" {
  default = "bulk_scan_orchestrator"
}

variable "supported_jurisdictions" {
  type = "list"
  description = "Jurisdictions to be supported by Bulk Scan in the given environment. Bulk Scan will only be able to map these ones to IDAM user credentials"
  default = ["SSCS", "BULKSCAN", "PROBATE", "DIVORCE", "FINREM", "CMC"]
}

variable "delete_envelopes_dlq_messages_enabled" {
  default = "false"
}

variable "delete_envelopes_dlq_messages_cron" {
  default = "0 0 7 * * *"
}

variable "delete_envelopes_dlq_messages_ttl" {
  default = "72h"
}

variable "transformation_url_bulkscan" {
  default = "http://bulk-scan-sample-app-aat.service.core-compute-aat.internal"
}

variable "transformation_url_probate" {
  default = ""
}

