// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues;


import com.azure.spring.autoconfigure.aad.AADAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication(exclude = AADAutoConfiguration.class)
@EnableScheduling
public class QueuesApplication {

    public static void main(String[] args) {
        Schedulers.enableMetrics();
        SpringApplication.run(QueuesApplication.class, args);
    }

}
