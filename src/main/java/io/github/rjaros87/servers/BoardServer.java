package io.github.rjaros87.servers;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;

import java.util.function.Predicate;

/**
 * Server responsible for team retrospective meeting.
 * Major tasks are exchanging notes between connected participants and storing results in DB/Cache.
 */
@Slf4j
@Singleton
@ServerWebSocket(value = "/meeting/board/{token}/{user}")
public class BoardServer {
    private final WebSocketBroadcaster broadcaster;

    public BoardServer(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    @Produces(MediaType.APPLICATION_JSON)
    public Publisher<String> onOpen(String token, String user, WebSocketSession session) {
        String msg = user + " Joined!";
        log.info("Msg: {}, session {}", msg, session.getId());

        return broadcaster.broadcast("{\"room\": \"" + token + "\", \"user\": \"" + user + "\"}",
                isValidRoom(token, session));
    }

    @OnMessage
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Publisher<String> onMessage(String token, String message, WebSocketSession session) {
        String msg = "[" + token + "] " + message;
        log.info("Got message from client {}", msg);

        String response = "{\"server\": " + message + "}";


        return broadcaster.broadcast(response, MediaType.TEXT_PLAIN_TYPE, isValidRoom(token, session));
    }

    @OnClose
    public Publisher<String> onClose(String token, String user, WebSocketSession session, CloseReason closeReason) {
        log.info("[{}, {}] Disconnected! CloseReason: {}", token, user, closeReason);

        session.close(closeReason);

        String response = "{\"server\": {\"username\":\"" + user + "\", \"body\": \"Disconnected\"}}";

        return broadcaster.broadcast(response, isValidRoom(token, session));
    }

    private Predicate<WebSocketSession> isValidRoom(String token, WebSocketSession session) {
        return s -> {
//            boolean sessionEq = Objects.equals(s.getId(), session.getId());
            var gameEq = token.equals(s.getUriVariables().get("token", String.class, null));
//            boolean sessionIsOpen = session.isOpen();
//            return  sessionEq && gameEq && sessionIsOpen;
            return  gameEq;
        };
    }
}
