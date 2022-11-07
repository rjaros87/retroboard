package io.github.rjaros87.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessage {
    private UserBoard userBoard;
    private EventMessage eventMessage;
}
