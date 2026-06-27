package com.unique.zhangaizerocode.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AppVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用封面
     */
    private String cover;

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    /**
     * 代码生成类型（枚举）
     */
    private String codeGenType;

    /**
     * 部署标识
     */
    private String deployKey;

    /**
     * 部署时间
     */
    private LocalDateTime deployedTime;

    /**
     * 已部署版本号
     */
    private Long deployedVersionNo;

    /**
     * 下载次数
     */
    private Long downloadCount;

    /**
     * 生成状态：not_generated/generating/succeeded/failed
     */
    private String generationStatus;

    /**
     * 可见范围：private/public
     */
    private String visibility;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 当前代码版本号，版本号大于 0 表示应用已经生成过。
     */
    private Long versionNo;

    /**
     * 对话轮次，按用户消息数统计
     */
    private Long chatCount;

    /**
     * 创建用户信息
     */
    private UserVO user;

    private static final long serialVersionUID = 1L;
}
