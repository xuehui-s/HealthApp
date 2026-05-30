package it.guowei.healthapp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(scanBasePackages = {"it.guowei.healthapp", "Controller", "Service", "Util", "Config"})
@MapperScan("Mapper")
public class HealthAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthAppApplication.class, args);
    }

}
