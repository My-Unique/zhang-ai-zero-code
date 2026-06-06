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
import com.unique.zhangaizerocode.ai.model.message.StreamMessage;
import com.unique.zhangaizerocode.ai.model.message.StreamMessageTypeEnum;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.core.AiCodeGeneratorFacade;
import com.unique.zhangaizerocode.core.builder.VueProjectBuilder;
import com.unique.zhangaizerocode.core.handler.StreamHandlerExecutor;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.exception.ThrowUtils;
import com.unique.zhangaizerocode.mapper.AppMapper;
import com.unique.zhangaizerocode.model.dto.app.AppQueryRequest;
import com.unique.zhangaizerocode.model.dto.app.AppRollbackVersionRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionDiffRequest;
import com.unique.zhangaizerocode.model.dto.app.AppVersionFileRequest;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.AppVersion;
import com.unique.zhangaizerocode.model.entity.ChatHistory;
import com.unique.zhangaizerocode.model.entity.User;
import com.unique.zhangaizerocode.model.enums.AppGenerationStatusEnum;
import com.unique.zhangaizerocode.model.enums.AppVisibilityEnum;
import com.unique.zhangaizerocode.model.enums.ChatHistoryMessageTypeEnum;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.model.vo.AppVO;
import com.unique.zhangaizerocode.model.vo.AppVersionDiffVO;
import com.unique.zhangaizerocode.model.vo.AppVersionFileVO;
import com.unique.zhangaizerocode.model.vo.AppVersionVO;
import com.unique.zhangaizerocode.model.vo.UserVO;
import com.unique.zhangaizerocode.service.AppService;
import com.unique.zhangaizerocode.service.AppVersionService;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import com.unique.zhangaizerocode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
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
    private UserService userService;
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

    private long countAppChatRounds(Long appId) {
        if (appId == null) {
            return 0;
        }
        return chatHistoryService.count(QueryWrapper.create()
                .eq(ChatHistory::getAppId, appId)
                .eq(ChatHistory::getMessageType, ChatHistoryMessageTypeEnum.USER.getValue()));
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

        AtomicInteger toolExecutionCount = new AtomicInteger();
        Flux<String> codeStream = trackVueProjectToolExecution(
                aiCodeGeneratorFacade.generateAndSaveCodeStream(effectiveMessage, codeGenTypeEnum, appId, newVersionNo),
                codeGenTypeEnum,
                toolExecutionCount
        );
        Flux<String> handledStream = streamHandlerExecutor
                .doExecute(codeStream, chatHistoryService, appId, loginUser, codeGenTypeEnum);

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
                .concatWith(Mono.fromCallable(() -> {
                    log.info("AI 代码生成流完成，开始校验并保存版本，appId: {}, versionNo: {}, codePath: {}",
                            appId, newVersionNo, codePath);
                    validateGeneratedCodeDirectory(codeGenTypeEnum, codePath, toolExecutionCount.get());
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
                下面是当前 Vue 项目的关键源码快照。
                当前源码快照是本次修改的事实基线。
                请基于这些源码进行修改，只调用 writeFile 写入本次确实需要修改的文件。
                没有被用户要求修改的功能、结构和内容必须保留。
                如果最近用户需求记录和当前源码快照不一致，以当前源码快照为准，不要补做历史需求。
                
                """);

        appendVueProjectSourceSnapshot(sourceDir, contextBuilder);
        return contextBuilder.toString();
    }

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

    private void appendVueProjectSourceSnapshot(File sourceDir, StringBuilder contextBuilder) {
        List<String> relativeFilePaths = List.of(
                "package.json",
                "vite.config.js",
                "index.html",
                "src/main.js",
                "src/App.vue",
                "src/router/index.js",
                "src/pages/Chat.vue"
        );

        int maxTotalLength = 24000;
        int maxSingleFileLength = 8000;

        contextBuilder.append("<current_project>\n");
        for (String relativeFilePath : relativeFilePaths) {
            File file = new File(sourceDir, relativeFilePath);
            if (!file.exists() || !file.isFile()) {
                continue;
            }
            String fileContent = FileUtil.readUtf8String(file);
            if (fileContent.length() > maxSingleFileLength) {
                fileContent = fileContent.substring(0, maxSingleFileLength)
                        + "\n<!-- 文件内容过长，后续内容已省略 -->";
            }

            String fileBlock = "<file path=\"" + relativeFilePath + "\">\n"
                    + fileContent
                    + "\n</file>\n\n";
            if (contextBuilder.length() + fileBlock.length() > maxTotalLength) {
                contextBuilder.append("<!-- 源码快照过长，后续文件已省略 -->\n");
                break;
            }
            contextBuilder.append(fileBlock);
        }
        contextBuilder.append("</current_project>\n");
    }

    private Flux<String> trackVueProjectToolExecution(Flux<String> codeStream,
                                                      CodeGenTypeEnum codeGenTypeEnum,
                                                      AtomicInteger toolExecutionCount) {
        if (!CodeGenTypeEnum.VUE_PROJECT.equals(codeGenTypeEnum)) {
            return codeStream;
        }
        return codeStream.doOnNext(chunk -> {
            try {
                StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
                if (StreamMessageTypeEnum.TOOL_EXECUTED.getValue().equals(streamMessage.getType())) {
                    int count = toolExecutionCount.incrementAndGet();
                    log.info("Vue 项目本轮已执行文件写入工具次数: {}", count);
                }
            } catch (Exception e) {
                log.warn("解析 Vue 项目流消息失败，chunk: {}", chunk, e);
            }
        });
    }

    private void validateGeneratedCodeDirectory(CodeGenTypeEnum codeGenTypeEnum, String codePath, int toolExecutionCount) {
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
             * 所以 Vue 项目必须要求本轮至少执行过一次文件写入工具。
             * 否则就不保存 app_version，也不更新 app.versionNo，避免出现“说改了但页面没变”的假版本。
             */
            ThrowUtils.throwIf(toolExecutionCount <= 0,
                    ErrorCode.SYSTEM_ERROR,
                    "Vue 项目生成失败，本轮没有执行任何文件写入工具");

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
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, previewKey);
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

        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }

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

    @Override
    public Boolean stopGeneration(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        App app = this.getById(appId);
        checkAppAuth(app, loginUser);
        updateGenerationStatus(appId, AppGenerationStatusEnum.FAILED);
        return true;
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

        return versionList.stream().map(this::toAppVersionVO).toList();
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

    @Override
    public String previewVersion(Long appId, Long versionNo, HttpServletRequest request) {
        AppVersion version = getAuthorizedVersion(appId, versionNo, request);
        App app = this.getById(appId);
        String previewKey = AppConstant.PREVIEW_KEY_PREFIX + appId + "_v" + versionNo;
        syncVersionToDeployDir(version, previewKey, app.getCodeGenType());
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, previewKey);
    }

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

    private File resolveVersionFile(AppVersion version, String filePath) {
        Path root = Path.of(version.getCodePath()).toAbsolutePath().normalize();
        Path target = root.resolve(filePath).normalize();
        ThrowUtils.throwIf(!target.startsWith(root), ErrorCode.PARAMS_ERROR, "非法文件路径");
        ThrowUtils.throwIf(!isEditableSourceFile(root.relativize(target).toString().replace('\\', '/')),
                ErrorCode.PARAMS_ERROR, "不支持编辑该文件");
        return target.toFile();
    }

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

    private void validateVersionFileRequest(AppVersionFileRequest request, boolean requireContent) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StrUtil.isBlank(request.getFilePath()), ErrorCode.PARAMS_ERROR, "文件路径不能为空");
        if (requireContent) {
            ThrowUtils.throwIf(request.getContent() == null, ErrorCode.PARAMS_ERROR, "文件内容不能为空");
            ThrowUtils.throwIf(request.getContent().length() > 2 * 1024 * 1024,
                    ErrorCode.PARAMS_ERROR, "文件内容过大，无法在线保存");
        }
    }

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

    private void cleanupAppFiles(App app, Long appId) {
        try {
            if (app != null && StrUtil.isNotBlank(app.getCodeGenType())) {
                FileUtil.del(new File(AppConstant.CODE_OUTPUT_ROOT_DIR,
                        app.getCodeGenType() + "_" + appId));
            }
            if (app != null) {
                deleteDeployDir(app.getDeployKey());
            }
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

    private void deleteDeployDir(String deployKey) {
        if (StrUtil.isBlank(deployKey)) {
            return;
        }
        FileUtil.del(new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey));
    }
}
