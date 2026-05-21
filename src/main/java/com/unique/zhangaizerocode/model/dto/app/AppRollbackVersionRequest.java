package com.unique.zhangaizerocode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppRollbackVersionRequest implements Serializable {

    /**
     * 应用 id
     */
    private Long appId;

    /**
     * 要回退到的版本号
     */
    private Long versionNo;
}