package com.reportcentre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ReportCentreApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportCentreApplication.class, args);
    }
}
