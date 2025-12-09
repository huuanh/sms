package com.example.smsforwarder;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles network calls to the remote SMS endpoint.
 */
public class ApiClient {
    public interface SendCallback {
        void onSuccess();

        void onFailure(String plainJson, Exception exception);
    }

    private static final String TAG = "ApiClient";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final String POST_URL = "https://emoney.win777.casino/pay/sms3money";
    private static final String ENCRYPTED_POST_URL = "https://emoney.win777.casino/pay/sms";
    private static final String SAMPLE_PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtPRv+C4dCQwQYeGyKB5eeqcdcwnlNXBQ4zgnHTsPXzn3W2tfv8hg5zsedCZTWcU6RfboXHKD121mSBuq9FhTQ1Fcogq3UHQmbEu2+/yZSK+ovKv0Oemsh7UsdueMkY7rIIOXPhxQPYqlHnuw5NE/fNw+aZR9OBbEDtI2NBZyY9Pa8PBLIqdNLUJnIbC6HYoOmXfR+dAZVthdXQBUgcAiDyK/a9cT05zvQE4k78kBXaqZH8p4bepbFlem4ytvS7hXMynFQQXSBC76YLQGYCv0NN18wYixV8fquRCOmWcL130y1qnGp61flX0+9klkVaoCipCz2YMI4ZnPr5KhxFYQiwIDAQAB";
    private static final String ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String TOKEN = "79db628d60bf411b3c2dfb80c14b6232";

    private final OkHttpClient client;

    public ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @VisibleForTesting
    ApiClient(OkHttpClient client) {
        this.client = client;
    }

    public JSONObject buildBodyJson(SmsModel sms, String receiverNumber, String receiverIccid) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("sender", sms.getSender());
        jsonObject.put("content", sms.getContent());
        jsonObject.put("receiver_number", receiverNumber);
        long currentTime = System.currentTimeMillis();
        jsonObject.put("sent_at", currentTime);
        jsonObject.put("received_at", currentTime);
        jsonObject.put("inserted_at", currentTime);
        jsonObject.put("receiver_iccid", receiverIccid == null ? "" : receiverIccid);
        return jsonObject;
    }

    /**
     * Encrypts the SMS payload and enqueues an async POST to the encrypted endpoint.
     */
    public void sendEncryptedAsync(JSONObject bodyJson, SendCallback callback) {
        if (bodyJson == null) {
            Log.w(TAG, "Body JSON is null; abort encrypted send.");
            if (callback != null) {
                callback.onFailure(null, new IllegalArgumentException("Body JSON is null"));
            }
            return;
        }
        final String bodyString = bodyJson.toString();
        try {
            JSONArray data = new JSONArray();
            data.put(new JSONObject(bodyString));
            final String payloadJson = data.toString();
            // String encrypted = encryptJson(payloadJson);

            sendToServer(payloadJson, callback);
            
        } catch (Exception exception) {
            Log.e(TAG, "Unexpected encryption error: " + exception.getMessage());
            if (callback != null) {
                callback.onFailure(bodyString, exception);
            }
        }
    }

    public void sendEncryptedListAsync(JSONArray bodyJsonArray, SendCallback callback) {
        if (bodyJsonArray == null) {
            Log.w(TAG, "Body JSON array is null; abort encrypted send.");
            if (callback != null) {
                callback.onFailure(null, new IllegalArgumentException("Body JSON array is null"));
            }
            return;
        }
        final String bodyString = bodyJsonArray.toString();
        try {
            // String encrypted = encryptJson(bodyString);

            sendToServer(bodyString, null);
        } catch (Exception exception) {
            Log.e(TAG, "Unexpected encryption error: " + exception.getMessage());
            if (callback != null) {
                callback.onFailure(bodyString, exception);
            }
        }
    }

    private void sendToServer(String data, SendCallback callback) {
        try {
            JSONObject requestJson = new JSONObject();
            requestJson.put("token", TOKEN);
            requestJson.put("data", data);

            Log.w(TAG, "Sending data: " + requestJson.toString());
            
            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                .url(POST_URL)
                .post(requestBody)
                .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "POST failed: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure(data, e);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "POST unsuccessful: code=" + response.code());
                        if (callback != null) {
                            callback.onFailure(data, null);
                        }
                    } else {
                        Log.d(TAG, "POST successful.");
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                    response.close();
                }
            });
        } catch (Exception exception) {
            Log.e(TAG, "Unexpected POST error: " + exception.getMessage());
            if (callback != null) {
                callback.onFailure(data, exception);
            }
        }
    }
    
    public String encryptJson(String data) throws Exception {
        PublicKey publicKey = loadPublicKey(SAMPLE_PUBLIC_KEY_BASE64);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        // Khởi tạo Cipher ở chế độ MÃ HÓA
        cipher.init(Cipher.ENCRYPT_MODE, publicKey); 
        
        // Mã hóa dữ liệu (thường là UTF-8)
        byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
        
        // Trả về Base64 để dễ dàng truyền tải
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    private PublicKey loadPublicKey(String base64Key) throws GeneralSecurityException {
        byte[] decoded = Base64.decode(base64Key, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }
}
