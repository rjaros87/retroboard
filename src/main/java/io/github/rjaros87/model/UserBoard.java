package io.github.rjaros87.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.annotation.PathVariable;
import lombok.Data;

@ReflectiveAccess
@Introspected
@Data
public class UserBoard {
    @PathVariable
    private String boardId;

    @PathVariable
    private String username;
}
