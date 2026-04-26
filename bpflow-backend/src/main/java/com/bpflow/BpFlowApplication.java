package com.bpflow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@SpringBootApplication
@EnableMongoAuditing
@Slf4j
@RequiredArgsConstructor
public class BpFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BpFlowApplication.class, args);
    }

}
