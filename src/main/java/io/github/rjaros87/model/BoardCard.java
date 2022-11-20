package io.github.rjaros87.model;

import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ReflectiveAccess
public class BoardCard {
    private String cardId;
    private String category;
    private String content;
    private String username;
    private Integer likes;
    private Integer dislikes;
}
