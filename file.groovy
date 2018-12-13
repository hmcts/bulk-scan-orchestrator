#!/usr/bin/env groovy

//    wrap([
//      $class                   : 'AzureKeyVaultBuildWrapper',
//      azureKeyVaultSecrets     : theSecrets.get(key),
//      keyVaultURLOverride      : "https://${key.replace('${env}', env)}.vault.azure.net/",
//      applicationIDOverride    : env.AZURE_CLIENT_ID,
//      applicationSecretOverride: env.AZURE_CLIENT_SECRET
//    ]) {
//      body.call()
//    }

/*

 someMethod(Closure body) {
   wrap('bulk-scan', bulkScanSecrets) {
    wrap('s2s', s2sSecrets) {
      wrap('ccd', ccdSecrets) {
        body.call()
      }
    }
   }
 }


 */

def secretMap = [
  'bulk-scan-${env}': [
    secret('idam-users-bulkscan-username', 'IDAM_USER_NAME'),
    secret('idam-users-bulkscan-password', 'IDAM_USER_PASSWORD'),
    secret('idam-client-secret', 'IDAM_CLIENT_SECRET')
  ],
  's2s-${env}'      : [
    secret('microservicekey-bulk-scan-orchestrator', 'S2S_SECRET'),
    secret('microservicekey-ccd-data', 'DATA_STORE_S2S_KEY'),
    secret('microservicekey-ccd-definition', 'DEFINITION_STORE_S2S_KEY'),
    secret('microservicekey-ccd-gw', 'API_GATEWAY_S2S_KEY'),
  ],
  'ccd-${env}'      : [
    secret('ccd-api-gateway-oauth2-client-secret', 'API_GATEWAY_IDAM_SECRET')
  ]
]

static LinkedHashMap<String, Object> secret(String secretName, String envVar) {
  [$class     : 'AzureKeyVaultSecret',
   secretType : 'Secret',
   name       : secretName,
   version    : '',
   envVariable: envVar
  ]
}

def mapToUse = new HashMap(secretMap)

env = 'aat'

executeClosure(mapToUse) {
  println "hi"
}

def doExecute(Map<String, String> secrets, Closure body) {
  def entry = secrets.entrySet().iterator().next()
  wrap("https://${entry.key.replace('${env}', this.env)}.vault.azure.net/", entry.value) {
    if (secrets.size() > 1) {
      secrets.remove(entry.key)
      return executeClosure(secrets, body)
    } else {
      body.call()
    }
  }
}

def executeClosure(Map<String, String> secrets, Closure body) {
  def mapToUse = new HashMap(secrets)
  doExecute(mapToUse, body)
}

def wrap(String keyVaultUrl, List<Map<String, Object>> secrets, Closure body) {
  println "${keyVaultUrl} ${secrets}"

  body.call()
}
