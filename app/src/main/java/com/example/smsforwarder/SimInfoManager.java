package com.example.smsforwarder;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches SIM slot metadata to associate subscription identifiers with receiver numbers.
 */
public class SimInfoManager {
    public static final class SimEntry {
        public final int slotIndex;
        public final int subscriptionId;
        public final String phoneNumber;
        public final String carrierName;
        public final String iccid;

        SimEntry(int slotIndex, int subscriptionId, String phoneNumber, String carrierName, String iccid) {
            this.slotIndex = slotIndex;
            this.subscriptionId = subscriptionId;
            this.phoneNumber = phoneNumber;
            this.carrierName = carrierName;
            this.iccid = iccid;
        }
    }

    private final Map<Integer, SimEntry> slotEntries = new HashMap<>();
    private final Map<Integer, Integer> subscriptionToSlot = new HashMap<>();

    public synchronized void refresh(Context context) {
        slotEntries.clear();
        subscriptionToSlot.clear();

        SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
        if (subscriptionManager == null) {
            return;
        }

        if (!hasPhonePermissions(context)) {
            return;
        }

        List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
        if (subscriptions == null) {
            return;
        }

        for (SubscriptionInfo info : subscriptions) {
            if (info == null) {
                continue;
            }
            int slotIndex = info.getSimSlotIndex();
            int subscriptionId = info.getSubscriptionId();
            String number = info.getNumber();
            if (TextUtils.isEmpty(number)) {
                number = "";
            }
            CharSequence displayName = info.getDisplayName();
            CharSequence carrierSequence = displayName != null ? displayName : info.getCarrierName();
            String carrierName = carrierSequence != null ? carrierSequence.toString() : "";
            String iccid;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                iccid = info.getIccId();
            } else {
                iccid = info.getIccId();
            }
            SimEntry entry = new SimEntry(slotIndex, subscriptionId, number, carrierName, iccid);
            slotEntries.put(slotIndex, entry);
            subscriptionToSlot.put(subscriptionId, slotIndex);
        }
    }

    public synchronized Map<Integer, String> snapshotSlotToNumberMap() {
        Map<Integer, String> map = new HashMap<>();
        for (Map.Entry<Integer, SimEntry> entry : slotEntries.entrySet()) {
            map.put(entry.getKey(), entry.getValue().phoneNumber);
        }
        return map;
    }

    public synchronized List<SimEntry> snapshotEntries() {
        return new ArrayList<>(slotEntries.values());
    }

    @Nullable
    public synchronized String getNumberForSubscription(int subscriptionId) {
        SimEntry entry = getEntryForSubscription(subscriptionId);
        return entry != null ? entry.phoneNumber : null;
    }

    @Nullable
    public synchronized String getIccidForSubscription(int subscriptionId) {
        SimEntry entry = getEntryForSubscription(subscriptionId);
        return entry != null ? entry.iccid : null;
    }

    @Nullable
    public synchronized SimEntry getEntryForSubscription(int subscriptionId) {
        Integer slot = subscriptionToSlot.get(subscriptionId);
        if (slot == null) {
            return null;
        }
        return slotEntries.get(slot);
    }

    private boolean hasPhonePermissions(Context context) {
        int readPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE);
        int readPhoneNumbers = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_NUMBERS);
        return readPhoneState == PackageManager.PERMISSION_GRANTED || readPhoneNumbers == PackageManager.PERMISSION_GRANTED;
    }
}
