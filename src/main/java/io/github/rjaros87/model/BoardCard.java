package io.github.rjaros87.model;

import io.micronaut.core.annotation.ReflectiveAccess;
import lombok.Data;

@ReflectiveAccess
@Data
public class BoardCard {
    private String carId;
    private String category;
    private String content;
    private String username;

}
