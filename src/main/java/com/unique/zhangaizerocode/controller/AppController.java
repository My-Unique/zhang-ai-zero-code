package com.unique.zhangaizerocode.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;

import com.mybatisflex.core.query.QueryWrapper;
import com.unique.zhangaizerocode.annotation.AuthCheck;
import com.unique.zhangaizerocode.common.BaseResponse;
import com.unique.zhangaizerocode.common.DeleteRequest;
import com.unique.zhangaizerocode.common.ResultUtils;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.constant.UserConstant;
import com.unique.zhangaizerocode.core.generation.AppGenerationTaskManager;
import com.unique.zhangaizerocode.core.generation.GenerationStreamEvent;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.manager.CosManager;
import com.unique.zhangaizerocode.model.dto.app.*;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.AppGenerationStatusEnum;
import com.unique.zhangaizerocode.model.enums.AppVisibilityEnum;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.model.vo.AppAssetVO;
import com.unique.zhangaizerocode.model.vo.AppVO;
import com.unique.zhangaizerocode.service.AppService;
import com.unique.zhangaizerocode.service.AppVersionService;
import com.unique.zhangaizerocode.service.ProjectDownloadService;
import com.unique.zhangaizerocode.service.UserService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import org.springframework.core.io.FileSystemResource;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    private static final long MAX_ASSET_SIZE = 10 * 1024 * 1024L;
    private static final long MAX_TEXT_ASSET_SIZE = 256 * 1024L;
    private static final Set<String> TEXT_ASSET_SUFFIXES = Set.of("txt", "md", "json", "csv", "yml", "yaml");

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;
    @Resource
    private AppVersionService appVersionService;
    @Resource
    private AppGenerationTaskManager appGenerationTaskManager;
    @Resource
    private ProjectDownloadService projectDownloadService;
    @Resource
    private CosManager cosManager;


    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Long addId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(addId);
    }


    /**
     * 更新应用（用户只能更新自己的应用名称）
     *
     * @param appUpdateRequest 更新请求
     * @param request          请求
     * @return 更新结果
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        if (appUpdateRequest == null || appUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = appUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可更新
        if (!oldApp.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        App app = new App();
        app.setId(id);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除应用（用户只能删除自己的应用）
     *
     * @param deleteRequest 删除请求
     * @param request       请求
     * @return 删除结果
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldApp.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getAppVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类（包含用户信息）
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 分页获取当前用户创建的应用列表
     *
     * @param appQueryRequest 查询请求
     * @param request         请求
     * @return 应用列表
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppVOByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询当前用户的应用
        appQueryRequest.setUserId(loginUser.getId());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 分页获取精选应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 精选应用列表
     */
    @PostMapping("/good/list/page/vo")
    public BaseResponse<Page<AppVO>> listGoodAppVOByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 限制每页最多 20 个
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 20, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        long pageNum = appQueryRequest.getPageNum();
        // 只查询精选的应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        appQueryRequest.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest)
                .isNotNull("deployKey")
                .ne("deployKey", "");
        // 分页查询
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @return 删除结果
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = appService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 更新请求
     * @return 更新结果
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        if (appAdminUpdateRequest == null || appAdminUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = appAdminUpdateRequest.getId();
        // 判断是否存在
        App oldApp = appService.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        App app = new App();
        BeanUtil.copyProperties(appAdminUpdateRequest, app);
        if (AppConstant.GOOD_APP_PRIORITY.equals(appAdminUpdateRequest.getPriority())) {
            app.setVisibility(AppVisibilityEnum.PUBLIC.getValue());
        }
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = appService.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员分页获取应用列表
     *
     * @param appQueryRequest 查询请求
     * @return 应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppVOByPageByAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        QueryWrapper queryWrapper = appService.getQueryWrapper(appQueryRequest);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), queryWrapper);
        // 数据封装
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return ResultUtils.success(appVOPage);
    }

    /**
     * 管理员根据 id 获取应用详情
     *
     * @param id 应用 id
     * @return 应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppVOByIdByAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(appService.getAppVO(app));
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
    @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<AppAssetVO> uploadAppAsset(@RequestParam Long appId,
                                                   @RequestPart("file") MultipartFile file,
                                                   HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");
        ThrowUtils.throwIf(file.getSize() > MAX_ASSET_SIZE, ErrorCode.PARAMS_ERROR, "文件不能超过 10MB");

        User loginUser = userService.getLoginUser(request);
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限上传该应用附件");
        }

        String originalFilename = StrUtil.blankToDefault(file.getOriginalFilename(), "upload");
        String suffix = StrUtil.blankToDefault(FileUtil.getSuffix(originalFilename), "").toLowerCase();
        String contentType = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");

        AppAssetVO assetVO = new AppAssetVO();
        assetVO.setName(originalFilename);
        assetVO.setContentType(contentType);
        assetVO.setSize(file.getSize());

        if (isTextAsset(suffix, contentType)) {
            ThrowUtils.throwIf(file.getSize() > MAX_TEXT_ASSET_SIZE,
                    ErrorCode.PARAMS_ERROR, "文本附件不能超过 256KB");
            try {
                assetVO.setTextContent(new String(file.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取文本附件失败：" + e.getMessage());
            }
            return ResultUtils.success(assetVO);
        }

        ThrowUtils.throwIf(!contentType.startsWith("image/"),
                ErrorCode.PARAMS_ERROR, "当前仅支持图片和文本附件");

        File tempFile = null;
        try {
            tempFile = File.createTempFile("app-asset-", StrUtil.isBlank(suffix) ? ".tmp" : "." + suffix);
            file.transferTo(tempFile);
            String key = buildAssetCosKey(appId, originalFilename, suffix);
            String url = cosManager.uploadFile(key, tempFile);
            ThrowUtils.throwIf(StrUtil.isBlank(url), ErrorCode.SYSTEM_ERROR, "上传附件失败");
            assetVO.setUrl(url);
            return ResultUtils.success(assetVO);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传附件失败：" + e.getMessage());
        } finally {
            if (tempFile != null) {
                FileUtil.del(tempFile);
            }
        }
    }

    private boolean isTextAsset(String suffix, String contentType) {
        return TEXT_ASSET_SUFFIXES.contains(suffix)
                || contentType.startsWith("text/");
    }

    private String buildAssetCosKey(Long appId, String originalFilename, String suffix) {
        String datePath = LocalDate.now().toString().replace("-", "/");
        String safeName = originalFilename.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (StrUtil.isBlank(safeName)) {
            safeName = "asset";
        }
        if (StrUtil.isNotBlank(suffix) && !safeName.toLowerCase().endsWith("." + suffix)) {
            safeName = safeName + "." + suffix;
        }
        return String.format("app-assets/%s/%s/%s_%s",
                datePath, appId, UUID.randomUUID().toString().replace("-", ""), safeName);
    }

    @GetMapping(
            value = "/chat/gen/code",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request,
                                                       HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return Flux.defer(() -> {
                    // 参数校验
                    ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
                    ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
                    // 获取当前登录用户
                    User loginUser = userService.getLoginUser(request);
                    // 调用服务生成代码（流式）
                    return appGenerationTaskManager.start(
                            appId,
                            () -> appService.chatToGenCode(appId, message, loginUser)
                    ).flatMap(event -> {
                        if ("error".equals(event.event())) {
                            return Flux.error(new BusinessException(ErrorCode.SYSTEM_ERROR, event.data()));
                        }
                        if ("done".equals(event.event())) {
                            return Flux.empty();
                        }
                        String chunk = event.data();
                        /*
                         * generated_file_snapshot 是后端发给前端的结构化事件，
                         * 用于更新文件树和代码预览，不应混入聊天文本流。
                         *
                         * 这里把它作为 SSE 命名事件 "file_snapshot" 单独发送，
                         * 前端通过 addEventListener('file_snapshot', ...) 监听，
                         * 不会进入聊天消息的 parseSseChunk 管道。
                         */
                        if (isGeneratedFileSnapshot(chunk)) {
                            return Flux.just(ServerSentEvent.<String>builder()
                                    .event("file_snapshot")
                                    .data(chunk)
                                    .build());
                        }
                        Map<String, String> wrapper = Map.of("d", chunk);
                        String jsonData = JSONUtil.toJsonStr(wrapper);
                        return Flux.just(ServerSentEvent.<String>builder()
                                .data(jsonData)
                                .build());
                    });
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()))
                .onErrorResume(error -> {
                    /*
                     * 这个接口的响应类型是 text/event-stream。
                     * 一旦流已经开始输出，后面再抛 BusinessException，
                     * Spring 的全局异常处理器会尝试返回普通 JSON，
                     * 但当前响应头已经是 text/event-stream，
                     * 于是会出现：
                     * No converter for BaseResponse with preset Content-Type 'text/event-stream'
                     *
                     * 所以 SSE 接口内部的错误不能交给普通全局异常处理器兜底。
                     * 这里把异常包装成一条 SSE 消息返回给前端，让前端仍然按流式消息处理。
                     */
                    Map<String, String> wrapper = Map.of("e", error.getMessage());
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(jsonData)
                            .build());
                });
    }
    /**
     * 构建应用临时预览，不写入正式部署信息
     */
    @GetMapping(
            value = "/chat/gen/code/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> streamGeneratingCode(@RequestParam Long appId,
                                                             HttpServletRequest request,
                                                             HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");
        return Flux.defer(() -> {
                    ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
                    User loginUser = userService.getLoginUser(request);
                    App app = appService.getById(appId);
                    ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
                    if (!app.getUserId().equals(loginUser.getId()) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
                        throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
                    }
                    return appGenerationTaskManager.subscribe(appId)
                            .flatMap(event -> {
                                if ("error".equals(event.event())) {
                                    return Flux.error(new BusinessException(ErrorCode.SYSTEM_ERROR, event.data()));
                                }
                                if ("done".equals(event.event())) {
                                    return Flux.empty();
                                }
                                return Flux.just(event.data());
                            });
                })
                .map(chunk -> {
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()))
                .onErrorResume(error -> {
                    Map<String, String> wrapper = Map.of("e", error.getMessage());
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data(jsonData)
                            .build());
                });
    }

    private static final String GENERATED_FILE_SNAPSHOT_PREFIX = "{\"type\":\"generated_file_snapshot\"";

    private static boolean isGeneratedFileSnapshot(String chunk) {
        return chunk != null && chunk.startsWith(GENERATED_FILE_SNAPSHOT_PREFIX);
    }

    @PostMapping("/preview")
    public BaseResponse<String> previewApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.previewApp(appId, loginUser));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 下载应用代码
     *
     * @param appId    应用ID
     * @param request  请求
     * @param response 响应
     */
    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        // 1. 基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        // 2. 查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验：只有应用创建者可以下载代码
        User loginUser = userService.getLoginUser(request);
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下载该应用代码");
        }
        // 4. 只有已经部署并生成稳定浏览地址的应用才允许下载
        ThrowUtils.throwIf(StrUtil.isBlank(app.getDeployKey()),
                ErrorCode.OPERATION_ERROR, "应用尚未部署，暂不支持下载代码");
        // 5. 构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.NOT_FOUND_ERROR, "应用代码不存在，请先生成代码");
        // 7. 记录下载次数
        ThrowUtils.throwIf(!appService.incrementDownloadCount(appId),
                ErrorCode.OPERATION_ERROR, "记录下载次数失败");
        // 8. 生成下载文件名（不建议添加中文内容）
        String downloadFileName = String.valueOf(appId);
        // 9. 调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }

    /**
     * 应用下线
     */
    @PostMapping("/undeploy")
    public BaseResponse<Boolean> undeployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(appService.undeployApp(appId, loginUser));
    }

    /**
     * 用户主动停止应用生成
     */
    @PostMapping("/generation/stop")
    public BaseResponse<Boolean> stopGeneration(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        User loginUser = userService.getLoginUser(request);
        appGenerationTaskManager.stop(appId);
        return ResultUtils.success(appService.stopGeneration(appId, loginUser));
    }


}
