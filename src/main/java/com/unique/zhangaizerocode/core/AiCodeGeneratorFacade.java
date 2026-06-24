package com.unique.zhangaizerocode.core;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.unique.zhangaizerocode.ai.AppNameGeneratorService;
import com.unique.zhangaizerocode.ai.AiCodeGeneratorService;
import com.unique.zhangaizerocode.ai.AiCodeGeneratorServiceFactory;
import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import com.unique.zhangaizerocode.ai.model.message.AiResponseMessage;
import com.unique.zhangaizerocode.ai.model.message.ToolExecutedMessage;
import com.unique.zhangaizerocode.ai.model.message.ToolRequestMessage;
import com.unique.zhangaizerocode.core.parser.CodeParserExecutor;
import com.unique.zhangaizerocode.core.saver.CodeFileSaverExecutor;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
//定义门面 通过门面调用方法

/**
 * AI 代码生成外观类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;


    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long versionNo) {

        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        // 根据 appId 获取对应的 AI 服务实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum,versionNo);

        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId, versionNo);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId, versionNo);
            }

            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long versionNo) {
        // 根据 appId 获取对应的 AI 服务实例

        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId,codeGenTypeEnum,versionNo);

        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId, versionNo);

            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId, versionNo);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId,userMessage);
                yield processTokenStream(tokenStream);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    //通用流式代码处理 现在需要优化成 针对不同的类型进行返回处理

    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId, Long versionNo) {
        // 当流式返回生成代码完成后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        String completeCode = codeBuilder.toString();
                        Object parserResult = CodeParserExecutor.executeParser(completeCode, codeGenTypeEnum);
                        // 保存代码到文件
                        File savedDir = CodeFileSaverExecutor.executeSaver(parserResult, codeGenTypeEnum, appId, versionNo);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存代码失败：" + e.getMessage());

                    }
                });
    }
    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(index, toolExecutionRequest);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        error.printStackTrace();
                        sink.error(error);
                    })
                    .start();
        });
    }

    //
    public String generateAppName(String initPrompt) {
        AppNameGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.appNameGeneratorService();
        if (StrUtil.isBlank(initPrompt)) {
            return "我的应用";
        }

        try {
            String appName = aiCodeGeneratorService.generateAppName(initPrompt);
            return cleanAppName(appName, initPrompt);
        } catch (Exception e) {
            log.error("生成应用名称失败", e);
            return fallbackAppName(initPrompt);
        }
    }

    private String cleanAppName(String appName, String initPrompt) {
        if (StrUtil.isBlank(appName)) {
            return fallbackAppName(initPrompt);
        }

        appName = appName.trim();

        // 去掉换行、引号和常见标点
        appName = appName.replaceAll("[\\r\\n\"'“”‘’`，。！？：:；;、]", "");

        if (StrUtil.isBlank(appName)) {
            return fallbackAppName(initPrompt);
        }

        // 防止 AI 返回太长
        if (appName.length() > 12) {
            appName = appName.substring(0, 12);
        }

        return appName;
    }

    private String fallbackAppName(String initPrompt) {
        if (StrUtil.isBlank(initPrompt)) {
            return "我的应用";
        }
        return initPrompt.substring(0, Math.min(initPrompt.length(), 12));
    }


}
