package com.unique.zhangaizerocode.core.parser;

import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\R?([\\s\\S]*?)(?:```|\\z)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_MARKER_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*\\[(?:工具调用|正在编写)](?:\\s*写入文件)?\\s+[^\\r\\n]+\\s*\\R?"
    );

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(cleanHtmlDocument(htmlCode));
        } else {
            // 如果没有找到代码块，将整个内容作为HTML
            result.setHtmlCode(cleanHtmlDocument(codeContent));
        }
        return result;
    }

    /**
     * 提取HTML代码内容
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private static String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String cleanGeneratedCode(String codeContent) {
        return TOOL_MARKER_LINE_PATTERN.matcher(codeContent).replaceAll("").trim();
    }

    private static String cleanHtmlDocument(String codeContent) {
        String code = cleanGeneratedCode(codeContent)
                .replaceFirst("(?is)^\\s*```html\\s*", "")
                .replaceFirst("(?is)^\\s*```\\s*", "")
                .replaceFirst("(?is)\\s*```\\s*$", "")
                .trim();

        int startIndex = findHtmlStartIndex(code);
        if (startIndex > 0) {
            code = code.substring(startIndex).trim();
        }

        int endIndex = code.toLowerCase().lastIndexOf("</html>");
        if (endIndex >= 0) {
            code = code.substring(0, endIndex + "</html>".length()).trim();
        }
        return code;
    }

    private static int findHtmlStartIndex(String code) {
        String lowerCode = code.toLowerCase();
        int doctypeIndex = lowerCode.indexOf("<!doctype");
        if (doctypeIndex >= 0) {
            return doctypeIndex;
        }
        return lowerCode.indexOf("<html");
    }
}


