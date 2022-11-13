package io.github.rjaros87.model;

import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ReflectiveAccess
public class UserMessage {
    private UserBoard userBoard;
    private EventMessage eventMessage;
}
