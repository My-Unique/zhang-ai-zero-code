package com.unique.zhangaizerocode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppVersionDiffRequest implements Serializable {

    private Long appId;

    /**
     * 旧版本号，例如 1
     */
    private Long oldVersionNo;

    /**
     * 新版本号，例如 3
     */
    private Long newVersionNo;

    private String fileName;
}