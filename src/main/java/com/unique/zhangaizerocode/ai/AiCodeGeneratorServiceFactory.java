package com.unique.zhangaizerocode.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.stream.Stream;
//实现ai方法

/**
 * AI代码生成器服务配置类
 * 该类用于配置和创建AI代码生成器服务的Bean
 */
//工厂类 将 AiCodeGeneratorService 注册进 Spring 容器了
//用工厂类创建实例
@Configuration
public class AiCodeGeneratorServiceFactory {

    /**
     * 注入ChatModel Bean
     * ChatModel用于与AI模型进行交互
     */
    @Resource
    private ChatModel chatModel;
    @Resource
    private StreamingChatModel streamingChatModel;
    /**
     * 创建并配置AiCodeGeneratorService Bean
     * 使用AiServices.create方法创建AI代码生成器服务实例
     * 该方法将chatModel注入到AiCodeGeneratorService中，使其能够调用AI模型进行代码生成

 *
     * @return 配置好的AiCodeGeneratorService实例，已注入chatModel依赖
     */
    @Bean  // 将该方法返回的对象注册为Spring容器中的Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        // 定义一个返回AiCodeGeneratorService类型实例的方法

      //  return AiServices.create(AiCodeGeneratorService.class, chatModel);
        //        AI Service 的特点：你写接口，它帮你生成实现。
        // 使用AiServices.create创建服务“实例”，并传入chatModel参数

        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();


    }
}
