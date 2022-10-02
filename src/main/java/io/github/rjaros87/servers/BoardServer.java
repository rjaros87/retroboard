package io.github.rjaros87.servers;

import io.github.rjaros87.model.BoardMessage;
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
@ServerWebSocket(value = "/meeting/board/{id}/{user}")
public class BoardServer {
    private final WebSocketBroadcaster broadcaster;

    public BoardServer(WebSocketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @OnOpen
    @Produces(MediaType.APPLICATION_JSON)
    public Publisher<BoardMessage> onOpen(String id, String user, WebSocketSession session) {
        String msg = user + " Joined!";
        log.info("Msg: {}, session {}", msg, session.getId());

        return broadcaster.broadcast(new BoardMessage(id, user, msg),
                isValidRoom(id, session));
    }

    @OnMessage
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Publisher<String> onMessage(String id, String message, WebSocketSession session) {
        String msg = "[" + id + "] " + message;
        log.info("Got message from client {}", msg);

        String response = "{\"server\": " + message + "}";


        return broadcaster.broadcast(response, MediaType.TEXT_PLAIN_TYPE, isValidRoom(id, session));
    }

    @OnClose
    public Publisher<String> onClose(String id, String user, WebSocketSession session, CloseReason closeReason) {
        log.info("[{}, {}] Disconnected! CloseReason: {}", id, user, closeReason);

        session.close(closeReason);

        String response = "{\"server\": {\"username\":\"" + user + "\", \"body\": \"Disconnected\"}}";

        return broadcaster.broadcast(response, isValidRoom(id, session));
    }

    private Predicate<WebSocketSession> isValidRoom(String id, WebSocketSession session) {
        return s -> {
//            boolean sessionEq = Objects.equals(s.getId(), session.getId());
            var gameEq = id.equals(s.getUriVariables().get("id", String.class, null));
//            boolean sessionIsOpen = session.isOpen();
//            return  sessionEq && gameEq && sessionIsOpen;
            return  gameEq;
        };
    }
}
