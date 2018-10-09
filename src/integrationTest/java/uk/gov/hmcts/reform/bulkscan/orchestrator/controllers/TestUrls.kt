package uk.gov.hmcts.reform.bulkscan.orchestrator.controllers

object TestUrls {
    private const val WIRE_MOCK_URL = "http://localhost:\${wiremock.port}"

    object wiremockUrls {
        const val IDAM_S2S_URL = WIRE_MOCK_URL
        const val IDAM_API_URL = WIRE_MOCK_URL
        const val CORE_CASE_DATA_URL = WIRE_MOCK_URL
    }

    //Use these to record or test against the docker versions.
    object dockerUrls {
        const val IDAM_S2S_URL = "http://localhost:4552"
        const val IDAM_API_URL = "http://localhost:8080"
        const val CORE_CASE_DATA_URL = "http://localhost:4452"
    }
}
