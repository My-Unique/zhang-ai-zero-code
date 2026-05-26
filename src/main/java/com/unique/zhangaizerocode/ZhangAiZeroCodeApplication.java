package com.unique.zhangaizerocode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;


@EnableAspectJAutoProxy(exposeProxy = true)
@MapperScan("com.unique.zhangaizerocode.mapper")
@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})

public class ZhangAiZeroCodeApplication {

/**
 * Spring Boot应用程序的主入口方法
 * 通过调用SpringApplication类的静态run方法来启动应用程序
 *
 * @param args 命令行参数，可以用于配置应用程序的启动参数
 */
    public static void main(String[] args) {
    // 调用SpringApplication的run方法，启动Spring Boot应用程序
    // 参数1：应用程序的主类，用于标识应用程序的入口
    // 参数2：命令行参数数组，可以传递给应用程序
        SpringApplication.run(ZhangAiZeroCodeApplication.class, args);
    }

}
