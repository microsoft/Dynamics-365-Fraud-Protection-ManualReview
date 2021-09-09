// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.config;


import com.azure.cosmos.CosmosAsyncClient;

import com.azure.cosmos.CosmosAsyncDatabase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.*;

@ComponentScan(basePackages = {"com.griddynamics.msd365fp.manualreview.queues.repository"})
@Configuration
@RequiredArgsConstructor
public class DatabaseConfig {

    @Qualifier("cosmosdbObjectMapper")
    private final ObjectMapper jsonMapper;

    @Bean
    public CosmosAsyncDatabase cosmosDatabase(CosmosAsyncClient cosmosClient,
                                              @Value("${azure.cosmosdb.database}") String databaseId) {
        return cosmosClient.getDatabase(databaseId);
    }

    @Bean
    public ExtendedCosmosContainer itemsContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(ITEMS_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer queuesContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(QUEUES_CONTAINER_NAME), jsonMapper);
    }

    @Bean
    public ExtendedCosmosContainer dictionariesContainer(CosmosAsyncDatabase cosmosDatabase) {
        return new ExtendedCosmosContainer(cosmosDatabase.getContainer(DICTIONARIES_CONTAINER_NAME), jsonMapper);
    }
}
