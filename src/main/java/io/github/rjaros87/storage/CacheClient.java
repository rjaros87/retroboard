package io.github.rjaros87.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rjaros87.model.Board;
import io.github.rjaros87.model.BoardId;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Singleton
public class CacheClient {
    private final RedisReactiveCommands<String, String> redis;

    public CacheClient(StatefulRedisConnection<String, String> redisConnection) {
        redis = redisConnection.reactive();
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

    public Mono<Board> getBoard(BoardId boardId) {
        Mono<Board> result = Mono.empty();

        var key = String.format(Board.STORAGE_KEY, boardId.getId());
        var response = redis.get(key).block();
        if (response != null) {
            var objectMapper = new ObjectMapper();

            try {
                var board = objectMapper.readValue(response, Board.class);
                result = Mono.just(board);
            } catch (JsonProcessingException e) {
                log.error("Unable to convert JSON to Board object");
            }
        }

        return result;
    }
}
