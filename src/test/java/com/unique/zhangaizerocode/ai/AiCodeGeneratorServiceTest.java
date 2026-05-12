package com.unique.zhangaizerocode.ai;

import com.mybatisflex.spring.boot.MultiDataSourceAutoConfiguration;
import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("做个游戏大全界面，不要超过五十行");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiF8ileCode() {
        MultiFileCodeResult multiFileCode = aiCodeGeneratorService.generateMultiFileCode("做个qq空间的界面，不要超过五十行");
        Assertions.assertNotNull(multiFileCode);
    }
}
