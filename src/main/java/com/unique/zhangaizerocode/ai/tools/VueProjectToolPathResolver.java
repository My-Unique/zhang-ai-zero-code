package com.unique.zhangaizerocode.ai.tools;

import com.unique.zhangaizerocode.constant.AppConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Vue项目工具路径解析器
 * 提供Vue项目路径解析相关的功能，包括项目根路径、文件路径和目录路径的解析
 */
final class VueProjectToolPathResolver {

    /**
     * 私有构造方法，防止实例化工具类
     */
    private VueProjectToolPathResolver() {
    }

    /**
     * 获取Vue项目的根路径
     * @param appId 应用ID，必须大于0
     * @param versionNo 版本号，必须大于0
     * @return 项目根路径的绝对路径
     * @throws IllegalArgumentException 如果appId或versionNo无效
     */
    static Path projectRoot(Long appId, Long versionNo) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("appId is required");
        }
        if (versionNo == null || versionNo <= 0) {
            throw new IllegalArgumentException("versionNo is required");
        }
        // 构建项目目录名称，格式为"vue_appId"
        String projectDirName = "vue_project_" + appId;
        // 返回完整的项目根路径，格式为"输出根目录/vue_appId/v版本号"
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName, "v" + versionNo)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * 解析Vue项目中的文件路径
     * @param appId 应用ID
     * @param versionNo 版本号
     * @param relativeFilePath 相对文件路径
     * @return 解析后的绝对文件路径
     */
    static Path resolveFile(Long appId, Long versionNo, String relativeFilePath) {
        return resolve(appId, versionNo, relativeFilePath, false);
    }

    /**
     * 解析Vue项目中的目录路径
     * @param appId 应用ID
     * @param versionNo 版本号
     * @param relativeDirPath 相对目录路径
     * @return 解析后的绝对目录路径
     */
    static Path resolveDirectory(Long appId, Long versionNo, String relativeDirPath) {
        return resolve(appId, versionNo, relativeDirPath, true);
    }

    /**
     * 内部路径解析方法
     * @param appId 应用ID
     * @param versionNo 版本号
     * @param relativePath 相对路径
     * @param allowBlank 是否允许为空
     * @return 解析后的绝对路径
     * @throws IllegalArgumentException 如果路径无效或试图逃逸项目目录
     */
    private static Path resolve(Long appId, Long versionNo, String relativePath, boolean allowBlank) {
        // 检查相对路径是否为空
        if (relativePath == null || relativePath.isBlank()) {
            if (allowBlank) {
                relativePath = "";
            } else {
                throw new IllegalArgumentException("relative path is required");
            }
        }
        // 获取项目根路径
        Path projectRoot = projectRoot(appId, versionNo);
        // 规范化相对路径
        Path requestedPath = Paths.get(relativePath).normalize();
        // 检查是否为绝对路径
        if (requestedPath.isAbsolute()) {
            throw new IllegalArgumentException("absolute path is not allowed");
        }
        // 解析目标路径
        Path targetPath = projectRoot.resolve(requestedPath).normalize();
        // 检查路径是否在项目目录内
        if (!targetPath.startsWith(projectRoot)) {
            throw new IllegalArgumentException("path cannot escape project version directory");
        }
        return targetPath;
    }
}
