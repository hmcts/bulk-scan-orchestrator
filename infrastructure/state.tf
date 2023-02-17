terraform {
  backend "azurerm" {}

  required_providers {
    azurerm = {
      source = "hashicorp/azurerm"
      version = "3.44.0"
    }
    azuread = {
      source  = "hashicorp/azuread"
      version = "2.34.0"
    }
  }
}

