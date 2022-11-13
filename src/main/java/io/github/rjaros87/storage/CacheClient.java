package io.github.rjaros87.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.Board;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Singleton
public class CacheClient {
    private final RedisReactiveCommands<String, String> redis;
    private final RedisPubSubReactiveCommands<String, String> redisSub;
    private final RedisPubSubReactiveCommands<String, String> redisPub;

    public final static String REDIS_CHANNEL = "channel";

    public CacheClient(@Named("default") StatefulRedisConnection<String, String> redisConnection,
                       @Named("sub") StatefulRedisPubSubConnection<String, String> redisSubConnection,
                       @Named("pub") StatefulRedisPubSubConnection<String, String> redisPubConnection
                       ) {
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
