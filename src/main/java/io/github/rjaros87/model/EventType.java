package io.github.rjaros87.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.ReflectiveAccess;

@ReflectiveAccess
public enum EventType {
    @JsonProperty("set")
    SET,

    @JsonProperty("delete")
    DELETE,

    @JsonProperty("like")
    LIKE,

    @JsonProperty("dislike")
    DISLIKE,

    @JsonProperty("assign")
    ASSIGN,

    @JsonProperty("connected")
    CONNECTED,

    @JsonProperty("disconnected")
    DISCONNECTED,

    @JsonProperty("card_color")
    CARD_COLOR

    //TODO: Handle incorrect json property
}