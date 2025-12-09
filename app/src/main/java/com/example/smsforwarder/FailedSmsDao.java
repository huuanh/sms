package com.example.smsforwarder;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FailedSmsDao {
    @Query("SELECT * FROM failed_sms ORDER BY created_at ASC")
    List<FailedSmsEntity> getAllFailed();

    @Insert
    long insert(FailedSmsEntity entity);

    @Delete
    void delete(FailedSmsEntity entity);
}
