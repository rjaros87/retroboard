package io.github.rjaros87.servers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.EventMessage;
import io.github.rjaros87.model.EventType;
import io.github.rjaros87.model.UserBoard;
import io.github.rjaros87.model.UserMessage;
import io.github.rjaros87.storage.CacheClient;
import io.github.rjaros87.storage.EventFields;
import io.github.rjaros87.storage.StorageMessage;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Server responsible for team retrospective meeting.
 * Major tasks are exchanging notes between connected participants and storing results in DB/Cache.
 */
@Slf4j
@Singleton
@ServerWebSocket(value = "/meeting/board/{boardId}/{username}")
public class BoardServer {
    private final WebSocketBroadcaster broadcaster;
    private final CacheClient cacheClient;
    private String hostIpAddress;

    public BoardServer(WebSocketBroadcaster broadcaster, CacheClient cacheClient) {
        this.broadcaster = broadcaster;
        this.cacheClient = cacheClient;
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            hostIpAddress = socket.getLocalAddress().getHostAddress();
            log.info("Server IP address: {}", hostIpAddress);
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        registerObserver();
    }

    @OnOpen
    @Consumes(MediaType.APPLICATION_JSON)
    public void onOpen(@RequestBean UserBoard userBoard, WebSocketSession session) {
        log.info("{} Joined!, session {}", userBoard.getUsername(), session.getId());

        cacheClient.sendCardsToConnectedUser(userBoard.getBoardId(), session);
    }

    @OnMessage
    @Consumes(MediaType.APPLICATION_JSON)
    public void onMessage(@RequestBean UserBoard userBoard, EventMessage message, WebSocketSession session) {
        log.info("[{}][{}] Got new message: {}", userBoard.getBoardId(), userBoard.getUsername(), message);

        processEvent(userBoard, message, session);
    }

    /**
     * Function store events from the client into the Cache
     *
     * @param userBoard
     * @param message
     */
    @ExecuteOn(TaskExecutors.IO)
    private void processEvent(UserBoard userBoard, EventMessage message, WebSocketSession session) {
        var cardId = message.getCardId();
        var boardId = userBoard.getBoardId();

        switch (message.getEventType()) {
            case CONNECTED:
            case DISCONNECTED:
                publishEvent(userBoard, message);
                break;
            case DELETE:
                cacheClient.processCardDeletion(boardId, cardId)
                    .subscribe(
                        result -> {
                            log.info("Number of deleted entries: {}", result);
                            publishEvent(userBoard, message, true);
                        }
                    );
                break;
            case LIKE:
                cacheClient.processEmotionEvent(EventFields.LIKE, boardId, userBoard.getUsername(), message)
                    .subscribe(
                        result -> {
                            log.info("Got result from like event: {}", result);
                            message.setContent(result.toString());
                            publishEvent(userBoard, message, true);
                        }
                    );
                break;
            case DISLIKE:
                cacheClient.processEmotionEvent(EventFields.DISLIKE, boardId, userBoard.getUsername(), message)
                    .subscribe(
                        result -> {
                            log.info("Got result from dislike event: {}", result);
                            message.setContent(result.toString());
                            publishEvent(userBoard, message, true);
                        }
                    );
                break;
            case SET:
                cacheClient.storeEvent(EventFields.CONTENT, boardId, cardId, message.getContent(), userBoard.getUsername())
                    .subscribe(result -> {
                            cacheClient.assignCardToBoard(userBoard.getBoardId(), cardId).subscribe();
                            publishEvent(userBoard, message);
                        }
                    );
                break;
            case ASSIGN:
                cacheClient.storeEvent(EventFields.CATEGORY, boardId, cardId, message.getContent())
                    .subscribe(result -> publishEvent(userBoard, message));
                break;
            default:
                throw new UnsupportedOperationException("Unsupported event type: " + message.getEventType());
        }
    }

    /**
     *
     * @param userBoard UserBoard
     * @param message contains object with event details
     * @param forceBroadcast it's kind of confirmation that some process finished successfully and is handled on client
     *                      WS
     */
    public void publishEvent(UserBoard userBoard, EventMessage message, boolean forceBroadcast) {
        var userMessage = UserMessage.builder()
            .userBoard(userBoard)
            .eventMessage(message)
            .build();
        var storageMessage = StorageMessage.builder()
            .userMessage(userMessage)
            .hostIp(hostIpAddress)
            .forceBroadcast(forceBroadcast)
            .build();
        if (!Boolean.parseBoolean(System.getenv("DEBUG"))) {
            log.info("Going to broadcast message on the server");
            broadcaster.broadcastAsync(userMessage, MediaType.APPLICATION_JSON_TYPE, isValidRoom(userBoard, forceBroadcast))
                .orTimeout(500, TimeUnit.MILLISECONDS);
        }

        try {
            if (storageMessage != null) {
                this.cacheClient.publish(storageMessage);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void publishEvent(UserBoard userBoard, EventMessage message) {
        publishEvent(userBoard, message, false);
    }

    /**
     * Method handle incomming messages from the CacheClient subscribed publisher
     */
    private void registerObserver() {
        //TODO create objectMapper accessor
        var objectMapper = new ObjectMapper();

        cacheClient.getCacheClientObserver().subscribe(message -> {
            try {
                log.info("Got message from CacheClient publisher: {}", message.getMessage());
                var storageMessage = objectMapper.readValue(message.getMessage(), StorageMessage.class);
                if (!storageMessage.getHostIp().equals(hostIpAddress)
                    || Boolean.parseBoolean(System.getenv("DEBUG"))) {
                    var userMessage = storageMessage.getUserMessage();
                    var userBoard = userMessage.getUserBoard();
                    log.info("Going to broadcast msg from publisher: {}, for board: {}", userMessage,
                        userBoard.getBoardId());
                    var forceBroadcast = storageMessage.isForceBroadcast();
                    broadcaster.broadcastAsync(userMessage, MediaType.APPLICATION_JSON_TYPE, isValidRoom(userBoard,
                        forceBroadcast))
                        .orTimeout(500, TimeUnit.MILLISECONDS);
                }
            } catch (JsonProcessingException e) {
                log.error("Unable to convert JSON to Board object", e);
            }
        });
    }

    @OnClose
    @Consumes(MediaType.APPLICATION_JSON)
    public void onClose(@RequestBean UserBoard userBoard, WebSocketSession session, CloseReason closeReason) {
        log.info("[{}, {}] Disconnected! CloseReason: {}", userBoard.getBoardId(), userBoard.getUsername(), closeReason);

        processEvent(userBoard, new EventMessage(EventType.DISCONNECTED, null, userBoard.getUsername()), session);
        //TODO fire event Disconected
        session.close(closeReason);
    }

    private Predicate<WebSocketSession> isValidRoom(UserBoard userBoard, boolean forceBroadcast) {
        return s -> {
            var validUserBoard = userBoard.getBoardId().equals(s.getUriVariables()
                .get("boardId", String.class, null));
            var validConsumers = !userBoard.getUsername().equalsIgnoreCase(s.getUriVariables()
                .get("username", String.class, null));

            log.info("internal valid : {}, user the same?: {} = {}, {}, forceBroadcast?: {}", validUserBoard,
                userBoard.getUsername(),
                s.getUriVariables().get("username", String.class, null), validConsumers,
                forceBroadcast);

            return validUserBoard && (validConsumers || forceBroadcast);
        };
    }
}
