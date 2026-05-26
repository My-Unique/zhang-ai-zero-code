package com.unique.zhangaizerocode.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.core.AiCodeGeneratorFacade;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.mapper.AppMapper;
import com.unique.zhangaizerocode.model.dto.app.AppQueryRequest;
import com.unique.zhangaizerocode.model.dto.app.AppRollbackVersionRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionDiffRequest;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.AppVersion;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.model.vo.AppVO;
import com.unique.zhangaizerocode.model.vo.AppVersionDiffVO;
import com.unique.zhangaizerocode.model.vo.AppVersionVO;
import com.unique.zhangaizerocode.model.vo.UserVO;
import com.unique.zhangaizerocode.service.AppService;
import com.unique.zhangaizerocode.service.AppVersionService;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import com.unique.zhangaizerocode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {
    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private AppVersionService appVersionService;
    @Resource
    private ChatHistoryService chatHistoryService;


    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        Long maxVersionNo = appVersionService.getMaxVersionNo(appId);
        Long newVersionNo = maxVersionNo + 1;
        String codePath = AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + codeGenTypeEnum.getValue() + "_" + appId
                + File.separator + "v" + newVersionNo;
        // 5. 调用 AI 生成代码
        chatHistoryService.addChatMessage(
                appId,
                message,
                ChatHistoryMessageTypeEnum.USER.getValue(),
                loginUser.getId()
        );

        StringBuilder aiResponseBuilder = new StringBuilder();
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId, newVersionNo);
        return contentFlux
                .doOnNext(aiResponseBuilder::append)
                .doOnComplete(() -> {
                    // 保存 app_version 表
                    String aiResponse = aiResponseBuilder.toString();
                    if (StrUtil.isNotBlank(aiResponse)) {
                        chatHistoryService.addChatMessage(
                                appId,
                                aiResponse,
                                ChatHistoryMessageTypeEnum.AI.getValue(),
                                loginUser.getId()
                        );
                    }

                    AppVersion appVersion = new AppVersion();
                    appVersion.setAppId(appId);
                    appVersion.setVersionNo(newVersionNo);
                    appVersion.setUserMessage(message);
                    appVersion.setCodePath(codePath);
                    appVersion.setCreateUser(loginUser.getId());
                    appVersion.setIsDelete(0);

                    boolean saveResult = appVersionService.save(appVersion);
                    ThrowUtils.throwIf(!saveResult, ErrorCode.OPERATION_ERROR, "保存应用版本失败");

                    // 更新 app 当前版本
                    App updateApp = new App();
                    updateApp.setId(appId);
                    updateApp.setCurrentVersionId(appVersion.getId());
                    updateApp.setVersionNo(newVersionNo);
                    updateApp.setEditTime(LocalDateTime.now());

                    boolean updateResult = this.updateById(updateApp);
                    ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用当前版本失败");
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(
                            appId,
                            errorMessage,
                            ChatHistoryMessageTypeEnum.AI.getValue(),
                            loginUser.getId()
                    );
                });
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }

        Long versionNo = app.getVersionNo();
        ThrowUtils.throwIf(versionNo == null || versionNo <= 0,
                ErrorCode.OPERATION_ERROR, "应用暂无可部署版本，请先生成代码");

        AppVersion appVersion = appVersionService.getByAppIdAndVersionNo(appId, versionNo);
        ThrowUtils.throwIf(appVersion == null,
                ErrorCode.NOT_FOUND_ERROR, "当前版本记录不存在");

        String deployKey = app.getDeployKey();
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }

        // 复制当前版本到部署目录
        syncVersionToDeployDir(appVersion, deployKey);

        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());

        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }


    private void checkAppAuth(App app, User loginUser) {
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }

        if (!Objects.equals(app.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    @Override
    public List<AppVersionVO> listAppVersions(Long appId, HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        App app = this.getById(appId);
        checkAppAuth(app, loginUser);

        List<AppVersion> versionList = appVersionService.listByAppId(appId);

        return versionList.stream().map(version -> {
            AppVersionVO vo = new AppVersionVO();
            vo.setId(version.getId());
            vo.setAppId(version.getAppId());
            vo.setVersionNo(version.getVersionNo());
            vo.setUserMessage(version.getUserMessage());
            vo.setCodePath(version.getCodePath());
            vo.setCreateTime(version.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean rollbackVersion(AppRollbackVersionRequest request, HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        Long appId = request.getAppId();
        Long versionNo = request.getVersionNo();

        App app = this.getById(appId);
        checkAppAuth(app, loginUser);

        // 根据 appId + versionNo 查版本
        AppVersion appVersion = appVersionService.getByAppIdAndVersionNo(appId, versionNo);
        if (appVersion == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        }

        // 1. 先更新 app 当前版本
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCurrentVersionId(appVersion.getId());
        updateApp.setVersionNo(appVersion.getVersionNo());
        updateApp.setEditTime(LocalDateTime.now());

        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "回滚版本失败");

        // 2. 如果应用已经部署过，就同步部署目录
        if (StrUtil.isNotBlank(app.getDeployKey())) {
            syncVersionToDeployDir(appVersion, app.getDeployKey());

            // 3. 更新部署时间
            App deployUpdateApp = new App();
            deployUpdateApp.setId(appId);
            deployUpdateApp.setDeployedTime(LocalDateTime.now());

            boolean deployUpdateResult = this.updateById(deployUpdateApp);
            ThrowUtils.throwIf(!deployUpdateResult, ErrorCode.OPERATION_ERROR, "更新部署时间失败");
        }

        return true;
    }

    @Override
    public AppVersionDiffVO diffVersion(AppVersionDiffRequest request, HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        Long appId = request.getAppId();

        App app = this.getById(appId);
        checkAppAuth(app, loginUser);

        AppVersion oldVersion = appVersionService.getByAppIdAndVersionNo(appId, request.getOldVersionNo());
        AppVersion newVersion = appVersionService.getByAppIdAndVersionNo(appId, request.getNewVersionNo());

        if (oldVersion == null || newVersion == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        }

        String fileName = request.getFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "index.html";
        }

        File oldFile = new File(oldVersion.getCodePath(), fileName);
        File newFile = new File(newVersion.getCodePath(), fileName);

        if (!oldFile.exists() || !newFile.exists()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "对比文件不存在");
        }

        String oldCode = FileUtil.readUtf8String(oldFile);
        String newCode = FileUtil.readUtf8String(newFile);

        AppVersionDiffVO diffVO = new AppVersionDiffVO();
        diffVO.setFileName(fileName);
        diffVO.setOldCode(oldCode);
        diffVO.setNewCode(newCode);

        return diffVO;
    }

    /**
     * 将指定版本代码同步到已有部署目录
     */
    private void syncVersionToDeployDir(AppVersion appVersion, String deployKey) {
        // 用版本表里保存的真实代码路径，不要重新手拼路径
        String sourceDirPath = appVersion.getCodePath();
        File sourceDir = new File(sourceDirPath);

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "版本代码目录不存在，无法同步部署");
        }

        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        File deployDir = new File(deployDirPath);

        try {
            // 先删除旧部署目录，避免旧文件残留
            if (deployDir.exists()) {
                FileUtil.del(deployDir);
            }

            // 重新创建部署目录
            FileUtil.mkdir(deployDir);

            // 再复制目标版本代码
            FileUtil.copyContent(sourceDir, deployDir, true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "同步部署目录失败：" + e.getMessage());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId;
        try {
            appId = Long.parseLong(id.toString());
        } catch (NumberFormatException e) {
            return false;
        }
        if (appId <= 0) {
            return false;
        }

        chatHistoryService.deleteByAppId(appId);
        appVersionService.deleteByAppId(appId);
        return super.removeById(id);
    }
}
