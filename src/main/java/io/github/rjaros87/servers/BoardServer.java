package io.github.rjaros87.servers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.EventMessage;
import io.github.rjaros87.model.EventType;
import io.github.rjaros87.model.UserBoard;
import io.github.rjaros87.model.UserMessage;
import io.github.rjaros87.storage.CacheClient;
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
        try(final DatagramSocket socket = new DatagramSocket()){
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
    }

    @OnMessage
    @Consumes(MediaType.APPLICATION_JSON)
    public void onMessage(@RequestBean UserBoard userBoard, EventMessage message, WebSocketSession session) {
        log.info("[{}][{}] Got new message: {}", userBoard.getBoardId(), userBoard.getUsername(), message);

        processEvent(userBoard, message, session);
    }

    /**
     * Function store events from the client into the Cache
     * @param userBoard
     * @param message
     */
    @ExecuteOn(TaskExecutors.IO)
    private void processEvent(UserBoard userBoard, EventMessage message, WebSocketSession session) {
        StorageMessage storageMessage;
        UserMessage userMessage;
        switch (message.getEventType()) {
            case CONNECTED:
            case DISCONNECTED:
            case SET:
            case ASSIGN:
            case DELETE:
            case LIKE:
            case DISLIKE:

                    userMessage = UserMessage.builder()
                            .userBoard(userBoard)
                            .eventMessage(message)
                            .build();
                    storageMessage = StorageMessage.builder()
                            .userMessage(userMessage)
                            .hostIp(hostIpAddress)
                            .build();
                if(!Boolean.parseBoolean(System.getenv("DEBUG"))) {
                    log.info("Going to broadcast message on the server");
                    broadcaster.broadcastAsync(userMessage, MediaType.APPLICATION_JSON_TYPE, isValidRoom(userBoard))
                            .orTimeout(500, TimeUnit.MILLISECONDS);
                }
                break;
            default:
                throw new UnsupportedOperationException("Unsupported event type: " + message.getEventType());
        }
        try {
            if (storageMessage != null) {
                this.cacheClient.publish(storageMessage);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
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
                    var eventMessage = userMessage.getEventMessage();
                    log.info("Going to broadcast msg from publisher: {}, for board: {}", eventMessage, userBoard.getBoardId());
                    broadcaster.broadcastAsync(eventMessage, MediaType.APPLICATION_JSON_TYPE, isValidRoom(userBoard))
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

    private Predicate<WebSocketSession> isValidRoom(UserBoard userBoard) {
        return s -> {
            var validUserBoard = userBoard.getBoardId().equals(s.getUriVariables()
                    .get("boardId", String.class, null));
            var validConsumers = !userBoard.getUsername().equalsIgnoreCase(s.getUriVariables()
                    .get("username", String.class, null));
//            return  validUserBoard && validConsumers;
            log.info("internal valid : {}", validUserBoard);
            log.info("user the same?: {} = {}, {}", userBoard.getUsername(), s.getUriVariables()
                    .get("username", String.class, null), validConsumers);
            return  validUserBoard && validConsumers;
        };
    }
}
