package io.github.rjaros87.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.Board;
import io.github.rjaros87.model.BoardCard;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import io.micronaut.http.MediaType;
import io.micronaut.websocket.WebSocketSession;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class CacheClient {
    private final static String BOARD_CARDS = "retroboard:board:%s:cards";
    private final static String BOARD_CARD = "retroboard:board:%s:cardId:%s";

    private final RedisReactiveCommands<String, String> redis;
    private final RedisPubSubReactiveCommands<String, String> redisSub;
    private final RedisPubSubReactiveCommands<String, String> redisPub;


    public final static String REDIS_CHANNEL = "channel";

    public CacheClient(@Named("default") StatefulRedisConnection<String, String> redisConnection,
                       @Named("sub") StatefulRedisPubSubConnection<String, String> redisSubConnection,
                       @Named("pub") StatefulRedisPubSubConnection<String, String> redisPubConnection) {
        redis = redisConnection.reactive();
        this.redisSub = redisSubConnection.reactive();
        this.redisPub = redisPubConnection.reactive();

        this.redisSub.subscribe(REDIS_CHANNEL).subscribe();
    }

    public Mono<String> storeBoard(Board board) {
        Mono<String> result = Mono.empty();

        var objectMapper = new ObjectMapper();

        var key = String.format(Board.STORAGE_KEY, board.getBoardId().getId());
        try {
            var value = objectMapper.writeValueAsString(board);
            var setArgs = new SetArgs()
                    .nx()
                    .ex(Duration.ofHours(2));
            result = redis.set(key, value, setArgs);
        } catch (JsonProcessingException e) {
            log.error("Unable to convert Board to JSON string: ", e);

        }

        return result;
    }

    public void storeEvent(EventFields field, String boardId, String cardId, String content) {
        storeEvent(field, boardId, cardId, content, null);
    }

    public void storeEvent(EventFields field, String boardId, String cardId, String content, String username) {
        if (cardId == null) {
            throw new RuntimeException("CardId cannot be null!");
        }

        var key = String.format(BOARD_CARD, boardId, cardId);

        var fields = new HashMap<String, String>();
        fields.put(field.getFieldKey(), content);

        if (username != null) {
            fields.put(EventFields.USERNAME.getFieldKey(), username);
        }

        redis.hset(key, fields).subscribe(
                result -> log.debug("hset for key: {}, fields: {}", key, fields),
                throwable -> log.error("Error during hset for key: {}, field: {}, content: {}, error: {}",
                        key, field, content, throwable.getMessage())
        );
    }

    public void assignCardToBoard(String boardId, String cardId) {
        if (cardId == null) {
            throw new RuntimeException("CardId cannot be null!");
        }

        var key = String.format(BOARD_CARDS, boardId);

        redis.sadd(key, cardId).subscribe(
                saddResult -> {
                    log.debug("sadd for key: {}, cardId: {}", key, cardId);

                    redis.expire(key, Duration.ofHours(1)).subscribe(
                            expireResult -> log.debug("Set expire for key: {}", key),
                            expireThrowable -> log.error("Error during set expire for key: {}, error: {}",
                                    key, expireThrowable.getMessage())
                    );
                },
                saddThrowable -> log.error("Error during adding to set for key: {}, error: {}",
                        key, saddThrowable.getMessage())
        );
    }

    public void sendCardsToConnectedUser(String boardId, WebSocketSession session) {
        var key = String.format(BOARD_CARDS, boardId);
        var cardIds = new ArrayList<String>();

        redis.smembers(key)
                .doOnComplete(getAndSendCard(cardIds, boardId, session))
                .subscribe(
                        cardsId -> {
                            log.debug("Add card id: {} to list", cardsId);
                            cardIds.add(cardsId);
                        },
                        throwable -> log.error("Unable to get cards list due to:", throwable)
                );
    }

    private Runnable getAndSendCard(ArrayList<String> cardIds, String boardId, WebSocketSession session) {
        return () -> {
            log.debug("Going to fetch: {} cards", cardIds.size());
            for (String cardId : cardIds) {
                var key = String.format(BOARD_CARD, boardId, cardId);
                log.debug("Going to fetch hash: {}", key);
                var boardCard = BoardCard.builder();
                boardCard.cardId(cardId);
                var brokenBoardCard = new AtomicBoolean(false);

                redis.hgetall(key).subscribe(
                        //FIXME: needs to be optimised, to send a batch of cards instead one by one
                        card -> {
                            log.debug("Got card: {}", card);
                            var cardValue = card.getValue();
                            var cardKey = card.getKey();
                            var eventField = EventFields.findByKey(cardKey);
                            if (eventField == null) {
                                brokenBoardCard.set(true);
                            } else {
                                switch (eventField) {
                                    case CATEGORY -> boardCard.category(cardValue);
                                    case CONTENT -> boardCard.content(cardValue);
                                    case USERNAME -> boardCard.username(cardValue);
                                    case DISLIKE ->  boardCard.dislikes(Integer.valueOf(cardValue));
                                    case LIKE -> boardCard.likes(Integer.valueOf(cardValue));
                                    default -> log.error("Unsupported key: {}", cardKey);
                                }
                            }
                        },
                        throwable -> log.error("Unable to fetch card due to:", throwable),
                        () -> {
                            if (!brokenBoardCard.get()) {
                                session.sendSync(boardCard.build(), MediaType.APPLICATION_JSON_TYPE);
                            }
                        }
                );
            }
        };
    }

    public Mono<Board> getBoard(String boardId) {
        Mono<Board> result = Mono.empty();

        var key = String.format(Board.STORAGE_KEY, boardId);
        var response = redis.get(key).block();
        if (response != null) {
            var objectMapper = new ObjectMapper();

            try {
                var board = objectMapper.readValue(response, Board.class);
                result = Mono.just(board);
            } catch (JsonProcessingException e) {
                log.error("Unable to convert JSON to Board object", e);
            }
        }

        return result;
    }

    public Flux<ChannelMessage<String, String>> getCacheClientObserver() {
        return redisSub.observeChannels();
    }

    public void publish(StorageMessage storageMessage) throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        redisPub.publish(REDIS_CHANNEL, objectMapper.writeValueAsString(storageMessage)).block();
    }
}
