package com.example.smsforwarder;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates retry logic for failed SMS payload submissions.
 */
public class RetryManager {
    private static final String TAG = "RetryManager";

    private final FailedSmsDao failedSmsDao;
    private final ApiClient apiClient;

    public RetryManager(FailedSmsDao failedSmsDao, ApiClient apiClient) {
        this.failedSmsDao = failedSmsDao;
        this.apiClient = apiClient;
    }

    public void retryFailedMessages() {
        List<FailedSmsEntity> failed = failedSmsDao.getAllFailed();
        if (failed.isEmpty()) {
            return;
        }
        JSONArray batchedPayloads = new JSONArray();
        List<FailedSmsEntity> processedEntities = new ArrayList<>();

        for (FailedSmsEntity entity : failed) {
            try {
                appendPayload(entity.getPayload(), batchedPayloads);
                processedEntities.add(entity);
            } catch (JSONException exception) {
                Log.e(TAG, "Corrupt cached payload; dropping id=" + entity.getId());
                failedSmsDao.delete(entity);
            }
        }

        if (batchedPayloads.length() == 0) {
            return;
        }

        apiClient.sendEncryptedListAsync(batchedPayloads, new ApiClient.SendCallback() {
            @Override
            public void onSuccess() {
                for (FailedSmsEntity entity : processedEntities) {
                    failedSmsDao.delete(entity);
                }
            }

            @Override
            public void onFailure(String plainJson, Exception exception) {
                if (exception != null) {
                    Log.w(TAG, "Batch retry still failing: " + exception.getMessage());
                } else {
                    Log.w(TAG, "Batch retry still failing.");
                }
            }
        });
    }

    public void processBodyJson(JSONObject bodyJson) {
        if (bodyJson == null) {
            return;
        }
        apiClient.sendEncryptedAsync(bodyJson, new ApiClient.SendCallback() {
            @Override
            public void onSuccess() {
                // no-op
            }

            @Override
            public void onFailure(String plainJson, Exception exception) {
                if (plainJson != null) {
                    cacheFailedPayload(plainJson);
                }
                if (exception != null) {
                    Log.e(TAG, "Encrypted send failure: " + exception.getMessage());
                }
            }
        });
    }

    private void cacheFailedPayload(String payload) {
        if (payload == null) {
            return;
        }
        FailedSmsEntity entity = new FailedSmsEntity(payload, System.currentTimeMillis());
        failedSmsDao.insert(entity);
    }

    private void appendPayload(String payload, JSONArray accumulator) throws JSONException {
        if (payload == null) {
            throw new JSONException("Payload is null");
        }
        String trimmed = payload.trim();
        if (trimmed.isEmpty()) {
            throw new JSONException("Payload is empty");
        }
        if (trimmed.startsWith("[")) {
            JSONArray array = new JSONArray(trimmed);
            if (array.length() == 0) {
                throw new JSONException("Empty payload array");
            }
            for (int index = 0; index < array.length(); index++) {
                JSONObject element = array.optJSONObject(index);
                if (element == null) {
                    throw new JSONException("Payload array element is not an object");
                }
                accumulator.put(element);
            }
            return;
        }
        accumulator.put(new JSONObject(trimmed));
    }
}
