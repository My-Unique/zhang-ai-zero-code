package com.unique.zhangaizerocode.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 应用生成附件信息。
 */
@Data
public class AppAssetVO implements Serializable {

    private String name;

    private String url;

    private String contentType;

    private Long size;

    private String textContent;
}
