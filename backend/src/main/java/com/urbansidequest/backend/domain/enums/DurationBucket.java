package com.urbansidequest.backend.domain.enums;

public enum DurationBucket {
    SHORT,
    HALF_DAY,
    FULL_DAY;

    public static DurationBucket fromMinutes(int durationMinutes) {
        if (durationMinutes <= 180) {
            return SHORT;
        }
        if (durationMinutes <= 360) {
            return HALF_DAY;
        }
        return FULL_DAY;
    }
}
