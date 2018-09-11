FROM hmcts/cnp-java-base:openjdk-jre-8-alpine-1.3

ENV APP bulk-scan-orchestrator.jar
ENV APPLICATION_TOTAL_MEMORY 1024M
ENV APPLICATION_SIZE_ON_DISK_IN_MB 53
ENV JAVA_OPTS ""

COPY build/libs/$APP /opt/app/
