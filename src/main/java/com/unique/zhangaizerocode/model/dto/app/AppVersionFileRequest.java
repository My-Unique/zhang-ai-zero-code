package com.unique.zhangaizerocode.model.dto.app;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppVersionFileRequest implements Serializable {

    private Long appId;

    private Long versionNo;

    private String filePath;

    private String content;
}
