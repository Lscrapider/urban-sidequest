package com.urbansidequest.backend.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbansidequest.backend.domain.po.UserPO;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AuthProperties authProperties;

    private final ObjectMapper objectMapper;

    public JwtTokenProvider(AuthProperties authProperties, ObjectMapper objectMapper) {
        this.authProperties = authProperties;
        this.objectMapper = objectMapper;
    }

    public String createAccessToken(UserPO user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + this.authProperties.getJwt().getAccessTokenValiditySeconds();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", this.authProperties.getJwt().getIssuer());
        payload.put("sub", user.getId().toString());
        payload.put("phone", user.getPhone());
        payload.put("iat", issuedAt);
        payload.put("exp", expiresAt);

        String unsignedToken = this.encodeJson(header) + "." + this.encodeJson(payload);
        return unsignedToken + "." + this.sign(unsignedToken);
    }

    public Optional<AuthenticatedUser> parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            String unsignedToken = parts[0] + "." + parts[1];
            if (!this.sign(unsignedToken).equals(parts[2])) {
                return Optional.empty();
            }

            Map<String, Object> payload = this.objectMapper.readValue(this.decode(parts[1]), MAP_TYPE);
            if (!this.authProperties.getJwt().getIssuer().equals(payload.get("iss"))) {
                return Optional.empty();
            }
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                return Optional.empty();
            }

            UUID userId = UUID.fromString(String.valueOf(payload.get("sub")));
            String phone = String.valueOf(payload.get("phone"));
            return Optional.of(new AuthenticatedUser(userId, phone));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public long getAccessTokenValiditySeconds() {
        return this.authProperties.getJwt().getAccessTokenValiditySeconds();
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(this.objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 内容序列化失败", exception);
        }
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    this.authProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 签名失败", exception);
        }
    }
}
