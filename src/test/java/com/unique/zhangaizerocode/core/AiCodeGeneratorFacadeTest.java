package com.unique.zhangaizerocode.core;

import cn.hutool.core.io.FileUtil;
import com.unique.zhangaizerocode.constant.AppConstant;
import com.unique.zhangaizerocode.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("任务记录网站", CodeGenTypeEnum.MULTI_FILE, 1L,1L);
        Assertions.assertNotNull(file);
    }


    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("任务记录网站", CodeGenTypeEnum.HTML, 1L,1L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
    @Test
    void generateVueProjectCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "简单的任务记录网站，总代码量不超过 200 行",
                CodeGenTypeEnum.VUE_PROJECT, 1L,1L);
        // 阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        // 验证结果
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }

    @Test
    void generateVueProjectCodeStreamDebugPrintChunks() {
        // 要调试的应用 ID，对应 tmp/code_output/vue_project_1 里的数字 1
        Long appId = 1L;

        // 当前已有的完整版本，这个目录必须已经存在并且能运行
        Long sourceVersionNo = 2L;

        // 本次要生成的新版本，测试会先把 v2 完整复制成 v3，再让 AI 覆盖修改文件
        Long targetVersionNo = 3L;

        // 本次测试的是 Vue 工程模式，所以目录名会是 vue_project_1
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.VUE_PROJECT;

        // 先模拟 AppServiceImpl.prepareNewVersionDirectory 的行为，保证 v3 不是空目录
        prepareDebugVersionDirectory(appId, sourceVersionNo, targetVersionNo, codeGenTypeEnum);

        // 用计数器给每个流式片段编号，方便你在控制台观察 chunk 顺序
        AtomicInteger chunkIndex = new AtomicInteger(0);

        // 这里仍然直接调用 Facade，目的是单独调试 AI 工具调用和流式输出
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "修改这个博客，改成蓝色背景",
                codeGenTypeEnum,
                appId,
                targetVersionNo
        );

        // collectList().block() 会订阅 Flux，真正触发 AI 请求、工具调用和文件写入
        List<String> result = codeStream
                .doOnNext(chunk -> {
                    // 每收到一个后端流式片段，编号加 1
                    int index = chunkIndex.incrementAndGet();
                    // 打印 chunk 分隔线，避免大量 JSON 混在一起看不清
                    System.out.println("\n========== chunk " + index + " ==========");
                    // 打印当前 chunk 原文，里面会出现 ai_response / tool_request / tool_executed
                    System.out.println(chunk);
                })
                // 把所有 chunk 收集成 List，方便测试结束后做断言
                .collectList()
                // 阻塞等待整个流结束；测试方法里可以这样写，业务代码里不要随便 block
                .block();

        // 断言结果列表不为空，说明流至少正常返回了一些内容
        Assertions.assertNotNull(result);
        // 断言至少收到一个 chunk，避免 AI 请求完全没有返回
        Assertions.assertFalse(result.isEmpty());
    }

    private void prepareDebugVersionDirectory(Long appId, Long sourceVersionNo, Long targetVersionNo,
                                              CodeGenTypeEnum codeGenTypeEnum) {
        // 拼出项目目录名，例如 vue_project_1
        String projectDirName = codeGenTypeEnum.getValue() + "_" + appId;

        // 拼出源版本目录，例如 tmp/code_output/vue_project_1/v2
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + projectDirName
                + File.separator + "v" + sourceVersionNo);

        // 拼出目标版本目录，例如 tmp/code_output/vue_project_1/v3
        File targetDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR
                + File.separator + projectDirName
                + File.separator + "v" + targetVersionNo);

        // 源版本必须存在，否则没有完整项目可以复制
        Assertions.assertTrue(sourceDir.exists() && sourceDir.isDirectory(),
                "源版本目录不存在，无法复制: " + sourceDir.getAbsolutePath());

        // 如果目标版本目录已经存在，先删掉，避免上一次失败生成留下半成品文件
        if (targetDir.exists()) {
            FileUtil.del(targetDir);
        }

        // 创建目标版本目录
        FileUtil.mkdir(targetDir);

        // 把源版本完整复制到目标版本，后续 AI 写文件时会覆盖需要修改的文件
        FileUtil.copyContent(sourceDir, targetDir, true);

        // 打印复制结果，方便你确认测试启动前 v3 已经是完整项目
        System.out.println("已复制完整版本目录:");
        System.out.println("source = " + sourceDir.getAbsolutePath());
        System.out.println("target = " + targetDir.getAbsolutePath());
    }
}
