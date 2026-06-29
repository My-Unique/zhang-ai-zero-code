package com.unique.zhangaizerocode.ai;

import com.unique.zhangaizerocode.ai.model.HtmlCodeResult;
import com.unique.zhangaizerocode.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import reactor.core.publisher.Flux;
//定义ai方法
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成的代码结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);

    @SystemMessage(fromResource = "prompt/app-name-system-prompt.txt")
    @UserMessage("用户的应用描述：{{initPrompt}}")
    String generateAppName(@V("initPrompt") String initPrompt);


    /**
     * 生成 Vue 项目代码（流式）
     *
     * @param userMessage 用户消息
     * @return 生成过程的流式响应
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    @UserMessage("{{userMessage}}")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @V("userMessage") String userMessage);

    /**
     * 创建 Vue 项目代码（流式）
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-create-system-prompt.txt")
    @UserMessage("{{userMessage}}")
    TokenStream generateVueProjectCreateStream(@MemoryId long appId, @V("userMessage") String userMessage);

    /**
     * 修改 Vue 项目代码（流式）
     */
    @SystemMessage(fromResource = "prompt/codegen-vue-project-modify-system-prompt.txt")
    @UserMessage("{{userMessage}}")
    TokenStream generateVueProjectModifyStream(@MemoryId long appId, @V("userMessage") String userMessage);

}
