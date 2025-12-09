package com.example.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;
import android.telephony.SubscriptionManager;

/**
 * Receives incoming SMS intents and forwards them to the background service.
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null || messages.length == 0) {
            Log.w(TAG, "No SMS messages found in intent.");
            return;
        }

        String sender = messages[0].getDisplayOriginatingAddress();
        StringBuilder bodyBuilder = new StringBuilder();
        for (SmsMessage smsMessage : messages) {
            if (smsMessage != null) {
                bodyBuilder.append(smsMessage.getMessageBody());
            }
        }

        int subscriptionId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        Intent serviceIntent = new Intent(context, SmsService.class);
        serviceIntent.setAction(SmsService.ACTION_PROCESS_SMS);
        serviceIntent.putExtra(SmsService.EXTRA_SENDER, sender);
        serviceIntent.putExtra(SmsService.EXTRA_BODY, bodyBuilder.toString());
        serviceIntent.putExtra(SmsService.EXTRA_TIMESTAMP, System.currentTimeMillis());
        serviceIntent.putExtra(SmsService.EXTRA_SUBSCRIPTION_ID, subscriptionId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
