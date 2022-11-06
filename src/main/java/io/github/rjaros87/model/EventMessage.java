package io.github.rjaros87.model;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * Class responsible for deserializing (JSON) incoming event messages
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage {
    @NotNull
    private EventType eventType;

    @Nullable
    private String cardId;

    @Nullable
    private String content;
}
