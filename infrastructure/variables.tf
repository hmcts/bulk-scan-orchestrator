variable "product" {}

variable "raw_product" {
  default = "bulk-scan"
}

variable "component" {
  type = string
}

variable "location_app" {
  type    = string
  default = "UK South"
}

variable "location_db" {
  type    = string
  default = "UK South"
}

variable "env" {
  type = string
}

variable "tenant_id" {}

variable "jenkins_AAD_objectId" {
  type        = string
  description = "(Required) The Azure AD object ID of a user, service principal or security group in the Azure Active Directory tenant for the vault. The object ID must be unique for the list of access policies."
}

variable "subscription" {}

variable "common_tags" {
  type = map(string)
}

variable "aks_subscription_id" {}

variable "num_staging_dbs" {
  default = 0
}

variable "test" {
  type = string
}
