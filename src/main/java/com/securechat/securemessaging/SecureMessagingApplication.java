package com.securechat.securemessaging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SecureMessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureMessagingApplication.class, args);
    }
}
