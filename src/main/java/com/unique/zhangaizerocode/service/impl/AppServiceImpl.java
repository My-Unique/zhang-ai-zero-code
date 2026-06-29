package com.unique.zhangaizerocode.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.unique.zhangaizerocode.ai.AiCodeGenTypeRoutingService;
import com.unique.zhangaizerocode.ai.model.message.StreamMessage;
import com.unique.zhangaizerocode.ai.model.message.StreamMessageTypeEnum;
import com.unique.zhangaizerocode.ai.model.message.ToolExecutedMessage;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.core.AiCodeGeneratorFacade;
import com.unique.zhangaizerocode.core.builder.VueProjectBuilder;
import com.unique.zhangaizerocode.core.handler.StreamHandlerExecutor;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.manager.CosManager;
import com.unique.zhangaizerocode.mapper.AppMapper;
import com.unique.zhangaizerocode.model.dto.app.*;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.AppVersion;
import com.unique.zhangaizerocode.model.entity.ChatHistory;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.AppGenerationModeEnum;
import com.unique.zhangaizerocode.model.enums.AppGenerationStatusEnum;
import com.unique.zhangaizerocode.model.enums.AppVisibilityEnum;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.model.vo.*;
import com.unique.zhangaizerocode.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/My-Unique">小建</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {
    @Resource
    private AppMapper appMapper;
    @Resource
    private UserService userService;
    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private AppVersionService appVersionService;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private CosManager cosManager;

    private final Set<Long> versionCoverRepairingApps = ConcurrentHashMap.newKeySet();


    /**
     * 构建应用展示对象，补充用户信息、状态和对话轮数。
     */
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
        normalizeAppVOState(app, appVO);
        appVO.setChatCount(countAppChatRounds(app.getId()));
        return appVO;
    }

    /**
     * 创建应用，生成应用名称并通过 AI 智能选择代码生成类型。
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");

        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());

        // 应用名称由 AI 生成，失败时会自动回退为 prompt 前 12 位
        String appName = aiCodeGeneratorFacade.generateAppName(initPrompt);
        app.setAppName(appName);

        // 使用 AI 智能选择代码生成类型，直接返回枚举类型
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        ThrowUtils.throwIf(selectedCodeGenType == null, ErrorCode.OPERATION_ERROR, "代码生成类型选择失败");
        app.setCodeGenType(selectedCodeGenType.getValue());

        // 刚创建 app 时还没有任何代码版本，所以当前版本号和部署版本号都是 0
        app.setVersionNo(0L);
        app.setDeployedVersionNo(0L);
        app.setDownloadCount(0L);
        app.setGenerationStatus(AppGenerationStatusEnum.NOT_GENERATED.getValue());
        app.setVisibility(AppVisibilityEnum.PRIVATE.getValue());

        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), selectedCodeGenType.getValue());
        return app.getId();
    }

    /**
     * 规范应用 VO 状态，兼容旧数据中缺失的可见性、生成状态和部署版本号。
     */
    private void normalizeAppVOState(App app, AppVO appVO) {
        Long versionNo = app.getVersionNo();
        if (StrUtil.isBlank(appVO.getVisibility())) {
            appVO.setVisibility(AppVisibilityEnum.PRIVATE.getValue());
        }
        if (versionNo != null && versionNo > 0) {
            String generationStatus = appVO.getGenerationStatus();
            if (StrUtil.isBlank(generationStatus)
                    || AppGenerationStatusEnum.NOT_GENERATED.getValue().equals(generationStatus)) {
                appVO.setGenerationStatus(AppGenerationStatusEnum.SUCCEEDED.getValue());
            }
        } else if (StrUtil.isBlank(appVO.getGenerationStatus())) {
            appVO.setGenerationStatus(AppGenerationStatusEnum.NOT_GENERATED.getValue());
        }
        if (StrUtil.isBlank(app.getDeployKey())) {
            appVO.setDeployedVersionNo(0L);
        } else if ((appVO.getDeployedVersionNo() == null || appVO.getDeployedVersionNo() <= 0)
                && versionNo != null && versionNo > 0) {
            appVO.setDeployedVersionNo(versionNo);
        }
    }

    /**
     * 统计应用的用户对话轮数，用于列表或详情展示。
     */
    private long countAppChatRounds(Long appId) {
        if (appId == null) {
            return 0;
        }
        return chatHistoryService.count(QueryWrapper.create()
                .eq(ChatHistory::getAppId, appId)
                .eq(ChatHistory::getMessageType, ChatHistoryMessageTypeEnum.USER.getValue()));
    }

    /**
     * 增加应用下载次数。
     */
    @Override
    public boolean incrementDownloadCount(Long appId) {
        if (appId == null || appId <= 0) {
            return false;
        }
        return appMapper.incrementDownloadCount(appId) > 0;
    }

    /**
     * 根据查询请求构建应用分页查询条件。
     */
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
        Long deployedVersionNo = appQueryRequest.getDeployedVersionNo();
        String generationStatus = appQueryRequest.getGenerationStatus();
        String visibility = appQueryRequest.getVisibility();
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
                .eq("deployedVersionNo", deployedVersionNo)
                .eq("generationStatus", generationStatus)
                .eq("visibility", visibility)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 批量构建应用展示对象，并批量补充用户信息。
     */
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

    /**
     * 处理用户对话并流式生成代码，最后校验目录、构建项目并保存新版本。
     */
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
        AppGenerationModeEnum generationMode = newVersionNo <= 1
                ? AppGenerationModeEnum.CREATE
                : AppGenerationModeEnum.MODIFY;
        log.info("AI generation request resolved, appId: {}, codeGenType: {}, newVersionNo: {}, generationMode: {}",
                appId, codeGenTypeEnum.getValue(), newVersionNo, generationMode);
        String codePath = AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + codeGenTypeEnum.getValue() + "_" + appId
                + File.separator + "v" + newVersionNo;
        updateGenerationStatus(appId, AppGenerationStatusEnum.GENERATING);

        /*
         * 生成新版本前，先准备一个完整的新版本目录。
         *
         * 为什么要做这一步：
         * 1. 用户说“把博客改成蓝色背景”这类需求时，AI 很可能只写入被修改的文件；
         * 2. 如果 v3 目录一开始是空的，AI 只写 App.vue / Home.vue，v3 就会缺少 package.json、vite.config.js 等文件；
         * 3. 缺少这些根文件后，v3 就不是一个完整 Vue 项目，npm install / npm run build 都会失败；
         * 4. 所以正确做法是：先把当前版本完整复制到新版本目录，再让 AI 覆盖需要修改的文件。
         */
        prepareNewVersionDirectory(app, appId, codePath);
        String effectiveMessage = buildCodeGenerationMessage(
                app,
                appId,
                message,
                codeGenTypeEnum
        );

        // 5. 调用 AI 生成代码
        chatHistoryService.addChatMessage(
                appId,
                message,
                ChatHistoryMessageTypeEnum.USER.getValue(),
                loginUser.getId()
        );

        AtomicInteger mutationToolExecutionCount = new AtomicInteger();
        Flux<String> codeStream = trackVueProjectToolExecution(
                aiCodeGeneratorFacade.generateAndSaveCodeStream(
                        effectiveMessage, codeGenTypeEnum, appId, newVersionNo, generationMode),
                codeGenTypeEnum,
                mutationToolExecutionCount
        );
        Flux<String> handledStream = streamHandlerExecutor
                .doExecute(codeStream, chatHistoryService, appId, newVersionNo, loginUser, codeGenTypeEnum);

        /*
         * 版本保存不能只放在 doOnComplete 里。
         *
         * 原因：
         * 1. doOnComplete 是一个旁路回调，它不会向前端输出任何“保存完成”的信号；
         * 2. 如果 SSE 客户端提前断开、Knife4j 没有持续消费完整流，doOnComplete 就不会执行；
         * 3. 结果就是代码目录可能已经创建了，但 app_version 没有保存，app.versionNo 仍然是 0；
         * 4. 用户马上点击部署时，就会一直看到“应用暂无可部署版本，请先生成代码”。
         *
         * 这里用 concatWith 把“校验代码目录 + 保存版本 + 更新 app.versionNo”
         * 串到 AI 输出流的最后一步。只有这一步成功执行后，前端才会收到“版本保存完成”的尾消息。
         * 如果这一步失败，AppController 会把异常包装成 SSE error 事件返回，问题会直接暴露给前端。
         */
        return handledStream
                .startWith("[系统] 正在分析需求并准备生成代码...\n\n")
                .concatWith(Mono.fromCallable(() -> {
                    log.info("AI 代码生成流完成，开始校验并保存版本，appId: {}, versionNo: {}, codePath: {}",
                            appId, newVersionNo, codePath);
                    validateGeneratedCodeDirectory(codeGenTypeEnum, codePath, mutationToolExecutionCount.get());
                    validateGeneratedCodeBuild(codeGenTypeEnum, codePath);
                    saveAppVersionAndUpdateCurrent(
                            appId,
                            newVersionNo,
                            message,
                            codePath,
                            loginUser.getId()
                    );
                    log.info("AI 代码生成版本保存完成，appId: {}, versionNo: {}", appId, newVersionNo);
                    return "\n\n[系统] 版本保存完成，可进行部署。\n\n";
                }))
                .doOnError(error -> {
                    updateGenerationStatus(appId, AppGenerationStatusEnum.FAILED);
                    log.error("AI 代码生成或保存版本失败，appId: {}, versionNo: {}",
                            appId, newVersionNo, error);
                })
                .doFinally(signalType -> {
                    log.info("AI 代码生成请求结束，appId: {}, versionNo: {}, signal: {}",
                            appId, newVersionNo, signalType);
                });
    }

    /**
     * 更新应用生成状态。
     */
    private void updateGenerationStatus(Long appId, AppGenerationStatusEnum status) {
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setGenerationStatus(status.getValue());
        updateApp.setEditTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        if (!updateResult) {
            log.warn("更新应用生成状态失败，appId: {}, status: {}", appId, status.getValue());
        }
    }

    /**
     * 构建代码生成提示词，Vue 项目会追加当前版本源码快照。
     */
    private String buildCodeGenerationMessage(App app,
                                              Long appId,
                                              String message,
                                              CodeGenTypeEnum codeGenTypeEnum) {
        if (!CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            return message;
        }

        Long currentVersionNo = app.getVersionNo();
        if (currentVersionNo == null || currentVersionNo <= 0) {
            return message;
        }

        AppVersion currentVersion = appVersionService.getByAppIdAndVersionNo(appId, currentVersionNo);
        if (currentVersion == null || StrUtil.isBlank(currentVersion.getCodePath())) {
            return message;
        }

        File sourceDir = new File(currentVersion.getCodePath());
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return message;
        }

        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("用户本次需求：\n")
                .append(message)
                .append("\n\n");

        appendRecentUserMessages(appId, message, contextBuilder);

        contextBuilder.append("""
                下面是当前 Vue 项目的文件列表。
                本轮不提供源码内容快照，请通过 readDir / readFile 工具了解项目结构和文件内容。
                修改任何已有文件前，必须先调用 readFile 读取该文件当前内容。
                读取成功后，再根据用户本次需求调用 modifyFile / writeFile / deleteFile。
                没有被用户要求修改的功能、结构和内容必须保留。
                
                """);

        appendVueProjectFileList(sourceDir, contextBuilder);
        return contextBuilder.toString();
    }

    /**
     * 追加最近的用户需求记录，帮助模型理解上下文但不补做历史任务。
     */
    private void appendRecentUserMessages(Long appId, String currentMessage, StringBuilder contextBuilder) {
        List<ChatHistory> recentUserMessages = chatHistoryService.list(
                QueryWrapper.create()
                        .eq(ChatHistory::getAppId, appId)
                        .eq(ChatHistory::getMessageType, ChatHistoryMessageTypeEnum.USER.getValue())
                        .orderBy(ChatHistory::getCreateTime, false)
                        .limit(5)
        );
        if (CollUtil.isEmpty(recentUserMessages)) {
            return;
        }

        Collections.reverse(recentUserMessages);
        Set<String> uniqueMessages = new LinkedHashSet<>();
        for (ChatHistory history : recentUserMessages) {
            String historyMessage = StrUtil.trim(history.getMessage());
            if (StrUtil.isBlank(historyMessage) || Objects.equals(historyMessage, currentMessage)) {
                continue;
            }
            uniqueMessages.add(historyMessage);
        }
        if (CollUtil.isEmpty(uniqueMessages)) {
            return;
        }

        contextBuilder.append("最近用户需求记录（仅用于理解上下文，不是本轮待办；本轮只执行“用户本次需求”）：\n");
        for (String historyMessage : uniqueMessages) {
            contextBuilder.append("- ").append(historyMessage).append("\n");
        }
        contextBuilder.append("\n");
    }

    /**
     * 将当前 Vue 项目的文件列表追加到 AI 输入上下文中，不包含源码内容。
     */
    private void appendVueProjectFileList(File sourceDir, StringBuilder contextBuilder) {
        List<String> relativeFilePaths = listVueProjectContextFiles(sourceDir);

        contextBuilder.append("<project_files>\n");
        for (String relativeFilePath : relativeFilePaths) {
            contextBuilder.append("- ").append(relativeFilePath).append("\n");
        }
        contextBuilder.append("</project_files>\n\n");
    }

    /**
     * 收集需要放入 Vue 上下文的文件列表，并按关键文件优先排序。
     */
    private List<String> listVueProjectContextFiles(File sourceDir) {
        Path root = sourceDir.toPath().toAbsolutePath().normalize();
        List<String> priorityFiles = List.of(
                "package.json",
                "vite.config.js",
                "index.html",
                "src/main.js",
                "src/App.vue",
                "src/router/index.js",
                "src/styles/main.css"
        );
        Map<String, Integer> priorityOrder = new HashMap<>();
        for (int index = 0; index < priorityFiles.size(); index++) {
            priorityOrder.put(priorityFiles.get(index), index);
        }

        return FileUtil.loopFiles(sourceDir).stream()
                .map(file -> root.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/'))
                .filter(this::isEditableSourceFile)
                .filter(filePath -> !isVueContextIgnoredFile(filePath))
                .distinct()
                .sorted(Comparator
                        .comparingInt((String filePath) -> priorityOrder.getOrDefault(filePath, Integer.MAX_VALUE))
                        .thenComparingInt(filePath -> filePath.startsWith("src/") ? 0 : 1)
                        .thenComparing(filePath -> filePath))
                .limit(80)
                .toList();
    }

    /**
     * 判断文件是否需要从 Vue 上下文文件列表中排除。
     */
    private boolean isVueContextIgnoredFile(String filePath) {
        String normalized = filePath.replace('\\', '/');
        String fileName = FileUtil.getName(normalized);
        return normalized.startsWith(".idea/")
                || normalized.startsWith(".vscode/")
                || normalized.startsWith("coverage/")
                || normalized.contains("/coverage/")
                || Set.of("package-lock.json", "pnpm-lock.yaml", "yarn.lock", "bun.lockb")
                .contains(fileName);
    }

    /**
     * 跟踪 Vue 项目生成过程中的文件变更工具调用次数。
     */
    private Flux<String> trackVueProjectToolExecution(Flux<String> codeStream,
                                                      CodeGenTypeEnum codeGenTypeEnum,
                                                      AtomicInteger mutationToolExecutionCount) {
        if (!CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            return codeStream;
        }
        return codeStream.doOnNext(chunk -> {
            try {
                StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
                if (StreamMessageTypeEnum.TOOL_EXECUTED.getValue().equals(streamMessage.getType())) {
                    ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                    if (isSuccessfulVueProjectMutation(toolExecutedMessage)) {
                        int count = mutationToolExecutionCount.incrementAndGet();
                        log.info("Vue 项目本轮已执行文件变更工具次数: {}", count);
                    }
                }
            } catch (Exception e) {
                log.warn("解析 Vue 项目流消息失败，chunk: {}", chunk, e);
            }
        });
    }

    /**
     * 只有真正改变项目文件的工具才允许推动新版本保存。
     */
    private boolean isVueProjectMutationTool(String toolName) {
        return Set.of("writeFile", "modifyFile", "deleteFile").contains(toolName);
    }

    /**
     * 只有成功执行的文件变更工具才计入本轮有效修改。
     */
    private boolean isSuccessfulVueProjectMutation(ToolExecutedMessage toolExecutedMessage) {
        if (toolExecutedMessage == null || !isVueProjectMutationTool(toolExecutedMessage.getName())) {
            return false;
        }
        String result = toolExecutedMessage.getResult();
        if (StrUtil.isBlank(result)) {
            return false;
        }
        String lowerResult = result.toLowerCase(Locale.ROOT);
        if (result.contains("成功") || lowerResult.contains("success")) {
            return true;
        }
        return !result.contains("失败")
                && !result.contains("错误")
                && !result.contains("警告")
                && !result.contains("未修改")
                && !result.contains("未发生变化")
                && !lowerResult.contains("failed")
                && !lowerResult.contains("error")
                && !lowerResult.contains("warning")
                && !lowerResult.contains("no change")
                && !lowerResult.contains("not found");
    }

    /**
     * 校验生成代码目录是否存在，并校验 Vue 项目是否具备最小工程结构。
     */
    private void validateGeneratedCodeDirectory(CodeGenTypeEnum codeGenTypeEnum, String codePath, int mutationToolExecutionCount) {
        File codeDir = new File(codePath);
        ThrowUtils.throwIf(!codeDir.exists() || !codeDir.isDirectory(),
                ErrorCode.SYSTEM_ERROR, "代码生成失败，输出目录不存在");

        /*
         * Vue 项目不是单个 HTML 文件，必须具备最小 Vite 工程结构。
         *
         * 之前出现过 AI 只写入 index.html 的情况：
         * - 目录存在；
         * - 版本记录也会被保存；
         * - 但部署时 npm install 找不到 package.json，最终才失败。
         *
         * 这里不要强制要求 src/router/index.js。
         * 原因是简单应用可以不使用 Vue Router，只要 src/main.js 没有 import router，
         * 缺少 router 目录并不会影响 npm install / npm run build。
         * 真正必须存在的是 Vite 构建入口和 Vue 根组件：
         * package.json、vite.config.js、index.html、src/main.js、src/App.vue。
         *
         * 所以这里在保存 app_version 前就做校验。
         * 只要核心文件缺失，就不允许把这个目录保存成一个正式版本。
         */
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            /*
             * 对 Vue 项目来说，目录存在不代表本轮真的改了代码。
             *
             * 修改历史版本时，prepareNewVersionDirectory 会先把旧版本完整复制到新版本目录；
             * 如果模型本轮没有真实调用 writeFile，目录里的文件仍然是旧版本，
             * 但只检查 package.json / src/App.vue 是否存在会误判为生成成功。
             *
             * 所以 Vue 项目必须要求本轮至少执行过一次文件变更工具。
             * 否则就不保存 app_version，也不更新 app.versionNo，避免出现“说改了但页面没变”的假版本。
             */
            ThrowUtils.throwIf(mutationToolExecutionCount <= 0,
                    ErrorCode.SYSTEM_ERROR,
                    "Vue 项目生成失败，本轮没有执行任何文件写入、修改或删除工具");

            List<String> requiredFiles = List.of(
                    "package.json",
                    "vite.config.js",
                    "index.html",
                    "src/main.js",
                    "src/App.vue"
            );

            List<String> missingFiles = requiredFiles.stream()
                    .filter(filePath -> !new File(codeDir, filePath).exists())
                    .toList();

            ThrowUtils.throwIf(CollUtil.isNotEmpty(missingFiles),
                    ErrorCode.SYSTEM_ERROR,
                    "Vue 项目生成不完整，缺少文件: " + String.join(", ", missingFiles));
        }
    }

    /**
     * 校验 Vue 项目是否可以正常构建。
     */
    private void validateGeneratedCodeBuild(CodeGenTypeEnum codeGenTypeEnum, String codePath) {
        if (codeGenTypeEnum != CodeGenTypeEnum.VUE_PROJECT) {
            return;
        }
        boolean buildSuccess = vueProjectBuilder.buildProject(codePath);
        ThrowUtils.throwIf(!buildSuccess,
                ErrorCode.SYSTEM_ERROR,
                "Vue 项目构建失败，生成代码存在语法错误或依赖问题，请重新生成");
    }

    /**
     * 创建新版本目录；有历史版本时先复制当前完整版本作为修改基线。
     */
    private void prepareNewVersionDirectory(App app, Long appId, String newCodePath) {
        /*
         * 这里负责把“新版本目录”准备成一个完整项目目录。
         *
         * 典型场景：
         * - v2 是一个完整 Vue 项目；
         * - 用户要求“改成蓝色背景”；
         * - AI 可能只调用工具写入 src/App.vue，而不会重新写 package.json、vite.config.js、index.html；
         * - 如果不先复制 v2，新生成的 v3 就只有少数几个文件，无法 npm install / npm run build。
         */
        File targetDir = new File(newCodePath);

        /*
         * 如果目标目录已经存在，通常说明上一次同版本生成失败，留下了半成品目录。
         * 这里先删掉，避免旧的空目录或残缺文件影响本次校验。
         */
        if (targetDir.exists()) {
            FileUtil.del(targetDir);
        }

        Long currentVersionNo = app.getVersionNo();
        /*
         * 当前应用还没有任何历史版本时，说明这是第一次生成。
         * 第一次生成没有旧版本可复制，也不应该提前创建空目录。
         *
         * 原因：
         * 1. 真正的项目目录应该由 FileWriteTool 在第一次写文件时创建；
         * 2. 如果这里提前创建空 v1，后面工具没执行时，文件树会显示一个空 v1；
         * 3. 这会误导排查，以为“生成了目录但文件丢了”；
         * 4. 实际正确状态应该是：工具没执行，就没有项目目录，最后校验直接失败。
         */
        if (currentVersionNo == null || currentVersionNo <= 0) {
            return;
        }

        FileUtil.mkdir(targetDir);

        /*
         * 从 app 当前版本号找到当前版本记录。
         * 注意这里不使用 maxVersionNo，因为 maxVersionNo 表示数据库里最大的版本号；
         * app.versionNo 才表示用户当前正在使用、部署、回滚后的真实当前版本。
         */
        AppVersion currentVersion = appVersionService.getByAppIdAndVersionNo(appId, currentVersionNo);
        ThrowUtils.throwIf(currentVersion == null, ErrorCode.NOT_FOUND_ERROR, "当前版本记录不存在");

        String sourceCodePath = currentVersion.getCodePath();
        ThrowUtils.throwIf(StrUtil.isBlank(sourceCodePath), ErrorCode.SYSTEM_ERROR, "当前版本代码路径为空");

        File sourceDir = new File(sourceCodePath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(),
                ErrorCode.SYSTEM_ERROR, "当前版本代码目录不存在，无法创建新版本");

        /*
         * 把当前完整版本复制到新版本目录。
         * 后续 FileWriteTool 会继续写入同一个 newCodePath，对需要修改的文件执行覆盖。
         * 这样即使 AI 只输出改动文件，新版本目录也仍然是完整可运行项目。
         */
        FileUtil.copyContent(sourceDir, targetDir, true);
    }

    /**
     * 保存应用版本记录，并更新应用当前版本号。
     */
    private void saveAppVersionAndUpdateCurrent(Long appId, Long versionNo, String userMessage,
                                                String codePath, Long userId) {
        AppVersion appVersion = new AppVersion();
        appVersion.setAppId(appId);
        appVersion.setVersionNo(versionNo);
        appVersion.setUserMessage(userMessage);
        appVersion.setCodePath(codePath);
        appVersion.setCreateUser(userId);
        appVersion.setIsDelete(0);

        boolean saveResult = appVersionService.save(appVersion);
        ThrowUtils.throwIf(!saveResult, ErrorCode.OPERATION_ERROR, "保存应用版本失败");

        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setCurrentVersionId(appVersion.getId());
        updateApp.setVersionNo(versionNo);
        updateApp.setGenerationStatus(AppGenerationStatusEnum.SUCCEEDED.getValue());
        updateApp.setEditTime(LocalDateTime.now());

        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用当前版本失败");
    }


    /**
     * 生成当前版本的临时预览地址，并异步更新当前版本封面。
     */
    @Override
    public String previewApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        checkAppAuth(app, loginUser);

        Long versionNo = app.getVersionNo();
        ThrowUtils.throwIf(versionNo == null || versionNo <= 0,
                ErrorCode.OPERATION_ERROR, "应用暂无可预览版本，请先生成代码");

        AppVersion appVersion = appVersionService.getByAppIdAndVersionNo(appId, versionNo);
        ThrowUtils.throwIf(appVersion == null, ErrorCode.NOT_FOUND_ERROR, "当前版本记录不存在");

        String previewKey = AppConstant.PREVIEW_KEY_PREFIX + appId;
        syncVersionToDeployDir(appVersion, previewKey, app.getCodeGenType());
        String previewUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, previewKey);
        generateCurrentPreviewScreenshotAsync(appId, previewUrl, versionNo);
        return previewUrl;
    }

    /**
     * 部署应用当前版本，生成正式访问地址并异步更新部署封面。
     */
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

        // 复制当前版本到部署目录；Vue 项目会先构建，再复制 dist 目录
        syncVersionToDeployDir(appVersion, deployKey, app.getCodeGenType());

        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        updateApp.setDeployedVersionNo(versionNo);

        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");

        // 同步当前对象，避免同一请求链路后续继续读取部署前的旧状态。
        app.setDeployKey(deployKey);
        app.setDeployedTime(updateApp.getDeployedTime());
        app.setDeployedVersionNo(versionNo);

        // 10. 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11. 异步生成截图并更新应用封面
        generateAppScreenshotAsync(appId, appDeployUrl, deployKey, versionNo);
        return appDeployUrl;

    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl, String deployKey, Long deployedVersionNo) {
        // 使用虚拟线程异步执行
        Thread.startVirtualThread(() -> {
            try {
                // 调用截图服务生成截图并上传
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("应用截图生成结果为空，跳过更新封面，appId: {}, url: {}", appId, appUrl);
                    return;
                }
                // 只更新仍指向本次部署的应用，避免旧截图任务覆盖新部署封面
                App oldApp = this.getById(appId);
                String oldCover = oldApp == null ? null : oldApp.getCover();
                boolean updated = UpdateChain.of(getMapper())
                        .set("cover", screenshotUrl, true)
                        .eq("id", appId)
                        .eq("deployKey", deployKey)
                        .eq("deployedVersionNo", deployedVersionNo)
                        .update();
                if (!updated) {
                    log.warn("应用部署状态已变化，跳过更新截图封面，appId: {}, deployKey: {}, versionNo: {}",
                            appId, deployKey, deployedVersionNo);
                } else {
                    updateVersionCover(appId, deployedVersionNo, screenshotUrl);
                    deleteAppCoverIfChanged(oldCover, screenshotUrl, appId);
                }
            } catch (Exception e) {
                log.warn("异步生成应用截图失败，不影响部署结果，appId: {}, url: {}", appId, appUrl, e);
            }
        });
    }


    /**
     * 异步生成当前预览地址的截图，并只在版本未变化时更新封面。
     */
    private void generateCurrentPreviewScreenshotAsync(Long appId, String previewUrl, Long versionNo) {
        Thread.startVirtualThread(() -> {
            try {
                App app = this.getById(appId);
                if (app == null || !Objects.equals(app.getVersionNo(), versionNo)) {
                    return;
                }
                String screenshotUrl = screenshotService.generateAndUploadScreenshot(previewUrl);
                if (StrUtil.isBlank(screenshotUrl)) {
                    log.warn("预览封面生成结果为空，跳过更新封面，appId: {}, url: {}", appId, previewUrl);
                    return;
                }
                App latestApp = this.getById(appId);
                if (latestApp == null || !Objects.equals(latestApp.getVersionNo(), versionNo)) {
                    deleteAppCover(screenshotUrl, appId);
                    return;
                }
                String oldCover = latestApp.getCover();
                boolean updated = UpdateChain.of(getMapper())
                        .set("cover", screenshotUrl, true)
                        .eq("id", appId)
                        .eq("versionNo", versionNo)
                        .update();
                if (!updated) {
                    deleteAppCover(screenshotUrl, appId);
                    log.warn("预览封面更新失败，appId: {}", appId);
                } else {
                    updateVersionCover(appId, versionNo, screenshotUrl);
                    deleteAppCoverIfChanged(oldCover, screenshotUrl, appId);
                }
            } catch (Exception e) {
                log.warn("异步生成预览封面失败，不影响预览结果，appId: {}, url: {}", appId, previewUrl, e);
            }
        });
    }

    /**
     * 更新指定版本的预览截图，供版本历史缩略图直接复用。
     */
    private void updateVersionCover(Long appId, Long versionNo, String coverUrl) {
        if (appId == null || versionNo == null || StrUtil.isBlank(coverUrl)) {
            return;
        }
        AppVersion version = appVersionService.getByAppIdAndVersionNo(appId, versionNo);
        if (version == null) {
            return;
        }
        AppVersion updateVersion = new AppVersion();
        updateVersion.setId(version.getId());
        updateVersion.setCover(coverUrl);
        boolean updated = appVersionService.updateById(updateVersion);
        if (!updated) {
            log.warn("版本缩略图更新失败，appId: {}, versionNo: {}", appId, versionNo);
        }
    }

    /**
     * 后台补齐旧版本缺失的预览截图；已有 cover 的版本不会重复截图。
     */
    private void repairMissingVersionCoversAsync(App app, List<AppVersion> versionList) {
        if (app == null || CollUtil.isEmpty(versionList)) {
            return;
        }
        List<AppVersion> missingVersions = versionList.stream()
                .filter(version -> StrUtil.isBlank(version.getCover()))
                .limit(20)
                .toList();
        if (missingVersions.isEmpty() || !versionCoverRepairingApps.add(app.getId())) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                for (AppVersion version : missingVersions) {
                    try {
                        AppVersion latestVersion = appVersionService.getByAppIdAndVersionNo(
                                app.getId(), version.getVersionNo());
                        if (latestVersion == null || StrUtil.isNotBlank(latestVersion.getCover())) {
                            continue;
                        }
                        String previewKey = AppConstant.PREVIEW_KEY_PREFIX + app.getId() + "_v"
                                + latestVersion.getVersionNo();
                        syncVersionToDeployDir(latestVersion, previewKey, app.getCodeGenType());
                        String previewUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, previewKey);
                        String screenshotUrl = screenshotService.generateAndUploadScreenshot(previewUrl);
                        if (StrUtil.isBlank(screenshotUrl)) {
                            continue;
                        }
                        updateVersionCover(app.getId(), latestVersion.getVersionNo(), screenshotUrl);
                        if (Objects.equals(app.getVersionNo(), latestVersion.getVersionNo())) {
                            UpdateChain.of(getMapper())
                                    .set("cover", screenshotUrl, true)
                                    .eq("id", app.getId())
                                    .eq("versionNo", latestVersion.getVersionNo())
                                    .update();
                        }
                    } catch (Exception e) {
                        log.warn("补齐版本缩略图失败，appId: {}, versionNo: {}",
                                app.getId(), version.getVersionNo(), e);
                    }
                }
            } finally {
                versionCoverRepairingApps.remove(app.getId());
            }
        });
    }

    /**
     * 定时修复已经部署但缺少封面的应用。
     */
    @Scheduled(cron = "0 0/30 * * * ?")
    public void repairMissingCoverApps() {
        List<App> deployedApps = this.list(QueryWrapper.create()
                .isNotNull("deployKey")
                .ne("deployKey", ""));
        deployedApps.stream()
                .filter(app -> StrUtil.isBlank(app.getCover()))
                .filter(app -> app.getDeployedVersionNo() != null && app.getDeployedVersionNo() > 0)
                .limit(20)
                .forEach(app -> {
                    String appUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, app.getDeployKey());
                    generateAppScreenshotAsync(app.getId(), appUrl, app.getDeployKey(), app.getDeployedVersionNo());
                });
    }

    /**
     * 下线应用并清理正式部署目录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean undeployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");

        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        if (!app.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限下线该应用");
        }

        deleteDeployDir(app.getDeployKey());

        boolean updateResult = UpdateChain.of(getMapper())
                .set("deployKey", null, true)
                .set("deployedTime", null, true)
                .set("deployedVersionNo", 0L, true)
                .set("editTime", LocalDateTime.now(), true)
                .eq("id", appId)
                .update();
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用下线信息失败");
        return true;
    }

    /**
     * 停止应用生成，将应用生成状态标记为失败。
     */
    @Override
    public Boolean stopGeneration(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        checkAppAuth(app, loginUser);
        updateGenerationStatus(appId, AppGenerationStatusEnum.FAILED);
        return true;
    }


    /**
     * 校验当前用户是否有权限操作应用。
     */
    private void checkAppAuth(App app, User loginUser) {
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        }

        if (!Objects.equals(app.getUserId(), loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 查询应用的版本历史列表。
     */
    @Override
    public List<AppVersionVO> listAppVersions(Long appId, HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);

        App app = this.getById(appId);
        checkAppAuth(app, loginUser);

        List<AppVersion> versionList = appVersionService.listByAppId(appId);
        syncCurrentVersionCoverFromApp(app, versionList);
        repairMissingVersionCoversAsync(app, versionList);

        return versionList.stream().map(this::toAppVersionVO).toList();
    }

    /**
     * 当前应用封面已经生成时，立即复用到当前版本缩略图。
     */
    private void syncCurrentVersionCoverFromApp(App app, List<AppVersion> versionList) {
        if (app == null || CollUtil.isEmpty(versionList) || StrUtil.isBlank(app.getCover())) {
            return;
        }
        versionList.stream()
                .filter(version -> Objects.equals(version.getVersionNo(), app.getVersionNo()))
                .filter(version -> StrUtil.isBlank(version.getCover()))
                .findFirst()
                .ifPresent(version -> {
                    version.setCover(app.getCover());
                    updateVersionCover(app.getId(), version.getVersionNo(), app.getCover());
                });
    }

    /**
     * 回滚应用到指定版本；如果应用已部署，同时同步部署目录。
     */
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
            syncVersionToDeployDir(appVersion, app.getDeployKey(), app.getCodeGenType());

            // 3. 更新部署时间
            App deployUpdateApp = new App();
            deployUpdateApp.setId(appId);
            deployUpdateApp.setDeployedTime(LocalDateTime.now());
            deployUpdateApp.setDeployedVersionNo(appVersion.getVersionNo());

            boolean deployUpdateResult = this.updateById(deployUpdateApp);
            ThrowUtils.throwIf(!deployUpdateResult, ErrorCode.OPERATION_ERROR, "更新部署时间失败");
        }

        return true;
    }

    /**
     * 对比两个版本中指定文件的源码内容。
     */
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

        File oldFile = resolveVersionFile(oldVersion, fileName);
        File newFile = resolveVersionFile(newVersion, fileName);

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
     * 列出指定版本中允许在线查看或编辑的源码文件。
     */
    @Override
    public List<String> listVersionFiles(Long appId, Long versionNo, HttpServletRequest request) {
        AppVersion version = getAuthorizedVersion(appId, versionNo, request);
        Path root = Path.of(version.getCodePath()).toAbsolutePath().normalize();

        return FileUtil.loopFiles(root.toFile()).stream()
                .map(file -> root.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/'))
                .filter(this::isEditableSourceFile)
                .sorted()
                .limit(500)
                .toList();
    }

    /**
     * 读取指定版本中的单个源码文件内容。
     */
    @Override
    public AppVersionFileVO readVersionFile(AppVersionFileRequest request, HttpServletRequest httpServletRequest) {
        validateVersionFileRequest(request, false);
        AppVersion version = getAuthorizedVersion(request.getAppId(), request.getVersionNo(), httpServletRequest);
        File targetFile = resolveVersionFile(version, request.getFilePath());
        ThrowUtils.throwIf(!targetFile.exists() || !targetFile.isFile(), ErrorCode.NOT_FOUND_ERROR, "代码文件不存在");
        ThrowUtils.throwIf(targetFile.length() > 2 * 1024 * 1024, ErrorCode.PARAMS_ERROR, "代码文件过大，无法在线编辑");

        AppVersionFileVO vo = new AppVersionFileVO();
        vo.setPath(request.getFilePath());
        vo.setContent(FileUtil.readUtf8String(targetFile));
        return vo;
    }

    /**
     * 保存手动编辑后的文件内容，并生成一个新的应用版本。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppVersionVO saveVersionFile(AppVersionFileRequest request, HttpServletRequest httpServletRequest) {
        validateVersionFileRequest(request, true);
        User loginUser = userService.getLoginUser(httpServletRequest);
        App app = this.getById(request.getAppId());
        checkAppAuth(app, loginUser);

        AppVersion sourceVersion = appVersionService.getByAppIdAndVersionNo(request.getAppId(), request.getVersionNo());
        ThrowUtils.throwIf(sourceVersion == null, ErrorCode.NOT_FOUND_ERROR, "源版本不存在");

        Long newVersionNo = appVersionService.getMaxVersionNo(request.getAppId()) + 1;
        String newCodePath = AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + app.getCodeGenType() + "_" + request.getAppId()
                + File.separator + "v" + newVersionNo;
        File newCodeDir = new File(newCodePath);
        FileUtil.mkdir(newCodeDir);
        FileUtil.copyContent(new File(sourceVersion.getCodePath()), newCodeDir, true);

        AppVersion temporaryVersion = new AppVersion();
        temporaryVersion.setCodePath(newCodePath);
        File targetFile = resolveVersionFile(temporaryVersion, request.getFilePath());
        FileUtil.mkParentDirs(targetFile);
        FileUtil.writeUtf8String(request.getContent(), targetFile);

        saveAppVersionAndUpdateCurrent(
                request.getAppId(),
                newVersionNo,
                "[手动编辑] " + request.getFilePath(),
                newCodePath,
                loginUser.getId()
        );

        AppVersion savedVersion = appVersionService.getByAppIdAndVersionNo(request.getAppId(), newVersionNo);
        syncVersionToDeployDir(savedVersion, AppConstant.PREVIEW_KEY_PREFIX + request.getAppId(), app.getCodeGenType());
        return toAppVersionVO(savedVersion);
    }

    /**
     * 生成指定历史版本的临时预览地址，不改变应用当前版本。
     */
    @Override
    public String previewVersion(Long appId, Long versionNo, HttpServletRequest request) {
        AppVersion version = getAuthorizedVersion(appId, versionNo, request);
        App app = this.getById(appId);
        String previewKey = AppConstant.PREVIEW_KEY_PREFIX + appId + "_v" + versionNo;
        syncVersionToDeployDir(version, previewKey, app.getCodeGenType());
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, previewKey);
    }

    /**
     * 获取指定版本并校验当前用户访问权限。
     */
    private AppVersion getAuthorizedVersion(Long appId, Long versionNo, HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(versionNo == null || versionNo <= 0, ErrorCode.PARAMS_ERROR, "版本号不能为空");
        User loginUser = userService.getLoginUser(request);
        App app = this.getById(appId);
        checkAppAuth(app, loginUser);
        AppVersion version = appVersionService.getByAppIdAndVersionNo(appId, versionNo);
        ThrowUtils.throwIf(version == null, ErrorCode.NOT_FOUND_ERROR, "版本不存在");
        return version;
    }

    /**
     * 根据版本代码目录解析文件路径，并防止路径穿越。
     */
    private File resolveVersionFile(AppVersion version, String filePath) {
        Path root = Path.of(version.getCodePath()).toAbsolutePath().normalize();
        Path target = root.resolve(filePath).normalize();
        ThrowUtils.throwIf(!target.startsWith(root), ErrorCode.PARAMS_ERROR, "非法文件路径");
        ThrowUtils.throwIf(!isEditableSourceFile(root.relativize(target).toString().replace('\\', '/')),
                ErrorCode.PARAMS_ERROR, "不支持编辑该文件");
        return target.toFile();
    }

    /**
     * 判断文件是否属于允许在线查看或编辑的源码文件。
     */
    private boolean isEditableSourceFile(String filePath) {
        String normalized = filePath.replace('\\', '/');
        if (normalized.startsWith("node_modules/")
                || normalized.startsWith("dist/")
                || normalized.startsWith(".git/")
                || normalized.contains("/node_modules/")
                || normalized.contains("/dist/")) {
            return false;
        }
        String suffix = FileUtil.getSuffix(normalized).toLowerCase();
        return Set.of("html", "css", "js", "jsx", "ts", "tsx", "vue", "json", "md", "txt", "yml", "yaml")
                .contains(suffix);
    }

    /**
     * 校验版本文件读写请求参数。
     */
    private void validateVersionFileRequest(AppVersionFileRequest request, boolean requireContent) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(request.getFilePath()), ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        if (requireContent) {
            ThrowUtils.throwIf(request.getContent() == null, ErrorCode.PARAMS_ERROR, "文件内容不能为空");
            ThrowUtils.throwIf(request.getContent().length() > 2 * 1024 * 1024,
                    ErrorCode.PARAMS_ERROR, "文件内容过大，无法在线保存");
        }
    }

    /**
     * 将应用版本实体转换为展示对象。
     */
    private AppVersionVO toAppVersionVO(AppVersion version) {
        AppVersionVO vo = new AppVersionVO();
        BeanUtil.copyProperties(version, vo);
        return vo;
    }

    /**
     * 将指定版本代码同步到已有部署目录
     */
    private void syncVersionToDeployDir(AppVersion appVersion, String deployKey, String codeGenType) {
        // 用版本表里保存的真实代码路径，不要重新手拼路径
        String sourceDirPath = appVersion.getCodePath();
        File sourceDir = new File(sourceDirPath);

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }

        /*
         * Vue 工程不能直接复制源码目录部署。
         *
         * 原因：
         * 1. Vue 项目的源码目录里有 package.json、src、vite.config.js 等开发期文件；
         * 2. 浏览器真正能直接访问的是 npm run build 后生成的 dist 目录；
         * 3. 所以部署 Vue 项目时必须先在版本目录执行 npm install / npm run build；
         * 4. 构建成功后，把 sourceDir 从源码目录切换成 dist 目录，后面统一复制 sourceDir 到部署目录。
         */
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请检查代码和依赖");

            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(),
                    ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");

            sourceDir = distDir;
            log.info("Vue 项目构建成功，将部署 dist 目录: {}", distDir.getAbsolutePath());
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

    /**
     * 删除应用，同时清理代码目录、预览目录、部署目录、封面和版本记录。
     */
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

        App app = this.getById(appId);
        cleanupAppFiles(app, appId);
        chatHistoryService.deleteByAppId(appId);
        appVersionService.deleteByAppId(appId);
        return super.removeById(id);
    }

    /**
     * 清理应用关联的本地代码、部署预览目录和 COS 封面。
     */
    private void cleanupAppFiles(App app, Long appId) {
        try {
            if (app != null && StrUtil.isNotBlank(app.getCodeGenType())) {
                FileUtil.del(new File(AppConstant.CODE_OUTPUT_ROOT_DIR,
                        app.getCodeGenType() + "_" + appId));
            }
            if (app != null) {
                deleteDeployDir(app.getDeployKey());
            }
            deleteAllAppCovers(app, appId);
            deleteDeployDir(AppConstant.PREVIEW_KEY_PREFIX + appId);
            File deployRoot = new File(AppConstant.CODE_DEPLOY_ROOT_DIR);
            File[] versionPreviewDirs = deployRoot.listFiles(file ->
                    file.isDirectory() && file.getName().startsWith(AppConstant.PREVIEW_KEY_PREFIX + appId + "_v"));
            if (versionPreviewDirs != null) {
                for (File previewDir : versionPreviewDirs) {
                    FileUtil.del(previewDir);
                }
            }
        } catch (Exception e) {
            log.warn("清理应用文件失败，appId: {}", appId, e);
        }
    }

    /**
     * 当封面 URL 已变化时删除旧封面文件。
     */
    private void deleteAppCoverIfChanged(String oldCover, String newCover, Long appId) {
        if (StrUtil.isBlank(oldCover) || Objects.equals(oldCover, newCover)) {
            return;
        }
        long versionUseCount = appVersionService.count(QueryWrapper.create()
                .eq("appId", appId)
                .eq("cover", oldCover)
                .eq("isDelete", 0));
        if (versionUseCount > 0) {
            return;
        }
        deleteAppCover(oldCover, appId);
    }

    /**
     * 删除应用封面和所有历史版本缩略图。
     */
    private void deleteAllAppCovers(App app, Long appId) {
        if (appId == null || appId <= 0) {
            return;
        }
        Set<String> coverUrls = appVersionService.listByAppId(appId).stream()
                .map(AppVersion::getCover)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (app != null && StrUtil.isNotBlank(app.getCover())) {
            coverUrls.add(app.getCover());
        }
        for (String coverUrl : coverUrls) {
            deleteAppCover(coverUrl, appId);
        }
    }

    /**
     * 删除 COS 中的应用封面文件。
     */
    private void deleteAppCover(String coverUrl, Long appId) {
        if (StrUtil.isBlank(coverUrl)) {
            return;
        }
        try {
            cosManager.deleteFileByUrl(coverUrl);
        } catch (Exception e) {
            log.warn("删除应用封面 COS 文件失败，appId: {}, cover: {}", appId, coverUrl, e);
        }
    }

    /**
     * 删除本地部署目录或预览目录。
     */
    private void deleteDeployDir(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return;
        }
        FileUtil.del(new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
    }
}
