// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.cosmos.utilities;

import com.azure.cosmos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.griddynamics.msd365fp.manualreview.cosmos.utilities.Constants.DEFAULT_COSMOS_TIMEOUT_SEC;

@RequiredArgsConstructor
@Slf4j
public class ExtendedCosmosContainer {

    private final CosmosContainer container;
    private final ObjectMapper jsonMapper;


    public Stream<CosmosItemProperties> runCrossPartitionQuery(final String query) {
        log.debug("Executing cross partition query: [{}]", query);
        final FeedOptions feedOptions = new FeedOptions();
        feedOptions.enableCrossPartitionQuery(true);
        Flux<FeedResponse<CosmosItemProperties>> feedResponseFlux =
                container.queryItems(query, feedOptions);
        return feedResponseFlux.subscribeOn(Schedulers.parallel())
                .map(FeedResponse::results)
                .collect(Collectors.toList())
                .blockOptional()
                .orElseThrow()
                .stream()
                .flatMap(List::stream);

    }

    public Page runCrossPartitionPageableQuery(final String query,
                                               final int size,
                                               final String continuationToken) {
        log.debug("Executing pageable query with size [{}] and continuation token [{}]: [{}]",
                size, continuationToken, query);
        final FeedOptions feedOptions = new FeedOptions();
        feedOptions.enableCrossPartitionQuery(true);
        feedOptions.maxItemCount(size);
        if (continuationToken != null) {
            feedOptions.requestContinuation(continuationToken);
        }
        Flux<FeedResponse<CosmosItemProperties>> feedResponseFlux =
                container.queryItems(query, feedOptions);
        FeedResponse<CosmosItemProperties> res = feedResponseFlux.blockFirst(Duration.ofSeconds(DEFAULT_COSMOS_TIMEOUT_SEC));
        return new Page(
                Objects.requireNonNull(res).results().stream(),
                res.responseHeaders().get("x-ms-continuation"));

    }

    public Stream<CosmosItemProperties> runPartitionQuery(final String query, final String partitionId) {
        final FeedOptions feedOptions = new FeedOptions();
        feedOptions.enableCrossPartitionQuery(false);
        feedOptions.partitionKey(new PartitionKey(partitionId));
        Flux<FeedResponse<CosmosItemProperties>> feedResponseFlux =
                container.queryItems(query, feedOptions);
        return feedResponseFlux.subscribeOn(Schedulers.parallel())
                .map(FeedResponse::results)
                .collect(Collectors.toList())
                .blockOptional()
                .orElseThrow()
                .stream()
                .flatMap(List::stream);
    }

    public <T> Optional<T> castCosmosObjectToClassInstance(final Object object, final Class<T> klass) {
        T res = null;
        try {
            res = jsonMapper.readValue(object.toString(), klass);
        } catch (JsonProcessingException e) {
            log.error("Object can not be parsed for CosmosDB: [{}]", object, e);
        }
        return Optional.ofNullable(res);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Page {
        Stream<CosmosItemProperties> content = Stream.empty();
        String continuationToken = null;
    }

}
