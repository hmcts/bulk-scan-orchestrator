capacity = "2"
idam_api_url = "https://idam-api.aat.platform.hmcts.net"
idam_client_redirect_uri = "https://rpe-bulk-scan-processor-sandbox.service.core-compute-sandbox.internal/oauth2/callback"
supported_jurisdictions = ["SSCS", "BULKSCAN", "DIVORCE", "PROBATE", "CMC"]

delete_envelopes_dlq_messages_enabled = "true"
# Run the dlq scheduler every minute
delete_envelopes_dlq_messages_cron = "0 * * * * *"
delete_envelopes_dlq_messages_ttl = "10s"

ccd-feign-logging = "full"
