package com.urbansidequest.backend.domain.vo;

import java.util.UUID;

public record CurrentUserVO(UUID id, String phone, String nickname, String avatarUrl) {
}
