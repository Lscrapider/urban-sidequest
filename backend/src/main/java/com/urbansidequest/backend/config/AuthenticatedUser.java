package com.urbansidequest.backend.config;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String phone) {
}
