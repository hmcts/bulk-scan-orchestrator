import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class GetSASToken {

    public static void main(String[] args) throws Exception {

        String sas = getSASToken("sb://bulk-scan-servicebus-sandbox.servicebus.windows.net/",
            "SendSharedAccessKey",
            "t1t3w9EVognlP9Lp3IcpjjFP69opitogcwAa1NVrkBs=");
        //Endpoint=sb://bulk-scan-servicebus-sandbox.servicebus.windows.net/;SharedAccessKeyName=SendSharedAccessKey;SharedAccessKey=t1t3w9EVognlP9Lp3IcpjjFP69opitogcwAa1NVrkBs=;EntityPath=envelopes
        System.out.println(sas);
    }

    private static String getSASToken(String resourceUri, String keyName, String key) throws Exception {
        long epoch = System.currentTimeMillis() / 1000L;
        int week = 60 * 60 * 24 * 7;
        String expiry = Long.toString(epoch + week);

        String stringToSign = URLEncoder.encode(resourceUri, "UTF-8") + "\n" + expiry;
        String signature = getHMAC256(key, stringToSign);
        return "SharedAccessSignature sr=" + URLEncoder.encode(resourceUri, "UTF-8") + "&sig=" +
            URLEncoder.encode(signature, "UTF-8") + "&se=" + expiry + "&skn=" + keyName;
    }


    private static String getHMAC256(String key, String input) throws Exception {
        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        sha256HMAC.init(secretKey);
        Base64.Encoder encoder = Base64.getEncoder();

        return new String(encoder.encode(sha256HMAC.doFinal(input.getBytes(UTF_8))));
    }
}
