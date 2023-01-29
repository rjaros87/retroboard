package io.github.rjaros87.listeners;

import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.crac.events.AfterRestoreEvent;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AfterRestoreEventListener implements ApplicationEventListener<AfterRestoreEvent> {
    private final RedisCommands<String ,String> redisCommands;
    private final RedisPubSubCommands<String ,String> redisCommandsSub;
    private final RedisPubSubCommands<String ,String> redisCommandsPub;


    public AfterRestoreEventListener(
        @Named("default") RedisCommands<String ,String> redisCommands,
        @Named("sub") RedisPubSubCommands<String ,String> redisCommandsSub,
        @Named("pub") RedisPubSubCommands<String ,String> redisCommandsPub
    ) {
        log.info("BeforeCheckpointEventListener constructor");
        this.redisCommands = redisCommands;
        this.redisCommandsSub = redisCommandsSub;
        this.redisCommandsPub = redisCommandsPub;
    }

    @Override
    public void onApplicationEvent(AfterRestoreEvent event) {
        log.info("AfterRestoreEventListener time taken: {}", event.getTimeTakenNanos());
    }
}
