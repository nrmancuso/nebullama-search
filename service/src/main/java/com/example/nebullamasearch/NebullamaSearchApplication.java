package com.example.nebullamasearch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NebullamaSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(NebullamaSearchApplication.class, args);
    }
}
