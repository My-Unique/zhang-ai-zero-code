package com.unique.zhangaizerocode.controller;

import com.unique.zhangaizerocode.common.BaseResponse;
import com.unique.zhangaizerocode.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
public class HealthController {

/**
 * 健康检查接口
 * 用于系统运行状态检测
 * @return 返回系统运行状态字符串"ok"
 */
    @GetMapping("/")    // HTTP GET请求映射，根路径"/"
    public BaseResponse<String> healthCheck() {    // 健康检查方法，返回系统状态

        return ResultUtils.success("ok");    // 返回系统运行状态，"ok"表示系统正常运行
    }
}
