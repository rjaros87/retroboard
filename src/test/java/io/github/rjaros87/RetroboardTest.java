package io.github.rjaros87;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

class RetroboardTest {

    private static final String REDIS_DOCKER_NAME = "bitnami/redis:latest";
    private static final Integer REDIS_PORT = 6379;
    private static final String REDIS_PWD = "str0ng_passw0rd";

    private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse(REDIS_DOCKER_NAME))
            .withExposedPorts(6379)
            .withEnv(Map.of("REDIS_PASSWORD", REDIS_PWD));

    private static ApplicationContext context;

    @BeforeAll
    public static void initUnitTest() {
        redis.start();

        context = ApplicationContext.run(PropertySource.of(
            "test", Map.of(
                    "redis.servers.default.host", redis.getHost(),
                    "redis.servers.default.port", redis.getMappedPort(REDIS_PORT),
                    "redis.servers.default.password", REDIS_PWD,
                    "redis.servers.pub.host", redis.getHost(),
                    "redis.servers.pub.port", redis.getMappedPort(REDIS_PORT),
                    "redis.servers.pub.password", REDIS_PWD,
                    "redis.servers.sub.host", redis.getHost(),
                    "redis.servers.sub.port", redis.getMappedPort(REDIS_PORT),
                    "redis.servers.sub.password", REDIS_PWD
            ))
        );
    }

    @AfterEach
    void tearDown() {
        redis.stop();
    }

    @Test
    void testItWorks() {
        Assertions.assertTrue(context.isRunning());
    }

}
