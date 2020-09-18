variable "product" {}

variable "raw_product" {
  default = "bulk-scan"
}

variable "component" {}

variable "location_app" {
  default = "UK South"
}

variable "env" {}

variable "ilbIp" {}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "subscription" {}

variable "common_tags" {
  type = map(string)
}

variable "deployment_namespace" {
  default = ""
}
