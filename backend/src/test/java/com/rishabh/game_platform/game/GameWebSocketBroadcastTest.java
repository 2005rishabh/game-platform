package com.rishabh.game_platform.game;

import com.rishabh.game_platform.auth.infrastructure.persistence.UserRepository;
import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.api.websocket.GameWebSocketController;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameStatus;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.GameState;
import com.rishabh.game_platform.game.domain.model.Player;
import com.rishabh.game_platform.game.domain.ports.GameStateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameWebSocketBroadcastTest {

    @Test
    @DisplayName("E2E-01 contract: A successful move broadcasts the authoritative state to the game topic")
    void broadcastsSuccessfulMoveToGameTopic() {
        GameService gameService = mock(GameService.class);
        SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
        UserRepository userRepository = mock(UserRepository.class);
        GameStateRepository gameStateRepository = mock(GameStateRepository.class);
        GameWebSocketController controller = new GameWebSocketController(
                gameService, messagingTemplate, userRepository, gameStateRepository);

        UUID gameId = UUID.randomUUID();
        GameSession updatedSession = GameSession.builder()
                .gameId(gameId)
                .status(GameStatus.IN_PROGRESS)
                .state(GameState.builder().boardState("updated-fen").currentTurn("BLACK").build())
                .build();
        MoveRequest moveRequest = new MoveRequest();
        moveRequest.setFrom("e2");
        moveRequest.setTo("e4");
        Principal principal = () -> "white-player";
        when(gameService.executeMove(
                eq(gameId),
                org.mockito.ArgumentMatchers.any(Player.class),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(updatedSession);

        controller.executeMove(gameId, moveRequest, principal);

        verify(messagingTemplate).convertAndSend("/topic/game/" + gameId, updatedSession);
    }
}
