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
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

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
                               Long versionNo,
                               User loginUser) {
        StringBuilder chatHistoryStringBuilder = new StringBuilder();
        Set<String> seenToolIds = new HashSet<>();
        return originFlux
                .flatMapIterable(chunk -> handleJsonMessageChunk(
                        chunk, chatHistoryStringBuilder, seenToolIds, appId, versionNo))
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

    private List<String> handleJsonMessageChunk(String chunk,
                                                StringBuilder chatHistoryStringBuilder,
                                                Set<String> seenToolIds,
                                                long appId,
                                                Long versionNo) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum typeEnum = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (typeEnum == null) {
            log.warn("不支持的消息类型: {}", streamMessage.getType());
            return List.of("");
        }
        List<String> outputs = switch (typeEnum) {
            case AI_RESPONSE -> List.of(handleAiResponse(chunk, chatHistoryStringBuilder));
            case TOOL_REQUEST -> List.of(handleToolRequest(chunk, chatHistoryStringBuilder, seenToolIds));
            case TOOL_EXECUTED -> handleToolExecuted(chunk, chatHistoryStringBuilder, appId, versionNo);
        };
        return outputs;
    }

    private String handleAiResponse(String chunk, StringBuilder chatHistoryStringBuilder) {
        AiResponseMessage aiMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
        String data = sanitizeVisibleStreamText(aiMessage.getData());
        chatHistoryStringBuilder.append(data);
        return data;
    }

    private String handleToolRequest(String chunk,
                                     StringBuilder chatHistoryStringBuilder,
                                     Set<String> seenToolIds) {
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

        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            log.warn("未找到工具实例，toolName: {}", toolName);
            return "";
        }
        String output = buildToolRequestOutput(toolRequestMessage, tool);
        if (StrUtil.isBlank(output)) {
            return "";
        }
        seenToolIds.add(toolKey);
        chatHistoryStringBuilder.append(output);
        return output;
    }

    private String buildToolRequestOutput(ToolRequestMessage toolRequestMessage, BaseTool tool) {
        /*
         * 文件变更工具（writeFile / modifyFile / deleteFile）必须能提取到相对路径才输出。
         *
         * 流式调用时，onPartialToolExecutionRequest 的参数是逐步累积的，
         * 前几帧可能因为 JSON 不完整而导致 extractRelativeFilePath 返回空。
         * 此时返回空字符串，handleToolRequest 不会将其加入 seenToolIds，
         * 后续参数完整的帧仍有机会展示 "[正在编写] xxx.vue"。
         *
         * 非文件变更工具（readFile / readDir）没有相对路径可提取，
         * 直接使用泛用响应即可。
         */
        if (isFileMutationTool(toolRequestMessage.getName())) {
            String relativeFilePath = extractRelativeFilePath(toolRequestMessage.getArguments());
            if (StrUtil.isNotBlank(relativeFilePath)) {
                return String.format("\n[正在编写] %s\n", relativeFilePath);
            }
            return "";
        }
        return tool.generateToolRequestResponse();
    }

    /**
     * 从工具调用参数中提取 relativeFilePath。
     *
     * 使用正则直接匹配 {@code "relativeFilePath":"xxx"}，不依赖完整 JSON 解析。
     * 原因是流式调用时参数是逐步累积的，即使累积到以 "}" 结尾也不代表 JSON 完整
     * —— content 字段内可能包含 Vue 模板中的 "}" 字符，导致 JSON 解析器报
     * "Unterminated string" 等不可恢复的错误。
     *
     * relativeFilePath 的值是简单文件路径（如 src/App.vue），不包含转义引号，
     * 在整个参数 JSON 中最早完成累积，正则提取完全可靠。
     */
    private static final java.util.regex.Pattern RELATIVE_FILE_PATH_PATTERN =
            java.util.regex.Pattern.compile("\"relativeFilePath\"\\s*:\\s*\"([^\"]*)\"");

    private String extractRelativeFilePath(String arguments) {
        if (StrUtil.isBlank(arguments)) {
            return "";
        }
        java.util.regex.Matcher matcher = RELATIVE_FILE_PATH_PATTERN.matcher(arguments);
        if (matcher.find()) {
            return StrUtil.blankToDefault(matcher.group(1), "");
        }
        return "";
    }

    private boolean isFileMutationTool(String toolName) {
        return Set.of("writeFile", "modifyFile", "deleteFile").contains(toolName);
    }

    private List<String> handleToolExecuted(String chunk,
                                            StringBuilder chatHistoryStringBuilder,
                                            long appId,
                                            Long versionNo) {
        ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        String toolName = toolExecutedMessage.getName();
        BaseTool tool = toolManager.getTool(toolName);
        if (tool == null) {
            log.warn("未找到工具实例，toolName: {}", toolName);
            return List.of("");
        }

        JSONObject arguments = JSONUtil.parseObj(toolExecutedMessage.getArguments());
        String result = tool.generateToolExecutedResult(arguments);
        String output = String.format("\n%s\n", result);
        chatHistoryStringBuilder.append(output);
        List<String> outputs = new ArrayList<>();
        outputs.add(output);
        String fileSnapshotEvent = buildGeneratedFileSnapshotEvent(toolName, arguments, toolExecutionResult(toolExecutedMessage),
                appId, versionNo);
        if (StrUtil.isNotBlank(fileSnapshotEvent)) {
            outputs.add(fileSnapshotEvent);
        }
        return outputs;
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

    private String toolExecutionResult(ToolExecutedMessage toolExecutedMessage) {
        return StrUtil.blankToDefault(toolExecutedMessage.getResult(), "");
    }

    private String buildGeneratedFileSnapshotEvent(String toolName,
                                                   JSONObject arguments,
                                                   String toolResult,
                                                   long appId,
                                                   Long versionNo) {
        if (!Set.of("writeFile", "modifyFile", "deleteFile").contains(toolName)) {
            return "";
        }
        try {
            Path projectRoot = projectRoot(appId, versionNo);
            if (!Files.exists(projectRoot) || !Files.isDirectory(projectRoot)) {
                return "";
            }
            String relativeFilePath = arguments.getStr("relativeFilePath");
            List<String> files = listProjectFiles(projectRoot);

            JSONObject event = JSONUtil.createObj()
                    .set("type", "generated_file_snapshot")
                    .set("toolName", toolName)
                    .set("action", resolveAction(toolName))
                    .set("actionLabel", resolveActionLabel(toolName))
                    .set("path", relativeFilePath)
                    .set("language", inferLanguage(relativeFilePath))
                    .set("files", files)
                    .set("result", toolResult)
                    .set("deleted", "deleteFile".equals(toolName));

            if (StrUtil.isNotBlank(relativeFilePath) && !"deleteFile".equals(toolName)) {
                Path filePath = resolveFile(projectRoot, relativeFilePath);
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    event.set("content", Files.readString(filePath));
                }
            }
            return event.toString();
        } catch (Exception e) {
            log.warn("构建生成中文件快照失败，appId: {}, versionNo: {}, toolName: {}",
                    appId, versionNo, toolName, e);
            return "";
        }
    }

    private Path projectRoot(long appId, Long versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw new IllegalArgumentException("versionNo is required");
        }
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId, "v" + versionNo)
                .toAbsolutePath()
                .normalize();
    }

    private Path resolveFile(Path projectRoot, String relativeFilePath) {
        if (StrUtil.isBlank(relativeFilePath)) {
            throw new IllegalArgumentException("relativeFilePath is required");
        }
        Path relativePath = Path.of(relativeFilePath).normalize();
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("absolute path is not allowed");
        }
        Path targetPath = projectRoot.resolve(relativePath).normalize();
        if (!targetPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException("path cannot escape project root");
        }
        return targetPath;
    }

    private List<String> listProjectFiles(Path projectRoot) throws IOException {
        try (Stream<Path> stream = Files.walk(projectRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> projectRoot.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                    .filter(this::isDisplayableSourceFile)
                    .sorted()
                    .limit(500)
                    .toList();
        }
    }

    private boolean isDisplayableSourceFile(String filePath) {
        String normalized = filePath.replace('\\', '/');
        if (normalized.startsWith("node_modules/")
                || normalized.startsWith("dist/")
                || normalized.startsWith(".git/")
                || normalized.startsWith("coverage/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/dist/")
                || normalized.contains("/coverage/")) {
            return false;
        }
        String lower = normalized.toLowerCase();
        return lower.endsWith(".html")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.endsWith(".jsx")
                || lower.endsWith(".ts")
                || lower.endsWith(".tsx")
                || lower.endsWith(".vue")
                || lower.endsWith(".json")
                || lower.endsWith(".md")
                || lower.endsWith(".txt")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml");
    }

    private String resolveAction(String toolName) {
        return switch (toolName) {
            case "modifyFile" -> "modify";
            case "deleteFile" -> "delete";
            default -> "write";
        };
    }

    private String resolveActionLabel(String toolName) {
        return switch (toolName) {
            case "modifyFile" -> "修改文件";
            case "deleteFile" -> "删除文件";
            default -> "写入文件";
        };
    }

    private String inferLanguage(String filePath) {
        if (StrUtil.isBlank(filePath)) {
            return "text";
        }
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".vue")) return "vue";
        if (lower.endsWith(".html")) return "html";
        if (lower.endsWith(".css")) return "css";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "typescript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "javascript";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".md")) return "markdown";
        return "text";
    }
}
