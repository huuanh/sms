package com.example.smsforwarder;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "failed_sms")
public class FailedSmsEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "payload")
    private final String payload;

    @ColumnInfo(name = "created_at")
    private final long createdAt;

    public FailedSmsEntity(String payload, long createdAt) {
        this.payload = payload;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
