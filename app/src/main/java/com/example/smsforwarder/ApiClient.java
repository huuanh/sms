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
    private static final String POST_URL = "https://emoney.win777.casino/pay/sms";
    private static final String ENCRYPTED_POST_URL = "https://emoney.win777.casino/pay/sms";
    private static final String SAMPLE_PUBLIC_KEY_BASE64 =
            "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtPRv+C4dCQwQYeGyKB5eeqcdcwnlNXBQ4zgnHTsPXzn3W2tfv8hg5zsedCZTWcU6RfboXHKD121mSBuq9FhTQ1Fcogq3UHQmbEu2+/yZSK+ovKv0Oemsh7UsdueMkY7rIIOXPhxQPYqlHnuw5NE/fNw+aZR9OBbEDtI2NBZyY9Pa8PBLIqdNLUJnIbC6HYoOmXfR+dAZVthdXQBUgcAiDyK/a9cT05zvQE4k78kBXaqZH8p4bepbFlem4ytvS7hXMynFQQXSBC76YLQGYCv0NN18wYixV8fquRCOmWcL130y1qnGp61flX0+9klkVaoCipCz2YMI4ZnPr5KhxFYQiwIDAQAB";

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

    public boolean sendSms(String jsonPayload) {
        RequestBody body = RequestBody.create(jsonPayload, JSON_MEDIA_TYPE);
        Request request = new Request.Builder()
                .url(POST_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            boolean success = response.isSuccessful();
            if (!success) {
                Log.w(TAG, "POST failed with code: " + response.code());
            }
            return success;
        } catch (IOException exception) {
            Log.e(TAG, "POST failed: " + exception.getMessage());
            return false;
        }
    }

    public String buildPayload(SmsModel sms, String receiverNumber) throws JSONException {
        JSONObject jsonObject = buildBodyJson(sms, receiverNumber, "");
        org.json.JSONArray data = new org.json.JSONArray();
        data.put(jsonObject);

        JSONObject payload = new JSONObject();
        payload.put("data", data);
        return payload.toString();
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

    public boolean sendEncrypted(String plainJson) {
        if (plainJson == null) {
            return false;
        }
        try {
            String encrypted = encryptJson(plainJson);
            JSONObject requestBody = new JSONObject();

            requestBody.put("token", "79db628d60bf411b3c2dfb80c14b6232"); 
            requestBody.put("data", encrypted); 

            Log.w(TAG, "Encrypted payload: " + requestBody.toString());

            // Request request = new Request.Builder()
            //         .url(ENCRYPTED_POST_URL)
            //         .post(requestBody)
            //         .build();

            // try (Response response = client.newCall(request).execute()) {
            //     boolean success = response.isSuccessful();
            //     if (!success) {
            //         Log.w(TAG, "Encrypted POST failed with code: " + response.code());
            //     }
            //     return success;
            //}
        } catch (Exception exception) {
            Log.e(TAG, "Encrypted send failed: " + exception.getMessage());
            return false;
        }
        return false;
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
        try {

            JSONObject requestJson = new JSONObject();

            org.json.JSONArray data = new org.json.JSONArray();
            data.put(bodyJson);
            final String plainJson = data.toString();
            String encrypted = encryptJson(plainJson);

            requestJson.put("token", "79db628d60bf411b3c2dfb80c14b6232");
            requestJson.put("data", encrypted); 

            Log.w(TAG, "data raw: " + plainJson);
            Log.w(TAG, "Encrypted payload: " + requestJson.toString());

            RequestBody requestBody = RequestBody.create(requestJson.toString(), JSON_MEDIA_TYPE);
            Request request = new Request.Builder()
                    .url(ENCRYPTED_POST_URL)
                    .post(requestBody)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Encrypted POST failed: " + e.getMessage());
                    if (callback != null) {
                        callback.onFailure(plainJson, e);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "Encrypted POST unsuccessful: code=" + response.code());
                        if (callback != null) {
                            callback.onFailure(plainJson, null);
                        }
                    } else if (callback != null) {
                        callback.onSuccess();
                        Log.w(TAG, "Encrypted POST successful." + requestBody.toString());
                    }
                    response.close();
                }
            });
        } catch (JSONException | GeneralSecurityException exception) {
            Log.e(TAG, "Encrypted payload error: " + exception.getMessage());
            if (callback != null) {
                callback.onFailure(null, exception);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // private String encryptJson(String plainJson) throws GeneralSecurityException {
    //     PublicKey publicKey = loadPublicKey(SAMPLE_PUBLIC_KEY_BASE64);
    //     return RsaCryptoJava.encrypt(plainJson, publicKey);
    // }
    private static final String ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
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
