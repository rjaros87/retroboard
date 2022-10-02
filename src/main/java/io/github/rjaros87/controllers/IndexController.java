package io.github.rjaros87.controllers;

import io.github.rjaros87.model.Board;
import io.github.rjaros87.model.BoardId;
import io.github.rjaros87.model.BoardTitle;
import io.github.rjaros87.storage.CacheClient;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.session.Session;
import io.micronaut.validation.Validated;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import javax.validation.Valid;

/**
 * Controller responsible for creating or joining RetroBoard
 */
@Slf4j
@Validated
@Controller
public class IndexController {
    @Inject
    private CacheClient cacheClient;

    @Value("${micronaut.server.host}")
    String serverHost;

    @Value("${micronaut.server.port}")
    String serverPort;

    @View("index")
    @Get("/")
    public HttpResponse<?> index(Session session) {
        var board = session.get(Board.SESSION_KEY, Board.class).orElse(null);
        log.info("Session: {}, Board: {}", session, board);

        return HttpResponse.ok(CollectionUtils.mapOf(
            "board", board,
            "serverHost", serverHost,
            "serverPort", serverPort
        ));
    }

    @View("index")
    @Post("/create-room")
    @Consumes({MediaType.ALL})
    public HttpResponse<?> createRoom(Session session, @Body @Valid BoardTitle boardTitle) {
        log.info("Session: {}", session);

        var board = session.get(Board.SESSION_KEY, Board.class)
                .orElseGet(() -> generateBoard(session, boardTitle));

        log.info("Board from session: {}", board);

        return HttpResponse.ok(CollectionUtils.mapOf(
                "board", board,
                "serverHost", serverHost,
                "serverPort", serverPort
        ));
    }

    private Board generateBoard(Session session, BoardTitle boardTitle) {
        //TODO: check if not exist and register board token in DB
        Board result = null;

        var id = new BoardId(null);
        var board = new Board();
        board.setBoardTitle(boardTitle);
        board.setBoardId(id);
        log.info("Going to store Board: {}", board);
        var storage =  cacheClient.storeBoard(board)
                .doOnSuccess(success -> {
                    session.put(Board.SESSION_KEY, board);
                    log.info("Board stored in Redis: {}", board);
                })
                .doOnError(throwable -> {
                    log.error("An error occurred during storing board: {}, exception: {}", board, throwable);
                    //TODO: Display error
                })
                .block();
        if (storage != null) {
            result = board;
        }

        return result;
    }
}
