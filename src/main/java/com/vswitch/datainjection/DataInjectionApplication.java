package com.vswitch.datainjection;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataInjectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataInjectionApplication.class, args);
    }
}
