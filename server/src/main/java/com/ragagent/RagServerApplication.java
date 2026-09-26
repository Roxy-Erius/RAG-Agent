package com.ragagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RagServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagServerApplication.class, args);
    }
}
