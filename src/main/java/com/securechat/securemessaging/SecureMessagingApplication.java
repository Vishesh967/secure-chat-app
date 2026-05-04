package com.securechat.securemessaging;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class SecureMessagingApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureMessagingApplication.class, args);
    }
}
