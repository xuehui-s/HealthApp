package it.guowei.healthapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * scanBasePackages 说明：
 * - it.guowei.healthapp  企业版代码（管理端/AI Agent/MQ/公共组件）
 * - Controller/Service/Util/Config  业务代码（预约/缴费/登录）
 * - Listener/Job  消息监听与定时消费（Redis 队列 → sys_message 落库）
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(scanBasePackages = {
        "it.guowei.healthapp", "Controller", "Service", "Util", "Config", "Listener", "Job"})
@MapperScan({"Mapper", "it.guowei.healthapp.infrastructure.mapper"})
public class HealthAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthAppApplication.class, args);
    }

}
