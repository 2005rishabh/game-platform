package com.rishabh.game_platform.shared.util;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class IdUtils {

    // Preserve existing behaviour for compatibility when Long was expected
    public static Long toLongId(String input) {
        if (input == null || input.isBlank()) {
            return System.currentTimeMillis();
        }

        try {
            return Long.parseLong(input);
        } catch (IllegalArgumentException e) {
            UUID uid = UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
            return Math.abs(uid.getMostSignificantBits());
        }
    }

    // Convert a free-form string (or UUID string) into a UUID
    public static UUID toUuid(String input) {
        if (input == null || input.isBlank()) {
            return UUID.randomUUID();
        }

        try {
            return UUID.fromString(input);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(input.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Convert a database Long id into a UUID deterministically
    public static UUID fromLong(Long id) {
        if (id == null) {
            return UUID.randomUUID();
        }
        return UUID.nameUUIDFromBytes(id.toString().getBytes(StandardCharsets.UTF_8));
    }
}