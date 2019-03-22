capacity = "2"
idam_api_url = "https://idam-api.aat.platform.hmcts.net"
idam_client_redirect_uri = "https://rpe-bulk-scan-processor-sandbox.service.core-compute-sandbox.internal/oauth2/callback"
supported_jurisdictions = ["SSCS", "BULKSCAN", "DIVORCE", "PROBATE"]

delete_envelopes_dlq_messages_enabled = "true"
delete_envelopes_dlq_messages_cron = "0 0 * * * *"
delete_envelopes_dlq_messages_ttl = "5m"
