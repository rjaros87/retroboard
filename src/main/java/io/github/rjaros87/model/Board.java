package io.github.rjaros87.model;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class Board {
    public static final String SESSION_KEY = "board";
    public static final String STORAGE_KEY = "retroboard:board:%s";

    private BoardId boardId;

    private BoardTitle boardTitle;

    public Board(BoardId boardId, BoardTitle boardTitle) {
        this.boardId = boardId;
        this.boardTitle = boardTitle;
    }

    public Board() {}

    public void setBoardTitle(BoardTitle boardTitle) {
        this.boardTitle = boardTitle;
    }

    public void setBoardId(BoardId boardId) {
        this.boardId = boardId;
    }

    public BoardTitle getBoardTitle() {
        return boardTitle;
    }

    public BoardId getBoardId() {
        return boardId;
    }
}
