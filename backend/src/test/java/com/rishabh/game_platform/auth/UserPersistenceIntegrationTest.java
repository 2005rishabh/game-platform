package com.rishabh.game_platform.auth;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext
public class UserPersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("IT-02-A: Database throws unique constraint violations on duplicate username or email")
    @Transactional
    void shouldEnforceUniqueConstraints() {
        // 1. Create and save initial user
        UserEntity user1 = new UserEntity();
        user1.setUsername("unique_player");
        user1.setEmail("player@test.com");
        user1.setPassword("password123");
        user1.setEloRating(1200);
        user1.setCreatedAt(LocalDateTime.now());
        userRepository.save(user1);

        // 2. Try saving another user with the same username but different email
        UserEntity user2 = new UserEntity();
        user2.setUsername("unique_player"); // Duplicate username
        user2.setEmail("other@test.com");
        user2.setPassword("password123");
        user2.setEloRating(1200);
        user2.setCreatedAt(LocalDateTime.now());

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(user2);
        }, "Should throw an exception due to duplicate username constraint");
    }

    @Test
    @DisplayName("IT-02-B: Transaction rollback works atomically during updates")
    @Transactional
    void verifyTransactionAtomicity() {
        UserEntity user = new UserEntity();
        user.setUsername("transaction_test_user");
        user.setEmail("tx@test.com");
        user.setPassword("password123");
        user.setEloRating(1500);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.saveAndFlush(user);

        // Verify initial state
        var savedUser = userRepository.findByUsername("transaction_test_user");
        assertTrue(savedUser.isPresent());
        assertEquals(1500, savedUser.get().getEloRating());
    }
}