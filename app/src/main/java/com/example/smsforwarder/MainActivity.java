package com.example.smsforwarder;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.example.smsforwarder.databinding.ActivityMainBinding;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        renderSimInfo();

        if (!PermissionsHelper.hasSmsPermissions(this)) {
            PermissionsHelper.requestSmsPermissions(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSimInfo();
    }

    private void renderSimInfo() {
        if (!PermissionsHelper.hasSmsPermissions(this)) {
            binding.textSimInfo.setText(R.string.permission_rationale);
            return;
        }
        SimInfoManager simInfoManager = new SimInfoManager();
        simInfoManager.refresh(this);
        List<SimInfoManager.SimEntry> entries = simInfoManager.snapshotEntries();
        if (entries.isEmpty()) {
            binding.textSimInfo.setText(R.string.no_sim_detected);
            return;
        }
        Collections.sort(entries, Comparator.comparingInt(entry -> entry.slotIndex));
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.sim_info_title)).append("\n");
        for (int i = 0; i < entries.size(); i++) {
            SimInfoManager.SimEntry entry = entries.get(i);
            String carrier = TextUtils.isEmpty(entry.carrierName) ? getString(R.string.unknown_carrier) : entry.carrierName;
            String number = TextUtils.isEmpty(entry.phoneNumber) ? "-" : entry.phoneNumber;
            String iccid = TextUtils.isEmpty(entry.iccid) ? "-" : entry.iccid;
            builder.append(getString(R.string.sim_info_format,
                    entry.slotIndex + 1,
                    carrier,
                    number,
                    iccid));
            if (i < entries.size() - 1) {
                builder.append("\n\n");
            }
        }
        binding.textSimInfo.setText(builder.toString().trim());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PermissionsHelper.SMS_PERMISSION_REQUEST_CODE) {
            if (!PermissionsHelper.hasSmsPermissions(this)) {
                Toast.makeText(this, R.string.permission_rationale, Toast.LENGTH_LONG).show();
            } else {
                renderSimInfo();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
