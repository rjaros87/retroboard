package io.github.rjaros87.storage;

import io.github.rjaros87.model.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageMessage {
    private UserMessage userMessage;
    private String hostIp;
}
