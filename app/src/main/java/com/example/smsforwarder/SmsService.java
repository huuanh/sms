package com.example.smsforwarder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.telephony.SubscriptionManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsService extends Service {
    public static final String ACTION_PROCESS_SMS = "com.example.smsforwarder.action.PROCESS_SMS";
    public static final String EXTRA_SENDER = "extra_sender";
    public static final String EXTRA_BODY = "extra_body";
    public static final String EXTRA_TIMESTAMP = "extra_timestamp";
    public static final String EXTRA_SUBSCRIPTION_ID = "extra_subscription_id";

    private static final String TAG = "SmsService";
    private static final String BRAND_NAME_FILTER = "(abc|xyz)";
    private static final String CHANNEL_ID = "sms_service_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ExecutorService executorService;
    private RetryManager retryManager;
    private SimInfoManager simInfoManager;
    private ApiClient apiClient;
    private boolean isForeground;

    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        LocalDatabase database = LocalDatabase.getInstance(this);
        apiClient = new ApiClient();
        retryManager = new RetryManager(database.failedSmsDao(), apiClient);
        simInfoManager = new SimInfoManager();
        simInfoManager.refresh(this);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_PROCESS_SMS.equals(intent.getAction())) {
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        ensureForeground();

        final String sender = intent.getStringExtra(EXTRA_SENDER);
        final String body = intent.getStringExtra(EXTRA_BODY);
        final long timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
        final int subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        executorService.execute(() -> handleIncomingSms(startId, sender, body, timestamp, subscriptionId));
        return START_NOT_STICKY;
    }

    private void handleIncomingSms(int startId, String sender, String body, long timestamp, int subscriptionId) {
        retryManager.retryFailedMessages();
        simInfoManager.refresh(this);

        if (TextUtils.isEmpty(sender) || body == null) {
            Log.w(TAG, "Missing SMS data; stopping processing.");
            stopSelfResult(startId);
            return;
        }

        SmsModel sms = new SmsModel(sender, body, timestamp);
        if (!sms.matchesFilter(BRAND_NAME_FILTER)) {
            Log.d(TAG, "Sender does not pass filter: " + sender);
            stopSelfResult(startId);
            return;
        }

        SimInfoManager.SimEntry simEntry = simInfoManager.getEntryForSubscription(subscriptionId);
        int slotIndex = simEntry != null ? simEntry.slotIndex : SubscriptionManager.INVALID_SIM_SLOT_INDEX;
        String receiverNumber = simInfoManager.getReceiverNumber(slotIndex);
        String receiverIccid = simEntry != null ? simEntry.iccid : null;
        if (TextUtils.isEmpty(receiverNumber)) {
            Log.w(TAG, "Receiver number not configured.");
            stopSelfResult(startId);
            return;
        }

        try {
            org.json.JSONObject bodyJson = apiClient.buildBodyJson(sms, receiverNumber, receiverIccid);
            retryManager.processBodyJson(bodyJson);
        } catch (JSONException exception) {
            Log.e(TAG, "Failed to build SMS body JSON: " + exception.getMessage());
        }
        stopSelfResult(startId);
    }

    private void ensureForeground() {
        if (isForeground) {
            return;
        }
        Notification notification = createServiceNotification();
        startForeground(NOTIFICATION_ID, notification);
        isForeground = true;
    }

    private Notification createServiceNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_notification_message))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "SMS Processing",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Handles foreground service notifications for SMS processing.");
        manager.createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (isForeground) {
            stopForeground(true);
            isForeground = false;
        }
        executorService.shutdownNow();
    }
}
