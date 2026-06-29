package com.unique.zhangaizerocode.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
public class FileWriteTool extends BaseTool {

    private final Long versionNo;

    public FileWriteTool(Long versionNo) {
        this.versionNo = versionNo;
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件的相对路径")
            String relativeFilePath,
            @P("要写入文件的内容")
            String content,
            @ToolMemoryId Long appId
    ) {
        try {
            if (relativeFilePath == null || relativeFilePath.isBlank()) {
                return "文件写入失败: 文件路径不能为空";
            }
            String sourceValidationError = validateSourceContent(relativeFilePath, content);
            if (sourceValidationError != null) {
                return sourceValidationError;
            }

            /*
             * AI 只能传相对路径，例如 index.html、src/App.vue。
             * 这里先把 AI 传入的字符串解析成 Path，但不能直接写入这个路径：
             * 1. 如果是绝对路径，可能绕过当前项目目录，必须拒绝；
             * 2. 如果包含 ../，后续 normalize 后可能逃逸到版本目录外，必须二次校验；
             * 3. 所有文件最终都必须落在 projectRoot，也就是当前 app 当前版本的目录里。
             */
            Path targetPath = VueProjectToolPathResolver.resolveFile(appId, versionNo, relativeFilePath);

            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            Files.write(targetPath, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("成功写入文件: {}", targetPath);
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException | IllegalArgumentException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * Prevent clarification text from being saved as app source code.
     */
    private String validateSourceContent(String relativeFilePath, String content) {
        String suffix = FileUtil.getSuffix(relativeFilePath);
        if (suffix == null) {
            return null;
        }
        String normalizedSuffix = suffix.toLowerCase(Locale.ROOT);
        if (!Set.of("html", "vue").contains(normalizedSuffix)) {
            return null;
        }
        if (content == null || content.isBlank()) {
            return "File write failed: " + relativeFilePath + " content is empty";
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        if ("html".equals(normalizedSuffix) && !looksLikeHtmlSource(lowerContent)) {
            return "File write failed: " + relativeFilePath
                    + " content does not look like HTML source. Do not write clarification text into source files.";
        }
        if ("vue".equals(normalizedSuffix) && !looksLikeVueSource(lowerContent)) {
            return "File write failed: " + relativeFilePath
                    + " content does not look like Vue SFC source. Do not write clarification text into source files.";
        }
        return null;
    }

    private boolean looksLikeHtmlSource(String lowerContent) {
        return lowerContent.contains("<!doctype")
                || lowerContent.contains("<html")
                || lowerContent.contains("<head")
                || lowerContent.contains("<body")
                || lowerContent.contains("<script")
                || lowerContent.contains("<style")
                || lowerContent.contains("<div");
    }

    private boolean looksLikeVueSource(String lowerContent) {
        return lowerContent.contains("<template")
                || lowerContent.contains("<script")
                || lowerContent.contains("<style");
    }
}
