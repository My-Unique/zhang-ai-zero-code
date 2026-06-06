package com.unique.zhangaizerocode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum AppVisibilityEnum {

    PRIVATE("私有", "private"),
    PUBLIC("公开", "public");

    private final String text;
    private final String value;

    AppVisibilityEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    public static AppVisibilityEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (AppVisibilityEnum anEnum : AppVisibilityEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }
}
