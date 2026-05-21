package com.unique.zhangaizerocode.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppVersionDiffVO implements Serializable {

    private String fileName;

    private String oldCode;

    private String newCode;
}