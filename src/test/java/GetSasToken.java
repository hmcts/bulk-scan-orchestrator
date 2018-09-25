import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

public class GetSasToken {

    public static void main(String[] args) throws Exception {

        String sas = new GetSasToken().getSasToken("sb://bulk-scan-servicebus-sandbox.servicebus.windows.net/",
            "SendSharedAccessKey",
            "t1t3w9EVognlP9Lp3IcpjjFP69opitogcwAa1NVrkBs=");
        System.out.println(sas);
    }

    private String getSasToken(String resourceUri, String keyName, String key) throws Exception {
        long epoch = System.currentTimeMillis() / 1000L;
        int week = 60 * 60 * 24 * 7;
        String expiry = Long.toString(epoch + week);

        String stringToSign = encode(resourceUri, "UTF-8") + "\n" + expiry;
        String signature = getHmac256(key, stringToSign);
        return "SharedAccessSignature sr=" + encode(resourceUri, "UTF-8")
            + "&sig=" + encode(signature, "UTF-8")
            + "&se=" + expiry
            + "&skn=" + keyName;
    }


    private String getHmac256(String key, String input) throws Exception {
        Mac sha = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
        sha.init(secretKey);
        Base64.Encoder encoder = Base64.getEncoder();

        return new String(encoder.encode(sha.doFinal(input.getBytes(UTF_8))));
    }
}
