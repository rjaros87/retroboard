package io.github.rjaros87.listeners;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.crac.events.BeforeCheckpointEvent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
@Requires(beans = {StatefulRedisConnection.class, StatefulRedisPubSubConnection.class})
@Singleton
public class BeforeCheckpointEventListener implements ApplicationEventListener<BeforeCheckpointEvent> {
    private final StatefulRedisConnection<String ,String> redisConnection;
    private final StatefulRedisPubSubConnection<String ,String> redisSub;
    private final StatefulRedisPubSubConnection<String ,String> redisPub;


    public BeforeCheckpointEventListener(
        @Named("default") StatefulRedisConnection<String ,String> redisConnection,
        @Named("sub") StatefulRedisPubSubConnection<String ,String> redisSubConnection,
        @Named("pub") StatefulRedisPubSubConnection<String ,String> redisPubConnection
    ) {
        log.info("BeforeCheckpointEventListener constructor");
        this.redisConnection = redisConnection;
        this.redisSub = redisSubConnection;
        this.redisPub = redisPubConnection;
    }

    @Override
    public void onApplicationEvent(BeforeCheckpointEvent event) {
        redisConnection.close();
        redisPub.close();
        redisSub.close();
        log.info("BeforeCheckpointEventListener time taken: {}", Duration.ofNanos(event.getTimeTakenNanos())
            .toSeconds());
    }
}
