package com.unique.zhangaizerocode.core;

import ch.qos.logback.classic.Logger;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.unique.zhangaizerocode.ai.AiCodeGeneratorService;
import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.core.parser.CodeParserExecutor;
import com.unique.zhangaizerocode.core.saver.CodeFileSaverExecutor;
import com.unique.zhangaizerocode.core.saver.CodeFileSaverTemplate;
import com.unique.zhangaizerocode.exception.BusinessException;
import com.unique.zhangaizerocode.exception.ErrorCode;
import com.unique.zhangaizerocode.model.entity.App;
import com.unique.zhangaizerocode.model.entity.AppVersion;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.message.Message;
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
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId,Long versionNo) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId,versionNo);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId,versionNo);
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
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId,Long versionNo) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId,versionNo);

            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId,versionNo);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }


    //通用流式代码处理
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenTypeEnum, Long appId,Long versionNo ) {
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
                        File savedDir = CodeFileSaverExecutor.executeSaver(parserResult, codeGenTypeEnum, appId,versionNo);
                        log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存代码失败：" + e.getMessage());

                    }
                });
    }
    //
    public String generateAppName(String initPrompt) {
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
