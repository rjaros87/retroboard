package io.github.rjaros87.storage;

import io.github.rjaros87.model.UserMessage;
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
public class StorageMessage {
    private UserMessage userMessage;
    private String hostIp;
}
