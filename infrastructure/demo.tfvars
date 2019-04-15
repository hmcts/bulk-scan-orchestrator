idam_api_url = "https://idam-api.demo.platform.hmcts.net"
idam_client_redirect_uri = "https://bulk-scan-orchestrator-demo.service.core-compute-demo.internal/oauth2/callback"
supported_jurisdictions = ["SSCS", "BULKSCAN", "DIVORCE", "PROBATE"]

delete_envelopes_dlq_messages_enabled = "true"
# Run the dlq scheduler every minute
delete_envelopes_dlq_messages_cron = "0 * * * * *"
delete_envelopes_dlq_messages_ttl = "10s"
