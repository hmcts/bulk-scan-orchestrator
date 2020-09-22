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

variable "supported_jurisdictions" {
  type = "list"
  description = "Jurisdictions to be supported by Bulk Scan in the given environment. Bulk Scan will only be able to map these ones to IDAM user credentials"
  default = ["SSCS", "BULKSCAN", "PROBATE", "DIVORCE", "FINREM", "CMC"]
}

variable "common_tags" {
  type = map(string)
}


