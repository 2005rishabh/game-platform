package com.rishabh.game_platform.game.application.service;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserEntity;
import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.events.GameEndedEvent;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import com.rishabh.game_platform.game.infrastructure.persistence.jpa.SpringDataProcessedGameEventRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(
        GameEventConsumer.class
    );
    private static final int K_FACTOR = 32;

    private final GameStateRepository gameStateRepository;
    private final UserRepository userRepository;
    private final SpringDataProcessedGameEventRepository processedEventRepository;

    public GameEventConsumer(
        GameStateRepository gameStateRepository,
        UserRepository userRepository
    ) {
        this(gameStateRepository, userRepository, null);
    }

    @Autowired
    public GameEventConsumer(
        GameStateRepository gameStateRepository,
        UserRepository userRepository,
        @Autowired(
            required = false
        ) SpringDataProcessedGameEventRepository processedEventRepository
    ) {
        this.gameStateRepository = gameStateRepository;
        this.userRepository = userRepository;
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    @KafkaListener(topics = "game-ended-topic", groupId = "game-platform-group")
    public void consumeGameEndedEvent(GameEndedEvent event) {
        if (event == null || event.gameId() == null) {
            log.warn("Received null or invalid GameEndedEvent from Kafka");
            return;
        }

        // 1. Atomic database idempotency registration via unique constraint and affected row count
        if (processedEventRepository != null) {
            int inserted = processedEventRepository.insertIfNotExists(
                event.gameId(),
                Instant.now()
            );
            if (inserted == 0) {
                log.info(
                    "Duplicate GameEndedEvent for gameId {} ignored (already processed in database).",
                    event.gameId()
                );
                return;
            }
        } else {
            // State-level Idempotency Check fallback when processedEventRepository is not configured
            Optional<GameSession> existingSession =
                gameStateRepository.findById(event.gameId());
            if (existingSession.isPresent()) {
                GameStatus currentStatus = existingSession.get().getStatus();
                if (isTerminalStatus(currentStatus)) {
                    log.info(
                        "GameSession {} already concluded with status {}. Ignoring duplicate event.",
                        event.gameId(),
                        currentStatus
                    );
                    return;
                }
            }
        }

        log.info(
            "KAFKA CONSUMER CAUGHT EVENT: Game {} ended. Winner: {}, Loser: {}, Reason: {}",
            event.gameId(),
            event.winnerId(),
            event.loserId(),
            event.reason()
        );

        // 2. Update GameSession status in repository using valid GameStatus enum
        Optional<GameSession> sessionOpt = gameStateRepository.findById(
            event.gameId()
        );
        sessionOpt.ifPresentOrElse(
            session -> {
                boolean isDraw =
                    "DRAW".equalsIgnoreCase(event.reason()) ||
                    "STALEMATE".equalsIgnoreCase(event.reason());
                if (isDraw) {
                    session.setStatus(GameStatus.DRAW);
                } else if (
                    session.getPlayer1() != null &&
                    event.winnerId() != null &&
                    (event
                        .winnerId()
                        .equalsIgnoreCase(session.getPlayer1().getUsername()) ||
                        event
                            .winnerId()
                            .equalsIgnoreCase(
                                String.valueOf(session.getPlayer1().getUserId())
                            ))
                ) {
                    session.setStatus(GameStatus.WHITE_WON);
                } else if (
                    session.getPlayer2() != null &&
                    event.winnerId() != null &&
                    (event
                        .winnerId()
                        .equalsIgnoreCase(session.getPlayer2().getUsername()) ||
                        event
                            .winnerId()
                            .equalsIgnoreCase(
                                String.valueOf(session.getPlayer2().getUserId())
                            ))
                ) {
                    session.setStatus(GameStatus.BLACK_WON);
                } else {
                    try {
                        GameStatus status = GameStatus.valueOf(
                            event.reason().toUpperCase()
                        );
                        session.setStatus(status);
                    } catch (Exception e) {
                        session.setStatus(GameStatus.WHITE_WON);
                    }
                }
                gameStateRepository.save(session);
                log.info(
                    "Successfully updated GameSession {} status to {}",
                    event.gameId(),
                    session.getStatus()
                );
            },
            () ->
                log.warn(
                    "GameSession {} not found when consuming GameEndedEvent",
                    event.gameId()
                )
        );

        // 3. Calculate ELO changes and update user ratings in database
        updateUserRatings(event);
    }

    private boolean isTerminalStatus(GameStatus status) {
        return (
            status == GameStatus.WHITE_WON ||
            status == GameStatus.BLACK_WON ||
            status == GameStatus.DRAW ||
            status == GameStatus.ABANDONED
        );
    }

    private void updateUserRatings(GameEndedEvent event) {
        Optional<UserEntity> winnerOpt = findUser(event.winnerId());
        Optional<UserEntity> loserOpt = findUser(event.loserId());

        if (winnerOpt.isEmpty() || loserOpt.isEmpty()) {
            log.warn(
                "Skipping Elo calculation. Winner found: {}, Loser found: {}",
                winnerOpt.isPresent(),
                loserOpt.isPresent()
            );
            return;
        }

        UserEntity winner = winnerOpt.get();
        UserEntity loser = loserOpt.get();

        int winnerElo =
            winner.getEloRating() != null ? winner.getEloRating() : 1200;
        int loserElo =
            loser.getEloRating() != null ? loser.getEloRating() : 1200;

        boolean isDraw =
            "DRAW".equalsIgnoreCase(event.reason()) ||
            "STALEMATE".equalsIgnoreCase(event.reason());

        double expectedWinner =
            1.0 / (1.0 + Math.pow(10, (loserElo - winnerElo) / 400.0));
        double expectedLoser =
            1.0 / (1.0 + Math.pow(10, (winnerElo - loserElo) / 400.0));

        double actualWinner = isDraw ? 0.5 : 1.0;
        double actualLoser = isDraw ? 0.5 : 0.0;

        int newWinnerElo = (int) Math.round(
            winnerElo + K_FACTOR * (actualWinner - expectedWinner)
        );
        int newLoserElo = (int) Math.round(
            loserElo + K_FACTOR * (actualLoser - expectedLoser)
        );

        winner.setEloRating(newWinnerElo);
        loser.setEloRating(newLoserElo);

        userRepository.save(winner);
        userRepository.save(loser);

        log.info(
            "Updated ELO Ratings for Game {}: {} ({} -> {}), {} ({} -> {})",
            event.gameId(),
            winner.getUsername(),
            winnerElo,
            newWinnerElo,
            loser.getUsername(),
            loserElo,
            newLoserElo
        );
    }

    private Optional<UserEntity> findUser(String idOrUsername) {
        if (idOrUsername == null || idOrUsername.isBlank()) {
            return Optional.empty();
        }

        Optional<UserEntity> userOpt = userRepository.findByUsername(
            idOrUsername
        );
        if (userOpt.isPresent()) {
            return userOpt;
        }

        try {
            Long id = Long.parseLong(idOrUsername);
            return userRepository.findById(id);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
