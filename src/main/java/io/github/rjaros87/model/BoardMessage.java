package io.github.rjaros87.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BoardMessage {
    private String board;
    private String username;
    private String message;
}
