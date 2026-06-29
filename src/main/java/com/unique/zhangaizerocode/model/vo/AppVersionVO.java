package com.unique.zhangaizerocode.model.vo;

import cn.hutool.core.date.DateTime;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class AppVersionVO implements Serializable {

    private Long id;

    private Long appId;

    private Long versionNo;

    private String userMessage;

    private String codePath;

    private String cover;

    private LocalDateTime createTime;
}
