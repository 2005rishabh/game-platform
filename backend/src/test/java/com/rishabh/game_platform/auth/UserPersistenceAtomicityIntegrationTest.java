package com.rishabh.game_platform.auth;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class UserPersistenceAtomicityIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("IT-02-A: Database rejects duplicate email addresses")
    void rejectsDuplicateEmails() {
        String suffix = UUID.randomUUID().toString();
        String email = "duplicate-" + suffix + "@test.invalid";
        userRepository.saveAndFlush(user("first-" + suffix, email));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user("second-" + suffix, email)));
    }

    @Test
    @DisplayName("IT-02-B: Transaction rollback restores both player ratings atomically")
    void rollsBackBothRatingUpdatesWhenTransactionFails() {
        String suffix = UUID.randomUUID().toString();
        UserEntity white = userRepository.saveAndFlush(
                user("white-" + suffix, "white-" + suffix + "@test.invalid"));
        UserEntity black = userRepository.saveAndFlush(
                user("black-" + suffix, "black-" + suffix + "@test.invalid"));

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            white.setEloRating(1216);
            black.setEloRating(1184);
            userRepository.saveAllAndFlush(List.of(white, black));
            throw new IllegalStateException("Simulated failure before transaction commit");
        }));

        assertEquals(1200, userRepository.findById(white.getId()).orElseThrow().getEloRating());
        assertEquals(1200, userRepository.findById(black.getId()).orElseThrow().getEloRating());
    }

    private UserEntity user(String username, String email) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("password123");
        user.setEloRating(1200);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }
}
