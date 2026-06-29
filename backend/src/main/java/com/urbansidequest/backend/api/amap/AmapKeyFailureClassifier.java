package com.urbansidequest.backend.api.amap;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

@Component
public class AmapKeyFailureClassifier {

    private static final String STATUS_SUCCESS = "1";

    private static final String QUOTA_LIMIT_ERROR_CODE = "10044";

    public Classification classify(JsonNode response) {
        if (response == null || STATUS_SUCCESS.equals(text(response, "status"))) {
            return Classification.none();
        }

        if (containsQuotaLimitErrorCode(text(response, "info"))
                || containsQuotaLimitErrorCode(text(response, "infocode"))) {
            return new Classification(FailureType.QUOTA_EXHAUSTED, reason(response));
        }

        return Classification.none();
    }

    private static boolean containsQuotaLimitErrorCode(String value) {
        return value != null && value.contains(QUOTA_LIMIT_ERROR_CODE);
    }

    private static String reason(JsonNode response) {
        String infoCode = text(response, "infocode");
        String info = text(response, "info");
        if (infoCode == null && info == null) {
            return "高德返回 key 相关错误";
        }
        if (infoCode == null) {
            return info;
        }
        if (info == null) {
            return infoCode;
        }
        return infoCode + ":" + info;
    }

    private static String text(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public enum FailureType {
        NONE,
        QUOTA_EXHAUSTED
    }

    public record Classification(FailureType type, String reason) {

        public static Classification none() {
            return new Classification(FailureType.NONE, null);
        }

        public boolean shouldDisableKey() {
            return this.type != FailureType.NONE;
        }
    }
}
