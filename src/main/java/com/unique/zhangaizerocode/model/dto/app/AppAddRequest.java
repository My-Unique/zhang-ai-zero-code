package com.unique.zhangaizerocode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AppAddRequest implements Serializable {

    /**
     * 应用初始化的 prompt
     */
    private String initPrompt;

    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * 代码生成类型：html / multi_file
     */
    private String codeGenType;
}
