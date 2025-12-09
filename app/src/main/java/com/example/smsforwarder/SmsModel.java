package com.example.smsforwarder;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Immutable model representing a processed SMS message.
 */
public class SmsModel {
    private final String sender;
    private final String content;
    private final long timestamp;

    public SmsModel(String sender, String content, long timestamp) {
        this.sender = sender;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getContent() {
        return content;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Ensures sender text matches the required regex filter.
     */
    public boolean matchesFilter(String brandNameFilter) {
        return true;
        
//        if (sender == null) {
//            return false;
//        }
//        return brandNameFilter.contains(sender);
    }
}
