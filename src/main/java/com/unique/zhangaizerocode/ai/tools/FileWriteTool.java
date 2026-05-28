package com.unique.zhangaizerocode.ai.tools;

import com.unique.zhangaizerocode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
public class FileWriteTool {

    private final Long versionNo;

    public FileWriteTool(Long versionNo) {
        this.versionNo = versionNo;
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

            String projectDirName = "vue_project_" + appId;
            Path projectRoot = Paths.get(
                    AppConstant.CODE_OUTPUT_ROOT_DIR,
                    projectDirName,
                    "v" + versionNo
            ).toAbsolutePath().normalize();

            /*
             * AI 只能传相对路径，例如 index.html、src/App.vue。
             * 这里先把 AI 传入的字符串解析成 Path，但不能直接写入这个路径：
             * 1. 如果是绝对路径，可能绕过当前项目目录，必须拒绝；
             * 2. 如果包含 ../，后续 normalize 后可能逃逸到版本目录外，必须二次校验；
             * 3. 所有文件最终都必须落在 projectRoot，也就是当前 app 当前版本的目录里。
             */
            Path requestedPath = Paths.get(relativeFilePath).normalize();
            if (requestedPath.isAbsolute()) {
                return "文件写入失败: 不允许使用绝对路径";
            }

            /*
             * 先用 projectRoot 拼接相对路径，再 normalize 消除 ./ 和 ../。
             * normalize 之后再检查 startsWith(projectRoot)，可以拦住 ../../pom.xml 这类路径逃逸。
             */
            Path targetPath = projectRoot.resolve(requestedPath).normalize();
            if (!targetPath.startsWith(projectRoot)) {
                return "文件写入失败: 文件路径不能超出项目目录";
            }

            Path parentDir = targetPath.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }

            Files.write(targetPath, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("成功写入文件: {}", targetPath);
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException | InvalidPathException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
}
