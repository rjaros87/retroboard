package io.github.rjaros87.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.Objects;

@ReflectiveAccess
@Introspected
public class BoardId {
    private String id;

    public BoardId(String id) {
        this.id = Objects.requireNonNullElseGet(id, this::generateId);
    }

    public BoardId() {
        this.id = generateId();
    }



    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return getId();
    }

    private String generateId() {
        return DigestUtils
                .md5Hex(String.valueOf(System.currentTimeMillis())).substring(0, 7);
    }
}
