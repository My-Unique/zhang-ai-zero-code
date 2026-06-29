package com.unique.zhangaizerocode.core.handler;

import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * Stream handler dispatcher.
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    public Flux<String> doExecute(Flux<String> originFlux,
                                  ChatHistoryService chatHistoryService,
                                  long appId,
                                  Long versionNo,
                                  User loginUser,
                                  CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                yield jsonMessageStreamHandler.handle(originFlux, chatHistoryService, appId, versionNo, loginUser);
            }
            case HTML, MULTI_FILE -> {
                yield new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
            }
        };
    }
}
