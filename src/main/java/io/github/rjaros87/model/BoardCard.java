package io.github.rjaros87.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.*;

import java.util.Collection;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ReflectiveAccess
@JsonInclude
public class BoardCard {
    private String cardId;
    private String category;
    private String content;
    private String username;
    @Singular
    private Collection<String> likes;
    @Singular
    private Collection<String> dislikes;
}
