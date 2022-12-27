package io.github.rjaros87.model;

import io.micronaut.core.annotation.ReflectiveAccess;
import jakarta.annotation.Nullable;
import lombok.*;

import javax.validation.constraints.NotNull;

/**
 * Class responsible for deserializing (JSON) incoming event messages
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ReflectiveAccess
public class EventMessage {
    @NotNull
    private EventType eventType;

    @Nullable
    private String cardId;

    @Nullable
    @Setter
    private String content;
}
