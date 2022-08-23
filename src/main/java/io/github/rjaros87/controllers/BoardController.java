package io.github.rjaros87.controllers;

import io.github.rjaros87.model.BoardId;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.views.View;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Valid;

/**
 * Controller responsible for generating view for team retrospective meeting.
 */
@Slf4j
@Controller("/board")
public class BoardController {

    @View("board")
    @Get("/{id}")
    public HttpResponse<BoardId> board(@Valid String id) {
        var boardId = new BoardId(id);
        log.info("Going to return board view for token: {}", boardId);

        return HttpResponse.ok(boardId);
    }
}
