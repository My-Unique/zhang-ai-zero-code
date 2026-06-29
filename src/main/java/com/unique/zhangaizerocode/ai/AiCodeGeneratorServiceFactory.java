package com.unique.zhangaizerocode.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.unique.zhangaizerocode.ai.tools.*;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.AppGenerationModeEnum;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import com.unique.zhangaizerocode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

//实现ai方法

//工厂类 将 AiCodeGeneratorService 注册进 Spring 容器了
//用工厂类创建实例
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    /**
     * 注入ChatModel Bean
     * ChatModel用于与AI模型进行交互
     */
    @Resource
    private ChatModel chatModel;
    @Resource
    private StreamingChatModel openAiStreamingChatModel;
    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamingChatModel reasoningStreamingChatModel;
    @Resource
    private ToolManager toolManager;
    /**
     * AI 服务实例缓存
     * 缓存策略：
     * - 最大缓存 1000 个实例
     * - 写入后 30 分钟过期
     * - 访问后 10 分钟过期
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) ->
                    log.debug("AI 服务实例被移除，缓存键: {}, 原因: {}", key, cause)
            )
            .build();

    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }

    public AppNameGeneratorService appNameGeneratorService() {
        return AiServices.builder(AppNameGeneratorService.class)
                .chatModel(chatModel)
                .build();
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        // 使用serviceCache获取服务实例，如果缓存中没有则创建新的实例
        // 使用lambda表达式作为缓存未命中时的回调函数
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML, 1L);

    }
    /**
     * 获取AI代码生成器服务实例
     * @param appId 应用ID，用于标识不同的应用
     * @param codeGenType 代码生成类型，指定生成代码的类型
     * @return 返回一个AiCodeGeneratorService实例，用于生成代码
     */
    /**
     * 构建缓存键
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType, Long versionNo,
                                 AppGenerationModeEnum generationMode) {
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenType)) {
            return appId + "_" + codeGenType.getValue() + "_" + versionNo + "_" + generationMode.name().toLowerCase();
        }
        return appId + "_" + codeGenType.getValue();
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Long versionNo) {
        return getAiCodeGeneratorService(appId, codeGenType, versionNo, AppGenerationModeEnum.MODIFY);
    }

    public AiCodeGeneratorService getAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Long versionNo,
                                                            AppGenerationModeEnum generationMode) {
        // 使用serviceCache获取服务实例，如果缓存中没有则创建新的实例
        // 使用lambda表达式作为缓存未命中时的回调函数
        log.info("为 appId: {}, versionNo: {}, generationMode: {} 创建新的 AI 服务实例",
                appId, versionNo, generationMode);
        if (versionNo == null || versionNo <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "版本号不能为空");
        }
        if (generationMode == null) {
            generationMode = AppGenerationModeEnum.MODIFY;
        }

        AppGenerationModeEnum effectiveMode = generationMode;
        String cacheKey = buildCacheKey(appId, codeGenType, versionNo, effectiveMode);
        return serviceCache.get(cacheKey, key -> createAiCodeGeneratorService(appId, codeGenType, versionNo, effectiveMode));

    }

    /**
     * 创建新的ai服务实例
     **/
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenType, Long versionNo,
                                                                AppGenerationModeEnum generationMode) {
        log.info("为 appId: {}, generationMode: {} 创建新的 AI 服务实例", appId, generationMode);
        // 根据 appId 构建独立的对话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();

        /*
         * Vue 项目生成不再把历史 AI 回复加载进 ChatMemory。
         *
         * 原因：
         * 1. chat_history 主要用于前端展示，里面可能包含旧代码块、工具调用展示文本、
         *    “已修改完成”这类自然语言结果；
         * 2. 这些内容再次塞给模型时，模型容易复读历史文本，而不是真实调用 writeFile；
         * 3. Vue 项目真正可靠的上下文不是历史回复，而是当前版本目录里的源码；
         * 4. AppServiceImpl 会在每次 Vue 生成前，把当前版本关键源码拼进本次 user message。
         *
         * 所以 Vue 项目这里保持干净 ChatMemory，只保留系统提示词、本次增强后的用户需求和真实工具。
         */
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenType)) {
            chatMemory.clear();
            log.info("Vue 项目生成不加载历史 AI 对话，改用当前源码快照作为上下文，appId: {}, versionNo: {}",
                    appId, versionNo);
        } else {
            chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        }
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                log.info("AI tools enabled for codeGenType: {}, appId: {}, versionNo: {}, generationMode: {}, tools: {}",
                        codeGenType.getValue(), appId, versionNo, generationMode,
                        toolManager.getToolNames(versionNo, generationMode));
                yield AiServices.builder(AiCodeGeneratorService.class)
                        // 非流式返回用 chatModel
//                        .chatModel(chatModel)
                        .streamingChatModel(reasoningStreamingChatModel)
                        // 因为有MemoryId 所以必须配置这个
                        .chatMemoryProvider(memoryId -> chatMemory)
                        .tools((Object[]) toolManager.getTools(versionNo, generationMode))

                        //幻觉工具名称策略
                        .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                                toolExecutionRequest, "Error:There is no tool named " + toolExecutionRequest.name()
                        ))
                        .build();
            }

            case HTML, MULTI_FILE -> {
                log.info("AI tools disabled for codeGenType: {}, appId: {}, versionNo: {}. This path uses code-block streaming and saver parsing.",
                        codeGenType.getValue(), appId, versionNo);
                //有{}的话 就是一个代码块 需要有返回值才行
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(openAiStreamingChatModel)
                        .chatMemory(chatMemory)
                        .build();
            }


            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型: " + codeGenType);
        };

    }


}



