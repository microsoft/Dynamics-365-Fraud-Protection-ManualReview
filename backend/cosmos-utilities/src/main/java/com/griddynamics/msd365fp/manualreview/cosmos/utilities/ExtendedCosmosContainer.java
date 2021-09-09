// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.cosmos.utilities;


import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.FeedResponse;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedFlux;

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

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.griddynamics.msd365fp.manualreview.cosmos.utilities.Constants.DEFAULT_COSMOS_TIMEOUT_SEC;

@RequiredArgsConstructor
@Slf4j
public class ExtendedCosmosContainer {

    private final CosmosAsyncContainer container;
    private final ObjectMapper jsonMapper;


    public Stream<JsonNode> runCrossPartitionQuery(final String query) {
        log.debug("Executing cross partition query: [{}]", query);
        final CosmosQueryRequestOptions feedOptions = new CosmosQueryRequestOptions();

        CosmosPagedFlux<JsonNode> feedResponseFlux =
                container.queryItems(query, feedOptions, JsonNode.class);
        return feedResponseFlux.byPage()
                .subscribeOn(Schedulers.parallel())
                .map(FeedResponse::getResults)
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
        final CosmosQueryRequestOptions feedOptions = new CosmosQueryRequestOptions();
//        feedOptions.maxItemCount(size);
        Flux<FeedResponse<JsonNode>> res= null;
        CosmosPagedFlux<JsonNode> feedResponseFlux =
                container.queryItems(query, feedOptions, JsonNode.class);
        if (continuationToken != null) {
            res = feedResponseFlux.byPage(continuationToken, size);
        }
        else{
            res = feedResponseFlux.byPage(size);
        }
        var bla = res.blockFirst(Duration.ofSeconds(DEFAULT_COSMOS_TIMEOUT_SEC));;
        return new Page(
                bla.getResults().stream(),

                bla.getResponseHeaders().get("x-ms-continuation"));

    }

    public Stream<JsonNode> runPartitionQuery(final String query, final String partitionId) {
        final CosmosQueryRequestOptions feedOptions = new CosmosQueryRequestOptions();
        feedOptions.setPartitionKey(new PartitionKey(partitionId));
        CosmosPagedFlux<JsonNode>  feedResponseFlux =
                container.queryItems(query, feedOptions, JsonNode.class);
        return feedResponseFlux.byPage()
                .subscribeOn(Schedulers.parallel())
                .map(FeedResponse::getResults)
                .collect(Collectors.toList())
                .blockOptional()
                .orElseThrow()
                .stream()
                .flatMap(List::stream);
    }

    public <T> Optional<T> castCosmosObjectToClassInstance1(final Object object, final Class<T> klass) {
        T res = null;
        try {
            res = jsonMapper.readValue(object.toString(), klass);
        } catch (JsonProcessingException e) {
            log.error("Object can not be parsed for CosmosDB: [{}]", object, e);
        }
        return Optional.ofNullable(res);
    }

    public <T> Optional<T> castCosmosObjectToClassInstance(final JsonNode node, final Class<T> klass) {
        T res = null;
        try {
            res = jsonMapper.readValue(node.toString(), klass);
        } catch (JsonProcessingException e) {
            log.error("Object can not be parsed for CosmosDB: [{}]", node, e);
        }
        return Optional.ofNullable(res);
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    public static class Page {
        Stream<JsonNode> content = Stream.empty();
        String continuationToken = null;
    }

}
