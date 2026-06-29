package com.unique.zhangaizerocode.ai.tools;

import lombok.extern.slf4j.Slf4j;
import com.unique.zhangaizerocode.model.enums.AppGenerationModeEnum;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具管理器
 * 统一管理所有工具，提供根据名称获取工具的功能
 */
@Slf4j
@Component
public class ToolManager implements InitializingBean {

    /**
     * 工具名称到工具实例的映射
     */
    private final Map<String, BaseTool> toolMap = new HashMap<>();

    /**
     * 初始化工具映射
     */
    @Override
    public void afterPropertiesSet() {
        for (BaseTool tool : createTools(1L)) {
            toolMap.put(tool.getToolName(), tool);
            log.info("注册工具: {} -> {}", tool.getToolName(), tool.getDisplayName());
        }
        log.info("工具管理器初始化完成，共注册 {} 个工具", toolMap.size());
    }

    /**
     * 根据工具名称获取工具实例
     *
     * @param toolName 工具英文名称
     * @return 工具实例
     */
    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    /**
     * 获取已注册的工具集合
     *
     * @return 工具实例集合
     */
    public BaseTool[] getAllTools() {
        return getAllTools(1L);
    }

    /**
     * 获取指定版本的工具集合。
     */
    public BaseTool[] getAllTools(Long versionNo) {
        return createTools(versionNo);
    }

    /**
     * 获取指定生成模式下可用的工具集合。
     */
    public BaseTool[] getTools(Long versionNo, AppGenerationModeEnum generationMode) {
        if (generationMode == AppGenerationModeEnum.CREATE) {
            return new BaseTool[]{
                    new FileWriteTool(versionNo),
                    new FileDirReadTool(versionNo)
            };
        }
        return createTools(versionNo);
    }

    /**
     * 获取指定版本可用的工具名称，方便生成链路日志排查。
     */
    public String getToolNames(Long versionNo) {
        return Arrays.stream(createTools(versionNo))
                .map(BaseTool::getToolName)
                .collect(Collectors.joining(", "));
    }

    /**
     * 获取指定生成模式下可用的工具名称，方便生成链路日志排查。
     */
    public String getToolNames(Long versionNo, AppGenerationModeEnum generationMode) {
        return Arrays.stream(getTools(versionNo, generationMode))
                .map(BaseTool::getToolName)
                .collect(Collectors.joining(", "));
    }

    private BaseTool[] createTools(Long versionNo) {
        return new BaseTool[]{
                new FileWriteTool(versionNo),
                new FileReadTool(versionNo),
                new FileModifyTool(versionNo),
                new FileDirReadTool(versionNo),
                new FileDeleteTool(versionNo)
        };
    }
}
