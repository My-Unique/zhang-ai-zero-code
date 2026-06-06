package com.unique.zhangaizerocode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum AppGenerationStatusEnum {

    NOT_GENERATED("未生成", "not_generated"),
    GENERATING("生成中", "generating"),
    SUCCEEDED("生成成功", "succeeded"),
    FAILED("生成失败", "failed");

    private final String text;
    private final String value;

    AppGenerationStatusEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static AppGenerationStatusEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppGenerationStatusEnum anEnum : AppGenerationStatusEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
