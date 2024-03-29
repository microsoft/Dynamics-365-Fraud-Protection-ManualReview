// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.config;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncDatabase;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.*;

@ComponentScan(basePackages = {"com.griddynamics.msd365fp.manualreview.analytics.repository"})
@Configuration
@RequiredArgsConstructor
@Slf4j
public class DatabaseConfig {

    @Qualifier("cosmosdbObjectMapper")
    private final ObjectMapper jsonMapper;

    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient cosmosClient,
                                              @Value("${azure.cosmos.database}") String databaseId) {
        return cosmosClient.getDatabase(databaseId);
    }

    @Bean
    public ExtendedCosmosContainer resolutionContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(RESOLUTION_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer itemLabelActivityContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(ITEM_LABEL_ACTIVITY_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer itemLockActivityContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(ITEM_LOCK_ACTIVITY_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer itemPlacementActivityContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(
                cosmosDatabase.getContainer(ITEM_PLACEMENT_ACTIVITY_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer queueSizeCalculationActivityContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(
                cosmosDatabase.getContainer(QUEUE_SIZE_CALCULATION_ACTIVITY_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer collectedQueueInfoContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(
                cosmosDatabase.getContainer(COLLECTED_QUEUE_INFO_CONTAINER_NAME), jsonMapper);
    }
}
