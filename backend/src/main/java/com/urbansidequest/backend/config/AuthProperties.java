package com.urbansidequest.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private final Jwt jwt = new Jwt();

    private String devVerificationCode;

    public Jwt getJwt() {
        return this.jwt;
    }

    public String getDevVerificationCode() {
        return this.devVerificationCode;
    }

    public void setDevVerificationCode(String devVerificationCode) {
        this.devVerificationCode = devVerificationCode;
    }

    public static class Jwt {

        private String issuer;

        private String secret;

        private long accessTokenValiditySeconds;

        public String getIssuer() {
            return this.issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getSecret() {
            return this.secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getAccessTokenValiditySeconds() {
            return this.accessTokenValiditySeconds;
        }

        public void setAccessTokenValiditySeconds(long accessTokenValiditySeconds) {
            this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        }
    }
}
