package io.github.rjaros87.controllers;

import io.github.rjaros87.model.Board;
import io.github.rjaros87.storage.CacheClient;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller responsible for generating view for team retrospective meeting.
 */
@Slf4j
@Controller("/board")
public class BoardController {
    @Inject
    private CacheClient cacheClient;

    @View("board")
    @Get("/{boardId}")
    public Board board(String boardId) {
        log.info("Going to find board with id: {}", boardId);

        return cacheClient.getBoard(boardId)
                .doOnError(throwable -> log.error("An error occurred when fetching board: ", throwable)).block();
    }
}
