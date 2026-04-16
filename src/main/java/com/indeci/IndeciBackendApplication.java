package com.indeci;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class IndeciBackendApplication {

    public static void main(String[] args) {

        ConfigurableApplicationContext context =
                SpringApplication.run(IndeciBackendApplication.class, args);

        PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
        

        // 🔥 GENERAR HASH
        System.out.println("HASH: " + encoder.encode("123456"));
    }
}