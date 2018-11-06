package uk.gov.hmcts.reform.bulkscan.orchestrator

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.test.context.ActiveProfiles
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val LOG = LoggerFactory.getLogger(GetSasToken::class.java)

//@SpringBootApplication
@ActiveProfiles("nosb")
class GetSasToken {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println(GetSasToken().getSasToken(args[0], args[1], args[2]))
        }
    }

    fun getSasToken(resourceUri: String, keyName: String, key: String): String {
        val epoch = System.currentTimeMillis() / 1000L
        val week = 60 * 60 * 24 * 7
        val expiry = (epoch + week).toString()

        val resource = URLEncoder.encode(resourceUri, "UTF-8")
        val stringToSign = resource + "\n" + expiry
        val sigBytes = getHmaC256(key, stringToSign)
        val signature = URLEncoder.encode(
            sigBytes,
            "UTF-8"
        )
        return "SharedAccessSignature sr=" + resource + "&sig=" + signature + "&se=" + expiry + "&skn=" + keyName
    }

    fun getHmaC256(key: String, input: String): String {
        val sha256HMAC = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        sha256HMAC.init(secretKey)
        val encoder = Base64.getEncoder()
        return String(encoder.encode(sha256HMAC.doFinal(input.toByteArray())))
    }
}
