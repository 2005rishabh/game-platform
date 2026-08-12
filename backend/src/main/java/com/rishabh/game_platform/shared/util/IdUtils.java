package com.rishabh.game_platform.shared.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class IdUtils {

    public static Long toLongId(String input) {
        if (input == null || input.isBlank()) {
            return System.currentTimeMillis();
        }

        try {
            // If the string is already a valid UUID format (e.g.
            // "123e4567-e89b-12d3-a456-426614174000")
            return Long.parseLong(input);
        } catch (IllegalArgumentException e) {
            // Fallback: Convert ANY string (e.g., "Guest_a1b2c3" or "rishabh") into a
            // valid, consistent UUID
            UUID uid = UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
            return Math.abs(uid.getMostSignificantBits());
        }
    }
}