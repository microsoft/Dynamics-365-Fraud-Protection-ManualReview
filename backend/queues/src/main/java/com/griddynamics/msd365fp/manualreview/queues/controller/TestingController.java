// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.controller;

import com.azure.cosmos.CosmosAsyncClient;
import com.azure.cosmos.CosmosAsyncContainer;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerAsyncClient;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.dfpauth.util.UserPrincipalUtility;
import com.griddynamics.msd365fp.manualreview.ehub.durable.config.properties.EventHubProperties;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.DurableEventHubProducerClientRegistry;
import com.griddynamics.msd365fp.manualreview.model.DisposabilityCheck;
import com.griddynamics.msd365fp.manualreview.model.ItemLabel;
import com.griddynamics.msd365fp.manualreview.model.ItemLock;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.model.event.Event;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.model.exception.IncorrectConditionException;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemDataField;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemFilterField;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueViewType;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.model.testing.MicrosoftDynamicsFraudProtectionV1ModelsBankEventActivityBankEvent;
import com.griddynamics.msd365fp.manualreview.queues.model.testing.MicrosoftDynamicsFraudProtectionV1ModelsPurchaseActivityPurchase;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepository;
import com.griddynamics.msd365fp.manualreview.queues.service.ItemEnrichmentService;
import com.griddynamics.msd365fp.manualreview.queues.service.TestingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.ADMIN_MANAGER_ROLE;
import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.SECURITY_SCHEMA_IMPLICIT;

//TODO: temporary for testing
@RestController
@RequestMapping("/api/testing")
@Tag(name = "testing", description = "Mocks and utilities")
@Slf4j
@SecurityRequirement(name = SECURITY_SCHEMA_IMPLICIT)
@RequiredArgsConstructor
@Secured({ADMIN_MANAGER_ROLE})
@SuppressWarnings({"java:S5411", "java:S3776", "java:S3358" })
public class TestingController {

    private static final Faker faker = new Faker();

    private final ItemRepository itemRepository;
    private final TestingService testingService;
    private final ItemEnrichmentService itemEnrichmentService;
    private final EventHubProperties ehProperties;
    @Qualifier("cosmosdbObjectMapper")
    private final ObjectMapper mapper;
    private final DurableEventHubProducerClientRegistry clientRegistry;
    private final CosmosAsyncClient cosmosClient;
    @Setter(onMethod = @__({@Autowired, @Qualifier("azureDFPAPIWebClient")}))
    private WebClient dfpClient;
    private final Random rand = new Random();

    @Value("${azure.dfp.purchase-event-url}")
    private String purchaseEventUrl;
    @Value("${azure.dfp.bank-event-url}")
    private String bankEventUrl;

    @Operation(summary = "Create a new item directly in the database")
    @PostMapping(value = "/items",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public List<Item> createItem(@RequestBody final Item parameters,
                                 @RequestParam(value = "amount", defaultValue = "1") final int amount) {
        List<Item> res = new LinkedList<>();
        String basename = parameters.getId() + "-";
        for (int i = 0; i < amount; i++) {
            parameters.setId(basename + i);
            parameters.setImported(parameters.getImported().plus(Duration.ofMinutes(i)));
            parameters.setTtl(-1);
            parameters.setLabel(new ItemLabel());
            parameters.setLock(new ItemLock());
            parameters.setReviewers(Collections.emptySet());
            res.add(testingService.createItem(parameters));
        }
        return res;
    }

    @Operation(summary = "Trigger forced item enrichment")
    @PostMapping(value = "/items/{id}/enrichment")
    public void enrichItemById(@PathVariable final String id) {
        itemEnrichmentService.enrichItem(id, true);
    }

    @Operation(summary = "Trigger forced item enrichment for ALL active items")
    @PostMapping(value = "/items/enrichment")
    public void enrichAllActiveItems() throws BusyException {
        Collection<String> items = PageProcessingUtility.getAllPages(
                c -> itemRepository.findActiveItemIds(300, c));
        log.warn("We're trying to reenrich {} items", items.size());
        Scheduler scheduler = Schedulers.fromExecutor(Executors.newSingleThreadExecutor());
        Flux.fromIterable(items)
                .doOnNext(item -> itemEnrichmentService.enrichItem(item, true))
                .subscribeOn(scheduler)
                .subscribe();
    }


    @Operation(summary = "Randomize scores for items in a queue")
    @PostMapping(value = "/queue/{queueId}/score/randomize")
    public void randomizeScore(@PathVariable final String queueId) throws BusyException {
        PageProcessingUtility.executeForAllPages(
                continuationToken -> itemRepository.findActiveItemsByQueueView(
                        QueueViewType.DIRECT,
                        queueId,
                        20,
                        continuationToken,
                        new Sort.Order(Sort.Direction.ASC, ItemDataField.IMPORT_DATE.getPath()),
                        null,
                        null),
                items -> items.getValues().forEach(item -> {
                    int score = rand.nextInt(999);
                    item.getAssessmentResult().setRiskScore(score);
                    item.getDecision().setRiskScore(score);
                    itemRepository.save(item);
                }));
    }

    @Operation(summary = "Get filter samples")
    @PostMapping(value = "/filters/{field}/samples")
    public Set<String> getFilterSamples(@PathVariable("field") final ItemFilterField field) {
        return itemRepository.findFilterSamples(field, null);
    }

    @Operation(summary = "Update the whole DB container by changing phrases from one to another")
    @PostMapping(value = "/databases/{dbName}/container/{containerName}")
    public void replace(@PathVariable("dbName") final String dbName,
                        @PathVariable("containerName") final String containerName,
                        @RequestParam(value = "from-regexp") final List<String> fromRegexpList,
                        @RequestParam(value = "to-string") final List<String> toStringList)
            throws BusyException, IncorrectConditionException {

        log.warn("User [{}] has run word replacing in [{}]/[{}] from {} to {}",
                UserPrincipalUtility.getUserId(),
                dbName,
                containerName,
                fromRegexpList,
                toStringList);

        if (fromRegexpList.size() != toStringList.size()) {
            throw new IncorrectConditionException("amounts of tags and replacements aren't match");
        }
        CosmosAsyncContainer container = cosmosClient.getDatabase(dbName).getContainer(containerName);
        ExtendedCosmosContainer extendedContainer = new ExtendedCosmosContainer(container, mapper);
        String query = String.format(
                "SELECT string FROM (SELECT VALUE toString(c) from c) string where %s",
                String.join(
                        " OR ",
                        fromRegexpList.stream()
                                .map(source -> String.format("CONTAINS(string, '%s')", source))
                                .collect(Collectors.toSet())));
        Map<String, String> replacements = zipToMap(fromRegexpList, toStringList);

        PageProcessingUtility.executeForAllPages(
                continuation -> {
                    ExtendedCosmosContainer.Page res = extendedContainer.runCrossPartitionPageableQuery(
                            query,
                            20,
                            continuation);
                    return new PageableCollection<>(
                            res.getContent()
                                    .map(cip -> cip.get("string"))
                                    .collect(Collectors.toSet()),
                            res.getContinuationToken());
                },
                collection -> collection.stream()
                        .map(str -> {
                            String result = str.toString();
                            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                                result = result.replaceAll(entry.getKey(), entry.getValue());
                            }
                            return result;
                        })
                        .collect(Collectors.toSet())
                        .forEach(str -> {
                            try {
                                container.upsertItem(mapper.readValue(str, Map.class))
                                        .subscribeOn(Schedulers.elastic())
                                        .subscribe();
                            } catch (JsonProcessingException e) {
                                log.error("Can't parse [{}]", str);
                                throw new RuntimeException("Can't parse");
                            }
                        }));
    }

    @Data
    @ToString(callSuper = true)
    @EqualsAndHashCode(callSuper = true)
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DummyEvent extends HashMap<String, Object> implements Event {
        private String id;
        @JsonIgnore
        private Map<String, Object> additionalParams = new HashMap<>();

        @JsonAnySetter
        public void setAdditionalParam(String name, Object value) {
            additionalParams.put(name, value);
        }

        @JsonAnyGetter
        public Object getAdditionalParam(String name) {
            return additionalParams.get(name);
        }
    }

    @Operation(summary = "Send custom event to EventHub")
    @PostMapping(value = "/events/{topic}")
    public void sendEvent(@PathVariable("topic") final String topic,
                          @RequestBody final DummyEvent body) throws JsonProcessingException {
        EventHubProducerAsyncClient client = new EventHubClientBuilder()
                .connectionString(
                        ehProperties.getConnectionString(),
                        topic)
                .buildAsyncProducerClient();
        EventData data = new EventData(mapper.writeValueAsString(body));
        client.send(Set.of(data))
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }

    @Operation(summary = "Send bunch of dummy events to EventHub topics")
    @PostMapping(value = "/events/bunch/{topic}")
    public void sendEvent(@PathVariable("topic") final Set<String> topics) throws JsonProcessingException {
        topics.forEach(name -> {
            EventHubProducerAsyncClient client = new EventHubClientBuilder()
                    .connectionString(
                            ehProperties.getConnectionString(),
                            name)
                    .buildAsyncProducerClient();
            for (int i = 0; i < 100; i++) {
                DummyEvent event = new DummyEvent();
                event.setId(UUID.randomUUID().toString());
                event.put("payload", name + " " + i);
                EventData data = null;
                try {
                    data = new EventData(mapper.writeValueAsString(event));
                    client.send(Set.of(data))
                            .subscribeOn(Schedulers.elastic())
                            .subscribe();
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Operation(summary = "Hard delete for all entries by IDs")
    @DeleteMapping(value = "/databases/{dbName}/container/{containerName}/entries")
    public void deleteAllById(
            @PathVariable("dbName") final String dbName,
            @PathVariable("containerName") final String containerName,
            @Parameter(hidden = true)
            @AuthenticationPrincipal PreAuthenticatedAuthenticationToken principal,
            @RequestBody List<Map<String, String>> ids) {
        CosmosAsyncContainer container = cosmosClient.getDatabase(dbName).getContainer(containerName);
        List<String> toDelete = ids.stream().map(mp -> mp.get("id")).collect(Collectors.toList());
        CosmosItemRequestOptions options = new CosmosItemRequestOptions();
        toDelete.forEach(id ->
                container.deleteItem(id,new PartitionKey(containerName)));
        log.warn("User [{}] has deleted items [{}].", UserPrincipalUtility.extractUserId(principal), toDelete);
    }

    @Operation(summary = "Generate specified amount of purchases. Send them to the DFP. " +
            "Generate events for EventHub.")
    @PostMapping(value = "/dfp/events", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<List<Object>> postEntities(@RequestParam(defaultValue = "1") int amount)
            throws JsonProcessingException {
        final List<List<Object>> result = new LinkedList<>();
        for (int i = 0; i < amount; i++) {
            List<Object> data = new LinkedList<>();
            MicrosoftDynamicsFraudProtectionV1ModelsPurchaseActivityPurchase purchase =
                    testingService.generatePurchase();
            data.add(purchase);
            log.debug(mapper.writeValueAsString(purchase));
            EventResponse purchaseEventResponse = dfpClient.post()
                    .uri(purchaseEventUrl)
                    .bodyValue(purchase)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .flatMap(r -> r.bodyToMono(EventResponse.class))
                    .doOnNext(a -> log.debug(a.toString()))
                    .block();
            data.add(purchaseEventResponse);
            if (purchaseEventResponse != null && purchaseEventResponse.getResultDetails() != null) {
                log.info("Purchase event has been populated to DFP with ID: [{}]",
                        purchaseEventResponse.getResultDetails().getPurchaseId());
            } else {
                log.warn("Purchase event has been populated to DFP but could not parse a response.");
            }
            int bankEventAmount = faker.random().nextInt(3) + 1;
            for (int j = 0; j < bankEventAmount; j++) {
                MicrosoftDynamicsFraudProtectionV1ModelsBankEventActivityBankEvent bankEvent =
                        testingService.generateBankEvent(purchase.getPurchaseId());
                data.add(bankEvent);
                log.debug(mapper.writeValueAsString(bankEvent));
                EventResponse bankEventResponse = dfpClient.post()
                        .uri(bankEventUrl)
                        .bodyValue(bankEvent)
                        .exchange()
                        .flatMap(r -> r.bodyToMono(EventResponse.class))
                        .doOnNext(a -> log.debug(a.toString()))
                        .block();
                if (bankEventResponse != null && bankEventResponse.getResultDetails() != null) {
                    log.info("Bank event has been populated to DFP with response: [{}]",
                            bankEventResponse.getResultDetails().isSuccess());
                } else {
                    log.warn("Bank event has been populated to DFP but could not parse a response.");
                }
            }
            result.add(data);
        }
        return result;
    }

    @Operation(summary = "Check disposable email domains for all the items")
    @PostMapping(value = "/check-email-domains")
    public List<DisposabilityCheck> checkEmailDomains() {
        return testingService.checkDisposableEmails();
    }

    @Data
    public static class EventResponse {
        private EventResultDetails resultDetails;
    }

    @Data
    public static class EventResultDetails {
        @JsonProperty("PurchaseId")
        private String purchaseId;
        @JsonProperty("Succeeded")
        private boolean success;
    }

    private static <K, V> Map<K, V> zipToMap(List<K> keys, List<V> values) {
        return IntStream.range(0, keys.size()).boxed()
                .collect(Collectors.toMap(keys::get, values::get));
    }

}
