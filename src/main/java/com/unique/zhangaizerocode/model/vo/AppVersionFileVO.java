package com.unique.zhangaizerocode.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AppVersionFileVO implements Serializable {

    private String path;

    private String content;
}
