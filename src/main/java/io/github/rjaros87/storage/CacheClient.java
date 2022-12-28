package io.github.rjaros87.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.Board;
import io.github.rjaros87.model.BoardCard;
import io.github.rjaros87.model.EventMessage;
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
    private final static String BOARD_CARD_LIKES = BOARD_CARD + ":%s";

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
            // FIXME: https://projectreactor.io/docs/core/release/reference/#faq.wrap-blocking
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

    public Mono<Long> storeEvent(EventFields field, String boardId, String cardId, String content) {
        return storeEvent(field, boardId, cardId, content, null);
    }

    public Mono<Long> storeEvent(EventFields field, String boardId, String cardId, String content, String username) {
        validateCardId(cardId);

        var key = String.format(BOARD_CARD, boardId, cardId);

        var fields = new HashMap<String, String>();
        fields.put(field.getFieldKey(), content);

        if (username != null) {
            fields.put(EventFields.USERNAME.getFieldKey(), username);
        }

        return redis.hset(key, fields)
                .doOnSuccess(result -> log.debug("hset for key: {}, fields: {}", key, fields))
                .doOnError(throwable -> log.error("Error during hset for key: {}, field: {}, content: {}, error: {}",
                        key, field, content, throwable.getMessage()));
    }

    public Mono<Long> processEmotionEvent(EventFields field, String boardId, String username, EventMessage message) {
        var cardId = message.getCardId();
        var content = Integer.parseInt(message.getContent());

        log.debug("Going to process event for content: {}", content);
        if (content < 1) {
            return storeDecrementEvent(field, boardId, cardId, username);
        }
        return storeIncrementEvent(field, boardId, cardId, username);
    }

    public Mono<Long> storeIncrementEvent(EventFields field, String boardId, String cardId, String username) {
        validateCardId(cardId);

        var key = String.format(BOARD_CARD_LIKES, boardId, cardId, field.getFieldKey());

        if (field.equals(EventFields.LIKE) || field.equals(EventFields.DISLIKE)) {
            log.debug("Going to increment {} list for cardId: {}, for boardId: {}", field.getFieldKey(), cardId,
                boardId);
            return redis.sadd(key, username)
                    .doOnError(
                            throwable -> log.error("Error during sadd for key: {}, field: {}, username: {}, error: {}",
                                    key, field, username, throwable.getMessage())
                    )
                    .flatMap(res -> redis.scard(key));
        } else {
            throw new RuntimeException("Unsupported event field for increment: " + field.getFieldKey());
        }
    }
    public Mono<Long> storeDecrementEvent(EventFields field, String boardId, String cardId, String username) {
        validateCardId(cardId);

        var key = String.format(BOARD_CARD_LIKES, boardId, cardId, field.getFieldKey());

        if (field.equals(EventFields.LIKE) || field.equals(EventFields.DISLIKE)) {
            log.debug("Going to decrement {} list for cardId: {}, for boardId: {}", field.getFieldKey(), cardId,
                boardId);
            return redis.srem(key, username).doOnError(
                throwable -> log.error("Error during srem for key: {}, field: {}, username: {}, error: {}",
                    key, field, username, throwable.getMessage())
            )
                .flatMap(res -> redis.scard(key));
        } else {
            throw new RuntimeException("Unsupported event field for increment: " + field.getFieldKey());
        }
    }

    private void validateCardId(String cardId) {
        if (cardId == null) {
            throw new RuntimeException("CardId cannot be null!");
        }
    }

    public Mono<Long> assignCardToBoard(String boardId, String cardId) {
        validateCardId(cardId);

        var key = String.format(BOARD_CARDS, boardId);

        return redis.sadd(key, cardId)
                .doOnSuccess(
                        saddResult -> {
                            log.debug("sadd for key: {}, cardId: {}", key, cardId);

                            redis.expire(key, Duration.ofHours(1)).subscribe(
                                    expireResult -> log.debug("Set expire for key: {}", key),
                                    expireThrowable -> log.error("Error during set expire for key: {}, error: {}",
                                            key, expireThrowable.getMessage())
                            );
                        }
                )
                .doOnError(saddThrowable -> log.error("Error during adding to set for key: {}, error: {}",
                        key, saddThrowable.getMessage()));
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
                var keyLikes = String.format(BOARD_CARD_LIKES, boardId, cardId, EventFields.LIKE.getFieldKey());
                var keyDislikes = String.format(BOARD_CARD_LIKES, boardId, cardId,
                    EventFields.DISLIKE.getFieldKey());

                log.debug("Going to fetch hash: {}", key);
                var boardCard = BoardCard.builder();
                boardCard.cardId(cardId);
                var brokenBoardCard = new AtomicBoolean(false);

                var likes = redis.smembers(keyLikes);
                var dislikes = redis.smembers(keyDislikes);

                var cardData = redis.hgetall(key);

                cardData.subscribe(
                        //FIXME: needs to be optimised, to send a batch of cards instead one by one
                        card -> {
                            log.debug("Got card: {}", card);
                            var cardValue = card.getValue();
                            var cardKey = card.getKey();
                            var eventField = EventFields.findByKey(cardKey);
                            if (eventField == null) {
                                log.error("Card cannot have null eventField");
                                brokenBoardCard.set(true);
                            } else {
                                switch (eventField) {
                                    case CATEGORY -> boardCard.category(cardValue);
                                    case CONTENT -> boardCard.content(cardValue);
                                    case USERNAME -> boardCard.username(cardValue);
                                    default -> log.error("Unsupported key: {}", cardKey);
                                }
                            }
                        },
                        throwable -> log.error("Unable to fetch card due to:", throwable),
                        () -> {
                            if (!brokenBoardCard.get()) {
                                likes.subscribe(
                                    resLikes -> {
                                        log.info("Number of likes: {}, for key: {}", resLikes, keyLikes);
                                        boardCard.like(resLikes);
                                    },
                                    likesThrowable -> log.error("Unable to fetch likes onOpen due to:", likesThrowable),
                                    () -> dislikes.subscribe(
                                        resDislike -> {
                                            log.info("Number of dislikes: {}, for key: {}", resDislike, keyDislikes);
                                            boardCard.dislike(resDislike);
                                        },
                                        disLikesThrowable -> log.error("Unable to fetch dislikes onOpen due to:", disLikesThrowable),
                                        () -> session.sendSync(boardCard.build(), MediaType.APPLICATION_JSON_TYPE)
                                    )
                                );
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
