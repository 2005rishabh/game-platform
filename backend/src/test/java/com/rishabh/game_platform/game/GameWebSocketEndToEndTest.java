package com.rishabh.game_platform.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.rishabh.game_platform.game.api.dto.MoveRequest;
import com.rishabh.game_platform.game.application.service.GameService;
import com.rishabh.game_platform.game.domain.enums.GameType;
import com.rishabh.game_platform.game.domain.model.GameSession;
import com.rishabh.game_platform.game.domain.model.Player;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class GameWebSocketEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GameService gameService;

    @Test
    @DisplayName("IT-04: Broadcasts move updates to all connected players")
    void broadcastsMoveToBothConnectedClients() throws Exception {
        Player white = Player.builder()
            .userId(UUID.randomUUID())
            .username("white-" + UUID.randomUUID())
            .build();
        Player black = Player.builder()
            .userId(UUID.randomUUID())
            .username("black-" + UUID.randomUUID())
            .build();
        GameSession game = gameService.createGame(white, GameType.CHESS);
        gameService.joinGame(game.getGameId(), black);

        List<Transport> transports = List.of(
            new WebSocketTransport(new StandardWebSocketClient())
        );
        WebSocketStompClient stompClient = new WebSocketStompClient(
            new SockJsClient(transports)
        );
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();
        stompClient.setTaskScheduler(scheduler);
        StompSession clientA = null;
        StompSession clientB = null;

        try {
            String endpoint = "http://localhost:" + port + "/ws";
            clientA = connect(stompClient, endpoint);
            clientB = connect(stompClient, endpoint);

            LinkedBlockingQueue<GameSession> clientAUpdates =
                new LinkedBlockingQueue<>();
            LinkedBlockingQueue<GameSession> clientBUpdates =
                new LinkedBlockingQueue<>();
            subscribe(clientA, game.getGameId().toString(), clientAUpdates);
            subscribe(clientB, game.getGameId().toString(), clientBUpdates);
            String stateDestination =
                "/app/game/" + game.getGameId() + "/state";
            GameSession initialA = requestStateUntilReceived(
                clientA,
                stateDestination,
                clientAUpdates
            );
            GameSession initialB = requestStateUntilReceived(
                clientB,
                stateDestination,
                clientBUpdates
            );
            assertNotNull(initialA);
            assertNotNull(initialB);

            MoveRequest illegalMove = new MoveRequest();
            illegalMove.setUsername(white.getUsername());
            illegalMove.setFrom("a1");
            illegalMove.setTo("b2");
            clientA.send(
                "/app/game/" + game.getGameId() + "/move",
                illegalMove
            );

            GameSession afterIllegalMove = clientBUpdates.poll(
                10,
                TimeUnit.SECONDS
            );
            assertNotNull(afterIllegalMove);
            assertEquals(
                initialA.getState().getBoardState(),
                afterIllegalMove.getState().getBoardState()
            );
            assertEquals("WHITE", afterIllegalMove.getState().getCurrentTurn());

            MoveRequest move = new MoveRequest();
            move.setUsername(white.getUsername());
            move.setFrom("e2");
            move.setTo("e4");
            clientA.send("/app/game/" + game.getGameId() + "/move", move);

            GameSession received = clientBUpdates.poll(10, TimeUnit.SECONDS);
            assertNotNull(
                received,
                "The second client should receive the room broadcast"
            );
            assertEquals(game.getGameId(), received.getGameId());
            assertEquals("BLACK", received.getState().getCurrentTurn());
            assertEquals(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                received.getState().getBoardState()
            );
        } finally {
            if (clientA != null) {
                clientA.disconnect();
            }
            if (clientB != null) {
                clientB.disconnect();
            }
            stompClient.stop();
            scheduler.shutdown();
        }
    }

    private StompSession connect(WebSocketStompClient client, String endpoint)
        throws Exception {
        return client
            .connectAsync(endpoint, new StompSessionHandlerAdapter() {})
            .toCompletableFuture()
            .get(20, TimeUnit.SECONDS);
    }

    private void subscribe(
        StompSession session,
        String gameId,
        LinkedBlockingQueue<GameSession> received
    ) {
        session.subscribe(
            "/topic/game/" + gameId,
            new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return GameSession.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    received.offer((GameSession) payload);
                }
            }
        );
    }

    private GameSession requestStateUntilReceived(
        StompSession session,
        String destination,
        LinkedBlockingQueue<GameSession> received
    ) throws InterruptedException {
        GameSession existing = received.poll(500, TimeUnit.MILLISECONDS);
        if (existing != null) {
            return existing;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            session.send(destination, "");
            GameSession state = received.poll(2, TimeUnit.SECONDS);
            if (state != null) {
                return state;
            }
        }
        return null;
    }
}
