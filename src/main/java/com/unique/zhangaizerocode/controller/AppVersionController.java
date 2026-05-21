package com.unique.zhangaizerocode.controller;

import com.unique.zhangaizerocode.annotation.AuthCheck;
import com.unique.zhangaizerocode.common.BaseResponse;
import com.unique.zhangaizerocode.common.ResultUtils;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.dto.app.AppRollbackVersionRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionDiffRequest;
import com.unique.zhangaizerocode.model.vo.AppVersionDiffVO;
import com.unique.zhangaizerocode.model.vo.AppVersionVO;
import com.unique.zhangaizerocode.service.AppService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 应用版本管理接口
 *
 * 说明：
 * 1. 这些接口都要求用户登录
 * 2. 具体是否有权操作某个应用，由 AppServiceImpl 判断：
 *    应用创建者 or 管理员 才能操作
 */
@RestController
@RequestMapping("/app/version")
public class AppVersionController {

    @Resource
    private AppService appService;


    /**
     * 查询应用历史版本
     */
    @GetMapping("/list")
    @AuthCheck
    public BaseResponse<List<AppVersionVO>> listAppVersions(@RequestParam Long appId,
                                                            HttpServletRequest httpServletRequest) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }

        List<AppVersionVO> versionVOList = appService.listAppVersions(appId, httpServletRequest);
        return ResultUtils.success(versionVOList);
    }

    /**
     * 回退到指定历史版本
     */
    @PostMapping("/rollback")
    @AuthCheck
    public BaseResponse<Boolean> rollbackVersion(@RequestBody AppRollbackVersionRequest request,
                                                 HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (request.getAppId() == null || request.getAppId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        if (request.getVersionNo() == null || request.getVersionNo() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "版本号不能为空");
        }

        Boolean result = appService.rollbackVersion(request, httpServletRequest);
        return ResultUtils.success(result);
    }

    /**
     * 对比两个版本的代码差异
     */
    @PostMapping("/diff")
    @AuthCheck
    public BaseResponse<AppVersionDiffVO> diffVersion(@RequestBody AppVersionDiffRequest request,
                                                      HttpServletRequest httpServletRequest) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (request.getAppId() == null || request.getAppId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        }
        if (request.getOldVersionNo() == null || request.getOldVersionNo() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "旧版本号不能为空");
        }
        if (request.getNewVersionNo() == null || request.getNewVersionNo() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新版本号不能为空");
        }

        AppVersionDiffVO diffVO = appService.diffVersion(request, httpServletRequest);
        return ResultUtils.success(diffVO);
    }
}