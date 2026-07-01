package com.unique.zhangaizerocode.core.builder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class VueProjectBuilder {

    private static final Pattern VITE_BASE_PATTERN = Pattern.compile("(base\\s*:\\s*)['\"][^'\"]*['\"]");
    private static final Pattern DEFINE_CONFIG_OBJECT_PATTERN = Pattern.compile("defineConfig\\(\\s*\\{");

    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目失败: {}", e.getMessage(), e);
            }
        });
    }

    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("Vue 项目目录不存在: {}", projectPath);
            return false;
        }

        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }

        log.info("开始构建 Vue 项目: {}", projectPath);
        ensureRelativeViteBase(projectDir);
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }

        File distDir = new File(projectDir, "dist");
        if (!distDir.exists() || !distDir.isDirectory()) {
            log.error("Vue 项目构建完成但未生成 dist 目录: {}", distDir.getAbsolutePath());
            return false;
        }
        patchDistIndexAssets(distDir);
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }

    private void ensureRelativeViteBase(File projectDir) {
        String[] configNames = {"vite.config.js", "vite.config.ts", "vite.config.mjs", "vite.config.mts"};
        for (String configName : configNames) {
            Path configPath = projectDir.toPath().resolve(configName);
            if (!Files.exists(configPath)) {
                continue;
            }
            try {
                String content = Files.readString(configPath, StandardCharsets.UTF_8);
                String updatedContent = normalizeViteBase(content);
                if (!updatedContent.equals(content)) {
                    Files.writeString(configPath, updatedContent, StandardCharsets.UTF_8);
                    log.info("已修正 Vite base 为相对路径: {}", configPath.toAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("修正 Vite base 失败，将继续尝试构建: {}", configPath.toAbsolutePath(), e);
            }
            return;
        }
    }

    private String normalizeViteBase(String content) {
        String repairedContent = content
                .replaceAll("base\\s*:\\s*['\"]\\.\\/['\"]\\s*,\\s*n\\s*base\\s*:\\s*['\"]\\.\\/['\"]\\s*,", "base: './',")
                .replaceAll("(?m)^\\s*base\\s*:\\s*['\"]\\.\\/['\"]\\s*,\\s*\\R\\s*base\\s*:\\s*['\"]\\.\\/['\"]\\s*,", "  base: './',");

        Matcher baseMatcher = VITE_BASE_PATTERN.matcher(repairedContent);
        if (baseMatcher.find()) {
            StringBuffer stringBuffer = new StringBuffer();
            do {
                baseMatcher.appendReplacement(
                        stringBuffer,
                        Matcher.quoteReplacement(baseMatcher.group(1) + "'./'")
                );
            } while (baseMatcher.find());
            baseMatcher.appendTail(stringBuffer);
            return stringBuffer.toString();
        }

        Matcher defineConfigMatcher = DEFINE_CONFIG_OBJECT_PATTERN.matcher(repairedContent);
        if (defineConfigMatcher.find()) {
            return defineConfigMatcher.replaceFirst(
                    Matcher.quoteReplacement("defineConfig({" + System.lineSeparator() + "  base: './',")
            );
        }
        return repairedContent;
    }

    private void patchDistIndexAssets(File distDir) {
        Path indexPath = distDir.toPath().resolve("index.html");
        if (!Files.exists(indexPath)) {
            return;
        }
        try {
            String content = Files.readString(indexPath, StandardCharsets.UTF_8);
            String updatedContent = content
                    .replace("src=\"/assets/", "src=\"./assets/")
                    .replace("href=\"/assets/", "href=\"./assets/")
                    .replace("src='/assets/", "src='./assets/")
                    .replace("href='/assets/", "href='./assets/");
            if (!updatedContent.equals(content)) {
                Files.writeString(indexPath, updatedContent, StandardCharsets.UTF_8);
                log.info("已修正 dist/index.html 静态资源路径: {}", indexPath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("修正 dist/index.html 静态资源路径失败: {}", indexPath.toAbsolutePath(), e);
        }
    }

    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300);
    }

    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180);
    }

    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            ProcessBuilder processBuilder = new ProcessBuilder(command.split("\\s+"));
            processBuilder.directory(workingDir);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            CompletableFuture<String> outputFuture = CompletableFuture.supplyAsync(
                    () -> readProcessOutput(process.getInputStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("命令执行超时（{} 秒），强制终止进程，已捕获输出: {}", timeoutSeconds, getCommandOutput(outputFuture));
                return false;
            }

            String output = getCommandOutput(outputFuture);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            }
            log.error("命令执行失败，退出码: {}，输出: {}", exitCode, output);
            return false;
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage(), e);
            return false;
        }
    }

    private String readProcessOutput(InputStream inputStream) {
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "读取命令输出失败: " + e.getMessage();
        }
    }

    private String getCommandOutput(CompletableFuture<String> outputFuture) {
        try {
            return outputFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            return "读取命令输出超时";
        } catch (Exception e) {
            return "读取命令输出失败: " + e.getMessage();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }
}
