package io.github.rjaros87.model;

import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.Nullable;

import javax.validation.constraints.Pattern;

@Introspected
public class BoardTitle {

    @Nullable
    @Pattern(regexp = "[a-zA-Z0-9-#/%&\\s]{0,19}")
    private String boardTitle;

    public void setBoardTitle(String boardTitle) {
        this.boardTitle = boardTitle;
    }

    public String getBoardTitle() {
        return boardTitle;
    }

    @Override
    public String toString() {
        return boardTitle;
    }
}
