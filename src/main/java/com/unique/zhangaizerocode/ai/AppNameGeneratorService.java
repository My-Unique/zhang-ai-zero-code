package com.unique.zhangaizerocode.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AppNameGeneratorService {

    @SystemMessage(fromResource = "prompt/app-name-system-prompt.txt")
    @UserMessage("用户的应用描述：{{initPrompt}}")
    String generateAppName(@V("initPrompt") String initPrompt);
}
