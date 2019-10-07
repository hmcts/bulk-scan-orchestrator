ARG APP_INSIGHTS_AGENT_VERSION=2.5.0

FROM hmctspublic.azurecr.io/base/java:openjdk-8-distroless-1.0

COPY lib/applicationinsights-agent-2.5.0.jar lib/AI-Agent.xml /opt/app/
COPY build/libs/bulk-scan-orchestrator.jar /opt/app/

HEALTHCHECK --interval=10s --timeout=10s --retries=10 CMD http_proxy="" wget -q --spider http://localhost:8582/health || exit 1

EXPOSE 8582

CMD ["bulk-scan-orchestrator.jar"]

