package com.unique.zhangaizerocode.core.handler;

import cn.hutool.json.JSONUtil;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JsonMessageStreamHandlerTest {

    @Test
    void shouldRenderToolCallsAsMarkdownWithoutRepeatedGeneratingText() {
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        User loginUser = new User();
        loginUser.setId(1L);

        String firstToolRequest = toolRequest("call_1", "src/index.html", "<h1>Hello</h1>");
        String secondToolRequest = toolRequest("call_2", "src/about.html", "<p>About</p>");

        String rendered = String.join("", handler.handle(
                Flux.just(
                        firstToolRequest,
                        firstToolRequest,
                        toolExecuted("call_1", "src/index.html", "<h1>Hello</h1>"),
                        secondToolRequest,
                        toolExecuted("call_2", "src/about.html", "<p>About</p>")
                ),
                chatHistoryService,
                123L,
                loginUser
        ).collectList().block());

        assertThat(rendered).contains("src/index.html");
        assertThat(rendered).contains("src/about.html");
        assertThat(rendered).contains("[正在编写] src/index.html");
        assertThat(rendered).contains("[工具调用] 写入完成 src/index.html");
        assertThat(rendered).contains("<h1>Hello</h1>");
        assertThat(rendered).contains("<p>About</p>");
        assertThat(rendered).contains("```html");
        assertThat(rendered).doesNotContain("正在生成并写入项目文件");
        assertThat(countOccurrences(rendered, "<h1>Hello</h1>")).isEqualTo(1);
        assertThat(countOccurrences(rendered, "<p>About</p>")).isEqualTo(1);

        ArgumentCaptor<String> savedMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(
                eq(123L),
                savedMessageCaptor.capture(),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()),
                eq(1L)
        );
        assertThat(savedMessageCaptor.getValue()).contains("src/index.html");
        assertThat(savedMessageCaptor.getValue()).contains("[正在编写] src/index.html");
        assertThat(savedMessageCaptor.getValue()).contains("[工具调用] 写入完成 src/index.html");
        assertThat(savedMessageCaptor.getValue()).contains("<h1>Hello</h1>");
    }

    @Test
    void shouldStartWritingStatusOnNewLineAfterToolPromptText() {
        JsonMessageStreamHandler handler = new JsonMessageStreamHandler();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        User loginUser = new User();
        loginUser.setId(1L);

        String rendered = String.join("", handler.handle(
                Flux.just(
                        aiResponse("[工具调用] 写入文件 src/App.vue"),
                        toolRequest("call_1", "src/App.vue", "<template></template>"),
                        toolExecuted("call_1", "src/App.vue", "<template></template>")
                ),
                chatHistoryService,
                123L,
                loginUser
        ).collectList().block());

        assertThat(rendered).contains("[工具调用] 写入文件 src/App.vue\n[正在编写] src/App.vue");

        ArgumentCaptor<String> savedMessageCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(
                eq(123L),
                savedMessageCaptor.capture(),
                eq(ChatHistoryMessageTypeEnum.AI.getValue()),
                eq(1L)
        );
        assertThat(savedMessageCaptor.getValue()).contains("[工具调用] 写入文件 src/App.vue\n[正在编写] src/App.vue");
    }

    private static String aiResponse(String data) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "ai_response",
                "data", data
        ));
    }

    private static String toolRequest(String id, String relativeFilePath, String content) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "tool_request",
                "id", id,
                "name", "writeFile",
                "arguments", toolArguments(relativeFilePath, content)
        ));
    }

    private static String toolExecuted(String id, String relativeFilePath, String content) {
        return JSONUtil.toJsonStr(Map.of(
                "type", "tool_executed",
                "id", id,
                "name", "writeFile",
                "arguments", toolArguments(relativeFilePath, content),
                "result", "ok"
        ));
    }

    private static String toolArguments(String relativeFilePath, String content) {
        return JSONUtil.toJsonStr(Map.of(
                "relativeFilePath", relativeFilePath,
                "content", content
        ));
    }

    private static int countOccurrences(String value, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
