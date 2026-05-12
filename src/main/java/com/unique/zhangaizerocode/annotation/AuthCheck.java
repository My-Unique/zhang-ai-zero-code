package com.unique.zhangaizerocode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 这是一个注解定义，用于方法级别的权限检查
@Target(ElementType.METHOD)  // 表示此注解只能用于方法上
@Retention(RetentionPolicy.RUNTIME)  // 表示此注解会在运行时保留，可以通过反射获取
public @interface AuthCheck {  // 定义一个名为AuthCheck的注解

    /**
     * 必须有某个角色
     * 这是一个默认值为空字符串的字符串类型注解元素
     * 用于指定访问该方法所需的用户角色
     */
    String mustRole() default "";  // 定义一个名为mustRole的注解元素，默认值为空字符串
}
