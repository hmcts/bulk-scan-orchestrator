package uk.gov.hmcts.reform.bulkscan.orchestrator.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.slf4j.LoggerFactory.getLogger;

public final class Util {
    private Util() {
        // utility class
    }

    private static final Logger logger = getLogger(Util.class);

    public static String getDocumentUuid(String documentUrl) {
        return documentUrl.substring(documentUrl.lastIndexOf("/") + 1);
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return instant == null
            ? null
            : ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * The function `validateAuthorization` checks if the provided authorization key
     * matches the expected format and value, throwing an exception if it is missing or invalid.
     *
     * @param authorizationKey The `authorizationKey` parameter is a string that represents the
     *                         authorization token used for authentication. In this specific method, it
     *                         is expected to be in the format "Bearer {apiKey}"
     * @param apiKey a string of alphanumerical characters that authorise the user to engage with certain endpoints.
     */
    public static void validateAuthorization(String authorizationKey, String apiKey) {
        if (StringUtils.isEmpty(authorizationKey)) {
            logger.error("API Key is missing");
            throw new InvalidApiKeyException("API Key is missing");
        } else if (!authorizationKey.equals("Bearer " + apiKey)) {
            logger.error("Invalid API Key");
            throw new InvalidApiKeyException("Invalid API Key");
        }
    }
}
