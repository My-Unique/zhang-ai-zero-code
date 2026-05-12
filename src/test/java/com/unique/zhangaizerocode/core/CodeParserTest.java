package com.unique.zhangaizerocode.core;

import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodeParserTest {

    @Test
    void parseHtmlCode() {
        String codeContent = """
                下面是一段示例 HTML 页面：
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <title>测试页面</title>
                </head>
                <body>
                    <h1>Hello World!</h1>
                </body>
                </html>
                ```
                结束示例 HTML
                """;

        HtmlCodeResult result = CodeParser.parseHtmlCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        System.out.println(result.getHtmlCode());
    }

    @Test
    void parseMultiFileCode() {
        String codeContent = """
                下面是一个多文件网页示例：
                ```html
                <!DOCTYPE html>
                <html>
                <head>
                    <title>多文件示例</title>
                    <link rel="stylesheet" href="style.css">
                </head>
                <body>
                    <h1>欢迎使用</h1>
                    <script src="script.js"></script>
                </body>
                </html>
                ```
                ```css
                h1 {
                    color: blue;
                    text-align: center;
                }
                ```
                ```js
                console.log('页面加载完成');
                ```
                结束示例
                """;

        MultiFileCodeResult result = CodeParser.parseMultiFileCode(codeContent);
        assertNotNull(result);
        assertNotNull(result.getHtmlCode());
        assertNotNull(result.getCssCode());
        assertNotNull(result.getJsCode());

        System.out.println(result.getHtmlCode());
        System.out.println(result.getCssCode());
        System.out.println(result.getJsCode());
    }
}