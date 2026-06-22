package com.urbansidequest.backend.domain.constant;

import java.time.format.DateTimeFormatter;

public final class DateTimeFormatConstant {

    public static final String BEIJING_LOCAL_DATE_TIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";

    public static final DateTimeFormatter BEIJING_LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern(BEIJING_LOCAL_DATE_TIME_PATTERN);

    private DateTimeFormatConstant() {
    }
}
