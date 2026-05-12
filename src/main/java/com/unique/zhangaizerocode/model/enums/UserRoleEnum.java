package com.unique.zhangaizerocode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;


@Getter
public enum UserRoleEnum {

//    枚举里的常量，本质上可以理解成这个枚举类里面的 静态常量对象。
//    public static final UserRoleEnum USER = new UserRoleEnum("用户", "user");
//    public static final UserRoleEnum ADMIN = new UserRoleEnum("管理员", "admin");
    // 枚举常量定义，每个常量都有对应的文本和值
    // 第一个参数是显示文本，第二个参数是实际值
    USER("用户", "user"),    // 普通用户角色
    ADMIN("管理员", "admin"); // 管理员角色



    // 枚举实例的私有属性
    private final String text; // 角色显示文本

    private final String value; // 角色实际值

    // 枚举构造函数，私有化，只能由枚举常量调用
    // 用于初始化枚举实例的文本和值属性
    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     * 这是一个静态方法，用于根据传入的value值来匹配并返回对应的枚举实例
 *
     * @param value 枚举值的value，用于匹配枚举实例
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        // create_table.sql. 判断传进来的 value 是否为空
        // ObjUtil.isEmpty(value) 一般表示：
        // value == null 或者 value 是空字符串等情况
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 2. 遍历 UserRoleEnum 里面所有的枚举值
        // UserRoleEnum.values() 会返回这个枚举类的所有枚举对象
        // 比如：[USER, ADMIN]
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }


//    /**
//     * 用 Map 缓存 value 和枚举对象的对应关系
//     *
//     * key：枚举的 value，比如 "admin"
//     * value：对应的枚举对象，比如 UserRoleEnum.ADMIN
//     */
//    private static final Map<String, UserRoleEnum> VALUE_ENUM_MAP = new HashMap<>();
//
//    /**
//     * 静态代码块：
//     * 类加载的时候执行一次，把所有枚举值放进 Map 里
//     */
//    static {
//        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
//            VALUE_ENUM_MAP.put(anEnum.value, anEnum);
//        }
//    }
//
//    /**
//     * 根据 value 获取对应的枚举对象
//     */
//    public static UserRoleEnum getEnumByValueComplex(String value) {
//        if (ObjUtil.isEmpty(value)) {
//            return null;
//        }
//
//        return VALUE_ENUM_MAP.get(value);
//    }

}
