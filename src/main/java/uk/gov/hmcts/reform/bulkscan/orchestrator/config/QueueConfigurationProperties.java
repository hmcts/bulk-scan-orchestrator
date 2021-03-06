package uk.gov.hmcts.reform.bulkscan.orchestrator.config;

public class QueueConfigurationProperties {

    private String accessKey;
    private String accessKeyName;
    private String queueName;

    public String getAccessKey() {
        return accessKey;
    }

    public String getAccessKeyName() {
        return accessKeyName;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public void setAccessKeyName(String accessKeyName) {
        this.accessKeyName = accessKeyName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
}
