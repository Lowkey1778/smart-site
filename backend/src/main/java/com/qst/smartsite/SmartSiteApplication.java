package com.qst.smartsite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 建筑安全智能监控平台 - 启动类
 * 技术栈：SpringBoot 3.3 + MyBatis-Plus + MySQL + JWT + WebSocket
 */
@SpringBootApplication
@MapperScan("com.qst.smartsite.mapper")
@EnableScheduling
public class SmartSiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSiteApplication.class, args);
        System.out.println("""

                ==================================================
                  建筑安全智能监控平台 后端启动成功
                  接口地址: http://localhost:8080
                  登录接口: POST /api/auth/login
                ==================================================
                """);
    }
}
