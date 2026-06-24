package com.unique.zhangaizerocode.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.unique.zhangaizerocode.ai.model.message.*;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JSON 消息流处理器
 * 处理 VUE_PROJECT 类型的复杂流式响应，包含工具调用信息
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    private static final String GENERATION_HEADER_MARKER = "__generation_header__";

    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId, User loginUser) {
        // 收集数据用于生成后端记忆格式
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        ToolStreamState toolStreamState = new ToolStreamState();
        return originFlux
                .map(chunk -> {
                    // 解析每个 JSON 消息块
                    return handleJsonMessageChunk(chunk, chatHistoryStringBuilder, seenToolIds, toolStreamState);
                })
                .filter(StrUtil::isNotEmpty) // 过滤空字串

                .doOnComplete(() -> {
                    // 流式响应完成后，添加 AI 消息到对话历史
                    String aiResponse = chatHistoryStringBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 解析并收集 TokenStream 数据
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder chatHistoryStringBuilder, Set<String> seenToolIds,
                                          ToolStreamState toolStreamState) {
        // 解析 JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        switch (typeEnum) {
            case AI_RESPONSE -> {
                AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = sanitizeVisibleStreamText(aiMessage.getData());
                // 直接拼接响应
                chatHistoryStringBuilder.append(data);
                return data;
            }
            case TOOL_REQUEST -> {
                StringBuilder output = new StringBuilder();
                if (!seenToolIds.contains(GENERATION_HEADER_MARKER)) {
                    seenToolIds.add(GENERATION_HEADER_MARKER);
                    if (chatHistoryStringBuilder.isEmpty()) {
                        String header = "为你生成代码：\n\n";
                        chatHistoryStringBuilder.append(header);
                        output.append(header);
                    } else if (!endsWithLineBreak(chatHistoryStringBuilder)) {
                        chatHistoryStringBuilder.append("\n");
                        output.append("\n");
                    }
                }
                ToolRequestMessage toolRequestMessage = JSONUtil.toBean(chunk, ToolRequestMessage.class);
                boolean shouldStartOnNewLine = !endsWithLineBreak(chatHistoryStringBuilder) && output.isEmpty();
                String partialOutput = renderPartialToolRequest(toolRequestMessage, toolStreamState, shouldStartOnNewLine);
                chatHistoryStringBuilder.append(partialOutput);
                output.append(partialOutput);
                return output.toString();
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String relativeFilePath = jsonObject.getStr("relativeFilePath");
                String suffix = FileUtil.getSuffix(relativeFilePath);
                String content = jsonObject.getStr("content");
                String result = String.format("""
                        [工具调用] 写入文件 %s
                        ```%s
                        %s
                        ```
                        """, relativeFilePath, suffix, content);
                // 输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                /*
                 * 当前 SSE 可以把完整文件内容返回给前端，方便用户看到生成过程；
                 * 但写入长期对话历史时，不能把完整代码块也塞进去。
                 *
                 * 原因：
                 * 1. 后续生成会把 chat_history 加载进 ChatMemory；
                 * 2. 如果历史里是一大坨代码块，模型会把这些内容当成对话样例继续复读；
                 * 3. 用户说“改成紫色”时，模型可能只回复“已改成紫色”，却不真实调用工具；
                 * 4. 现在 chat_history 会保存完整展示内容，后续写入 ChatMemory 前再清洗代码块。
                 */
                if (toolStreamState.markExecuted(getToolStreamKey(toolExecutedMessage.getId(), toolExecutedMessage.getName()), relativeFilePath)) {
                    String completionOutput = "\n```\n\n[工具调用] 写入完成 " + relativeFilePath + "\n\n";
                    chatHistoryStringBuilder.append(completionOutput);
                    return completionOutput;
                }
                chatHistoryStringBuilder.append(output);
                return output;
            }
            default -> {
                log.error("不支持的消息类型: {}", typeEnum);
                return "";
            }
        }
    }

    private String renderPartialToolRequest(ToolRequestMessage toolRequestMessage, ToolStreamState toolStreamState,
                                            boolean shouldStartOnNewLine) {
        String arguments = toolRequestMessage.getArguments();
        if (StrUtil.isBlank(arguments)) {
            return "";
        }
        String key = getToolStreamKey(toolRequestMessage.getId(), toolRequestMessage.getName(), toolRequestMessage.getIndex());
        String accumulatedArguments = toolStreamState.appendArguments(key, arguments);
        String relativeFilePath = extractJsonStringValue(accumulatedArguments, "relativeFilePath");
        String content = extractJsonStringValue(accumulatedArguments, "content");
        if (StrUtil.isBlank(relativeFilePath) || content == null) {
            return "";
        }

        PartialToolFile partialToolFile = toolStreamState.getOrCreatePartialFile(key, relativeFilePath);
        StringBuilder output = new StringBuilder();
        if (!partialToolFile.opened) {
            partialToolFile.opened = true;
            partialToolFile.relativeFilePath = relativeFilePath;
            if (shouldStartOnNewLine) {
                output.append("\n");
            }
            output.append("[正在编写] ").append(relativeFilePath).append("\n");
            output.append("```").append(FileUtil.getSuffix(relativeFilePath)).append("\n");
        }

        if (content.length() > partialToolFile.visibleContentLength) {
            output.append(content.substring(partialToolFile.visibleContentLength));
            partialToolFile.visibleContentLength = content.length();
        }
        return output.toString();
    }

    private boolean endsWithLineBreak(StringBuilder builder) {
        return !builder.isEmpty() && builder.charAt(builder.length() - 1) == '\n';
    }

    private String getToolStreamKey(String id, String name) {
        return getToolStreamKey(id, name, null);
    }

    private String getToolStreamKey(String id, String name, Integer index) {
        if (index != null) {
            return "index:" + index;
        }
        if (StrUtil.isNotBlank(id)) {
            return "id:" + id;
        }
        if (StrUtil.isNotBlank(name)) {
            return "name:" + name;
        }
        return "__default_write_file__";
    }

    private String extractJsonStringValue(String json, String fieldName) {
        String key = "\"" + fieldName + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex + key.length());
        if (colonIndex < 0) {
            return null;
        }
        int valueStart = colonIndex + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return null;
        }

        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = valueStart + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escaping) {
                appendEscapedCharacter(value, ch);
                escaping = false;
                continue;
            }
            if (ch == '\\') {
                escaping = true;
                continue;
            }
            if (ch == '"') {
                break;
            }
            value.append(ch);
        }
        return value.toString();
    }

    private void appendEscapedCharacter(StringBuilder value, char ch) {
        switch (ch) {
            case 'n' -> value.append('\n');
            case 'r' -> value.append('\r');
            case 't' -> value.append('\t');
            case 'b' -> value.append('\b');
            case 'f' -> value.append('\f');
            case '"', '\\', '/' -> value.append(ch);
            default -> value.append(ch);
        }
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

    private static class ToolStreamState {
        private final Map<String, StringBuilder> argumentBuffers = new HashMap<>();
        private final Map<String, PartialToolFile> partialFiles = new HashMap<>();

        private String appendArguments(String key, String arguments) {
            StringBuilder argumentBuffer = argumentBuffers.computeIfAbsent(key, ignored -> new StringBuilder());
            argumentBuffer.append(arguments);
            return argumentBuffer.toString();
        }

        private PartialToolFile getOrCreatePartialFile(String key, String relativeFilePath) {
            PartialToolFile existing = partialFiles.get(key);
            if (existing != null) {
                return existing;
            }
            if (partialFiles.size() == 1) {
                Map.Entry<String, PartialToolFile> entry = partialFiles.entrySet().iterator().next();
                PartialToolFile partialToolFile = entry.getValue();
                if (StrUtil.equals(partialToolFile.relativeFilePath, relativeFilePath)) {
                    partialFiles.remove(entry.getKey());
                    partialFiles.put(key, partialToolFile);
                    return partialToolFile;
                }
            }
            PartialToolFile partialToolFile = new PartialToolFile();
            partialFiles.put(key, partialToolFile);
            return partialToolFile;
        }

        private boolean markExecuted(String key, String relativeFilePath) {
            PartialToolFile partialToolFile = partialFiles.get(key);
            if (partialToolFile == null && StrUtil.isNotBlank(relativeFilePath)) {
                for (Map.Entry<String, PartialToolFile> entry : partialFiles.entrySet()) {
                    if (StrUtil.equals(entry.getValue().relativeFilePath, relativeFilePath)) {
                        partialToolFile = entry.getValue();
                        key = entry.getKey();
                        break;
                    }
                }
            }
            if (partialToolFile == null || !partialToolFile.opened) {
                return false;
            }
            partialFiles.remove(key);
            argumentBuffers.remove(key);
            return true;
        }
    }

    private static class PartialToolFile {
        private String relativeFilePath;
        private int visibleContentLength;
        private boolean opened;
    }
}
