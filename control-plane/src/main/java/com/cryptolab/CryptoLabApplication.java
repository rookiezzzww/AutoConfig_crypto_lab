package com.cryptolab;

import com.cryptolab.config.LabProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LabProperties.class)
/** 密码漏洞实验室控制面的 Spring Boot 程序入口。 */
public class CryptoLabApplication {
    /** 创建 Spring 容器，并启动 REST 服务和定时状态同步任务。 */
    public static void main(String[] args) {
        SpringApplication.run(CryptoLabApplication.class, args);
    }
}
