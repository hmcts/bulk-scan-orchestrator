provider "azurerm" {
  features {}
}

# Make sure the resource group exists
resource "azurerm_resource_group" "rg" {
  name     = "${var.product}-${var.component}-${var.env}"
  location = var.location_app
}

data "azurerm_key_vault" "key_vault" {
  name                = "${var.raw_product}-${var.env}"
  resource_group_name = "${var.raw_product}-${var.env}"
}

data "azurerm_key_vault" "s2s_key_vault" {
  name                = "s2s-${var.env}"
  resource_group_name = "rpe-service-auth-provider-${var.env}"
}


data "azurerm_key_vault_secret" "s2s_secret" {
  key_vault_id = data.azurerm_key_vault.s2s_key_vault.id
  name      = "microservicekey-bulk-scan-orchestrator"
}

# Copy orchestrator s2s secret from s2s key vault to bulkscan key vault
resource "azurerm_key_vault_secret" "bulk_scan_orchestrator_app_s2s_secret" {
  name         = "s2s-secret-bulk-scan-orchestrator"
  value        = data.azurerm_key_vault_secret.s2s_secret.value
  key_vault_id = data.azurerm_key_vault.key_vault.id
}

module "bulk-scan-db" {
  source             = "git@github.com:hmcts/cnp-module-postgres?ref=master"
  product            = var.product
  component          = var.component
  location           = var.location_db
  env                = var.env
  database_name      = "bulk_scan"
  postgresql_user    = "bulk_scanner"
  postgresql_version = "10"
  sku_name           = "GP_Gen5_2"
  sku_tier           = "GeneralPurpose"
  common_tags        = var.common_tags
  subscription       = var.subscription
}

# Staging DB to be used by AAT staging pod for functional tests
module "bulk-scan-staging-db" {
  source             = "git@github.com:hmcts/cnp-module-postgres?ref=master"
  name               = "${var.product}-${var.component}-staging"
  product            = var.product
  component          = var.component
  location           = var.location_db
  env                = var.env
  database_name      = "bulk_scan"
  postgresql_user    = "bulk_scanner"
  postgresql_version = "11"
  sku_name           = "GP_Gen5_2"
  sku_tier           = "GeneralPurpose"
  common_tags        = var.common_tags
  subscription       = var.subscription
}

# region DB secrets
# names have to be in such format as library hardcodes them for migration url build

resource "azurerm_key_vault_secret" "db_user" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-POSTGRES-USER"
  value        = module.bulk-scan-db.user_name
}

resource "azurerm_key_vault_secret" "db_password" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-POSTGRES-PASS"
  value        = module.bulk-scan-db.postgresql_password
}

resource "azurerm_key_vault_secret" "db_host" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-POSTGRES-HOST"
  value        = module.bulk-scan-db.host_name
}

resource "azurerm_key_vault_secret" "db_port" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-POSTGRES-PORT"
  value        = module.bulk-scan-db.postgresql_listen_port
}

resource "azurerm_key_vault_secret" "db_database" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-POSTGRES-DATABASE"
  value        = module.bulk-scan-db.postgresql_database
}

# endregion

# region staging DB secrets

resource "azurerm_key_vault_secret" "staging_db_user" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-staging-postgres-user"
  value        = module.bulk-scan-staging-db.user_name
}

resource "azurerm_key_vault_secret" "staging_db_password" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-staging-postgres-pass"
  value        = module.bulk-scan-staging-db.postgresql_password
}

resource "azurerm_key_vault_secret" "staging_db_host" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-staging-postgres-host"
  value        = module.bulk-scan-staging-db.host_name
}

resource "azurerm_key_vault_secret" "staging_db_port" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-staging-postgres-port"
  value        = module.bulk-scan-staging-db.postgresql_listen_port
}

resource "azurerm_key_vault_secret" "staging_db_database" {
  key_vault_id = data.azurerm_key_vault.key_vault.id
  name         = "${var.component}-staging-postgres-database"
  value        = module.bulk-scan-staging-db.postgresql_database
}

# endregion
