package com.unique.zhangaizerocode.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.unique.zhangaizerocode.ai.model.message.AiResponseMessage;
import com.unique.zhangaizerocode.ai.model.message.StreamMessage;
import com.unique.zhangaizerocode.ai.model.message.StreamMessageTypeEnum;
import com.unique.zhangaizerocode.ai.model.message.ToolExecutedMessage;
import com.unique.zhangaizerocode.ai.model.message.ToolRequestMessage;
import com.unique.zhangaizerocode.ai.tools.BaseTool;
import com.unique.zhangaizerocode.ai.tools.ToolManager;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * JSON 消息流处理器。
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    @Resource
    private ToolManager toolManager;

    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               User loginUser) {
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .map(chunk -> handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds))
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> {
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(
                            appId,
                            aiResponse,
                            ChatHistoryMessageTypeEnum.AI.getValue(),
                            loginUser.getId()
                    );
                })
                .doOnError(error -> {
                    String errorMessage = "AI 回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(
                            appId,
                            errorMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(),
                            loginUser.getId()
                    );
                });
    }

    private String handleJsonMessageChunk(String chunk,
                                          StringBuilder chatHistoryStringBuilder,
                                          Set<String> seenToolIds) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("不支持的消息类型: {}", streamMessage.getType());
            return "";
        }
        return switch (typeEnum) {
            case AI_RESPONSE -> handleAiResponse(chunk, chatHistoryStringBuilder);
            case TOOL_REQUEST -> handleToolRequest(chunk, seenToolIds);
            case TOOL_EXECUTED -> handleToolExecuted(chunk, chatHistoryStringBuilder);
        };
    }

    private String handleAiResponse(String chunk, StringBuilder chatHistoryStringBuilder) {
        AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
        String data = sanitizeVisibleStreamText(aiMessage.getData());
        chatHistoryStringBuilder.append(data);
        return data;
    }

    private String handleToolRequest(String chunk, Set<String> seenToolIds) {
        ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
        String toolName = toolRequestMessage.getName();
        String toolId = toolRequestMessage.getId();
        String toolKey = StrUtil.isNotBlank(toolId)
                ? "id:" + toolId
                : "index:" + toolRequestMessage.getIndex();
        if (toolRequestMessage.getIndex() == null && StrUtil.isNotBlank(toolName)) {
            toolKey = "name:" + toolName;
        }
        if (StrUtil.isBlank(toolKey) || seenToolIds.contains(toolKey)) {
            return "";
        }
        seenToolIds.add(toolKey);

        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            log.warn("未找到工具实例，toolName: {}", toolName);
            return "";
        }
        return tool.generateToolRequestResponse();
    }

    private String handleToolExecuted(String chunk, StringBuilder chatHistoryStringBuilder) {
        ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        String toolName = toolExecutedMessage.getName();
        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            log.warn("未找到工具实例，toolName: {}", toolName);
            return "";
        }

        JSONObject arguments = JSONUtil.parseObj(toolExecutedMessage.getArguments());
        String result = tool.generateToolExecutedResult(arguments);
        String output = String.format("\n%s\n", result);
        chatHistoryStringBuilder.append(output);
        return output;
    }

    private String sanitizeVisibleStreamText(String data) {
        if (StrUtil.isBlank(data)) {
            return "";
        }
        return data
                .replace("[选择工具] 写入文件\n", "")
                .replace("[选择工具] 写入文件\r\n", "")
                .replace("[选择工具] 写入文件", "");
    }
}
