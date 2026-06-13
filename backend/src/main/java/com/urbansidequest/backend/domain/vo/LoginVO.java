package com.urbansidequest.backend.domain.vo;

public record LoginVO(String tokenType, String accessToken, long expiresIn, CurrentUserVO user) {
}
