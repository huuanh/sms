package com.example.smsforwarder;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

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
        for (FailedSmsEntity entity : failed) {
            final FailedSmsEntity cachedEntity = entity;
            try {
                JSONObject bodyJson = new JSONObject(entity.getPayload());
                apiClient.sendEncryptedAsync(bodyJson, new ApiClient.SendCallback() {
                    @Override
                    public void onSuccess() {
                        failedSmsDao.delete(cachedEntity);
                    }

                    @Override
                    public void onFailure(String plainJson, Exception exception) {
                        if (exception != null) {
                            Log.w(TAG, "Retry still failing for id=" + cachedEntity.getId() + ": " + exception.getMessage());
                        } else {
                            Log.w(TAG, "Retry still failing for id=" + cachedEntity.getId());
                        }
                    }
                });
            } catch (JSONException exception) {
                Log.e(TAG, "Corrupt cached payload; dropping id=" + cachedEntity.getId());
                failedSmsDao.delete(cachedEntity);
            }
        }
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
}
