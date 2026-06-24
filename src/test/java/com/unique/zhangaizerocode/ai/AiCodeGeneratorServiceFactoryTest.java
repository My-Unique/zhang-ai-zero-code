package com.unique.zhangaizerocode.ai;

import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiCodeGeneratorServiceFactoryTest {

    @Test
    void appNameGeneratorServiceShouldBuildWithoutChatMemoryProvider() {
        AiCodeGeneratorServiceFactory factory = new AiCodeGeneratorServiceFactory();
        ReflectionTestUtils.setField(factory, "chatModel", mock(ChatModel.class));

        AppNameGeneratorService service = factory.appNameGeneratorService();

        assertThat(service).isNotNull();
    }
}
