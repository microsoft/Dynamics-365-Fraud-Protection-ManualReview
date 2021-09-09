// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.DurableEventHubProcessorClientRegistry;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.DurableEventHubProducerClientRegistry;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.HealthCheckProcessor;
import com.griddynamics.msd365fp.manualreview.ehub.durable.streaming.DurableEventHubProducerClient;
import com.griddynamics.msd365fp.manualreview.model.ItemLock;
import com.griddynamics.msd365fp.manualreview.model.event.Event;
import com.griddynamics.msd365fp.manualreview.model.event.internal.*;
import com.griddynamics.msd365fp.manualreview.model.event.type.LockActionType;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.queues.config.properties.ApplicationProperties;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.HealthCheck;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Queue;
import com.griddynamics.msd365fp.manualreview.queues.repository.HealthCheckRepository;
import com.griddynamics.msd365fp.manualreview.queues.repository.QueueRepository;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.DEFAULT_QUEUE_PAGE_SIZE;

@Slf4j
@RequiredArgsConstructor
@Service
public class StreamService implements HealthCheckProcessor {

    public static final String ITEM_ASSIGNMENT_EVENT_HUB = "item-assignment-event-hub";
    public static final String ITEM_LOCK_EVENT_HUB = "item-lock-event-hub";
    public static final String QUEUE_SIZE_EVENT_HUB = "queue-size-event-hub";
    public static final String OVERALL_SIZE_EVENT_HUB = "overall-size-event-hub";
    public static final String ITEM_LABEL_EVENT_HUB = "item-label-event-hub";
    public static final String ITEM_RESOLUTION_EVENT_HUB = "item-resolution-event-hub";
    public static final String QUEUE_UPDATE_EVENT_HUB = "queue-update-event-hub";
    public static final String EVENT_HUB_CONSUMER = "event-hub-consumer";
    public static final String EH_HEALTH_CHECK_PREFIX = "EH";
    private final ModelMapper modelMapper;
    private final QueueRepository queueRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final ApplicationProperties applicationProperties;

    @Setter(onMethod = @__({@Autowired}))
    private DurableEventHubProducerClientRegistry producerRegistry;
    @Setter(onMethod = @__({@Autowired}))
    private DurableEventHubProcessorClientRegistry processorRegistry;
    @Setter(onMethod = @__({@Autowired}))
    private StreamService thisService;

    @Value("${azure.event-hub.health-check-ttl}")
    private Duration healthCheckTtl;
    @Value("${azure.event-hub.health-check-allowed-delay}")
    private Duration healthCheckAllowedDelay;
    @Value("${azure.event-hub.health-check-batch-size}")
    private long healthCheckBatchSize;

    private final String healthCheckId = UUID.randomUUID().toString();
    private long healthCheckNum = 0;


    @Override
    public void processConsumerHealthCheck(final String hubName, final String partition, final String checkId) {
        try {
            thisService.saveReceivedHealthCheck(hubName, partition, checkId);
        } catch (Exception e) {
            log.warn("Health-check [{}] for [{}] has been received on partition [{}] but can't be saved to DB due to:",
                    checkId, hubName, partition, e);
        }
    }

    @Retry(name = "cosmosOptimisticUpdate")
    protected void saveReceivedHealthCheck(final String hubName, final String partition, final String checkId) {
        HealthCheck healthCheck = healthCheckRepository.findById(checkId).orElseGet(() ->
                HealthCheck.builder()
                        .id(checkId)
                        .type(EVENT_HUB_CONSUMER)
                        .receivedBy(applicationProperties.getInstanceId())
                        .ttl(healthCheckTtl.toSeconds())
                        .build());
        healthCheck.setActive(false);
        healthCheck.setDetails("partition:" + partition);
        healthCheck.setResult(true);
        healthCheckRepository.save(healthCheck);
    }

    public boolean checkStreamingHealth() {
        List<HealthCheck> overdueHealthChecks = healthCheckRepository.findAllByTypeAndActiveIsTrueAndCreatedLessThan(
                EVENT_HUB_CONSUMER,
                OffsetDateTime.now().minus(healthCheckAllowedDelay).toEpochSecond());
        if (!overdueHealthChecks.isEmpty()) {
            log.error("Negative Event Hub health-check have been discovered:{}", overdueHealthChecks);
        }
        overdueHealthChecks.forEach(hc -> {
            hc.setResult(false);
            hc.setActive(false);
            healthCheckRepository.save(hc);
        });

        List<Mono<Void>> sendings = new LinkedList<>();
        processorRegistry.forEach((hub, client) -> {
            for (int i = 0; i < healthCheckBatchSize; i++) {
                HealthCheck healthCheck = HealthCheck.builder()
                        .id(String.join("-",
                                EH_HEALTH_CHECK_PREFIX,
                                applicationProperties.getInstanceType(),
                                hub,
                                String.valueOf(healthCheckNum),
                                healthCheckId))
                        .type(EVENT_HUB_CONSUMER)
                        .generatedBy(applicationProperties.getInstanceId())
                        .active(true)
                        .ttl(healthCheckTtl.toSeconds())
                        ._etag("new")
                        .build();
                sendings.add(client.sendHealthCheckPing(healthCheck.getId())
                        .doOnSuccess(v -> {
                            try {
                                healthCheck.setCreated(OffsetDateTime.now());
                                healthCheckRepository.save(healthCheck);
                            } catch (CosmosAccessException e) {
                                log.debug("Receiver already inserted this [{}] health-check entry", healthCheck.getId());
                            }
                        }));
                healthCheckNum++;
            }
        });
        Mono.zipDelayError(sendings, results -> results)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        return overdueHealthChecks.isEmpty();
    }

    /**
     * Sends event to the {@link DurableEventHubProducerClient}
     *
     * @param event   the event object
     * @param channel the name of producer
     * @return true when event was successfully sent
     */
    public <T extends Event> Mono<Void> sendEvent(T event, String channel) {
        log.info("Sending event to [{}] with body: [{}]", channel, event);
        return producerRegistry.get(channel).send(event)
                .doOnSuccess(v -> log.info("Event [{}] sending has been started successfully.", event.getId()))
                .doOnError(v -> log.error("Event [{}] has not been sent: [{}]", event.getId(), event));
    }

    /**
     * Sending of Item assignment event.
     * The method should be used in case if there were NOT any
     * assignments before (e.g. item was just enriched).
     * Otherwise use {@link this.sendItemAssignmentEvent(Item, Set)}
     */
    public void sendItemAssignmentEvent(final Item item) {
        try {
            Set<String> newIds = item.getQueueIds();
            if (CollectionUtils.isEmpty(newIds)) {
                newIds = getActiveResidualQueues().stream().map(Queue::getId).collect(Collectors.toSet());
            }
            sendItemAssignmentEvent(item, newIds, Set.of())
                    .subscribeOn(Schedulers.elastic())
                    .subscribe();
        } catch (BusyException e) {
            log.error("Event [{}] for item [{}] wasn't sent due to database overload",
                    ItemAssignmentEvent.class, item.getId());
        }
    }

    /**
     * Sending of Item assignment event.
     * The method should be used in case if item had any
     * assignments before. The empty queueIds will be treated as set of
     * residual queues (except newIds when item is inactive).
     * Otherwise use {@link this.sendItemAssignmentEvent(Item)}
     */
    public void sendItemAssignmentEvent(final Item item, final Set<String> oldQueueIds) {
        try {
            Set<String> newIds = item.getQueueIds();
            Set<String> oldIds = oldQueueIds;
            if (CollectionUtils.isEmpty(newIds) && item.isActive()) {
                newIds = getActiveResidualQueues().stream().map(Queue::getId).collect(Collectors.toSet());
            }
            if (CollectionUtils.isEmpty(oldIds)) {
                oldIds = getActiveResidualQueues().stream().map(Queue::getId).collect(Collectors.toSet());
            }
            sendItemAssignmentEvent(item, newIds, oldIds)
                    .subscribeOn(Schedulers.elastic())
                    .subscribe();
        } catch (BusyException e) {
            log.error("Event [{}] for item [{}] wasn't sent due to database overload",
                    ItemAssignmentEvent.class, item.getId());
        }
    }

    private Mono<Void> sendItemAssignmentEvent(final Item item, final Set<String> newIds, final Set<String> oldIds) {
        ItemAssignmentEvent event = ItemAssignmentEvent.builder()
                .id(item.getId())
                .newQueueIds(newIds)
                .oldQueueIds(oldIds)
                .actioned(OffsetDateTime.now())
                .build();
        return sendEvent(event, ITEM_ASSIGNMENT_EVENT_HUB);
    }

    public void sendItemLockEvent(Item item, ItemLock prevLock, LockActionType actionType) {
        ItemLockEvent event = ItemLockEvent.builder()
                .id(item.getId())
                .actionType(actionType)
                .build();
        if (actionType.isRelease()) {
            event.setQueueId(prevLock.getQueueId());
            event.setQueueViewId(prevLock.getQueueViewId());
            event.setOwnerId(prevLock.getOwnerId());
            event.setLocked(prevLock.getLocked());
            event.setReleased(OffsetDateTime.now());
        } else {
            event.setQueueId(item.getLock().getQueueId());
            event.setQueueViewId(item.getLock().getQueueViewId());
            event.setOwnerId(item.getLock().getOwnerId());
            event.setLocked(item.getLock().getLocked());
        }
        sendEvent(event, ITEM_LOCK_EVENT_HUB)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }

    public void sendQueueSizeEvent(Queue queue) {
        QueueSizeUpdateEvent event = modelMapper.map(queue, QueueSizeUpdateEvent.class);
        sendEvent(event, QUEUE_SIZE_EVENT_HUB)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }

    public void sendOverallSizeEvent(int size) {
        OverallSizeUpdateEvent event = new OverallSizeUpdateEvent(size, OffsetDateTime.now());
        sendEvent(event, OVERALL_SIZE_EVENT_HUB)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }

    public void sendItemLabelEvent(final Item item, final Item oldItem) {
        ItemLabelEvent event = ItemLabelEvent.builder()
                .id(item.getId())
                .label(item.getLabel())
                .assesmentResult(item.getAssessmentResult())
                .decisionApplyingDuration(oldItem.getLock() == null || oldItem.getLock().getLocked() == null ? Duration.ZERO :
                        Duration.between(oldItem.getLock().getLocked(), item.getLabel().getLabeled()))
                .build();
        sendEvent(event, ITEM_LABEL_EVENT_HUB)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }


    public ItemResolutionEvent createItemResolvedEvent(final Item item) {
        ItemResolutionEvent res = modelMapper.map(item, ItemResolutionEvent.class);
        res.setId(item.getId() + "-" + item.getLabel().getLabeled());
        return modelMapper.map(item, ItemResolutionEvent.class);
    }

    public Mono<Void> sendItemResolvedEvent(final Event event) {
        return sendEvent(event, ITEM_RESOLUTION_EVENT_HUB);
    }

    public void sendQueueUpdateEvent(final Queue queue) {
        QueueUpdateEvent event = modelMapper.map(queue, QueueUpdateEvent.class);
        sendEvent(event, QUEUE_UPDATE_EVENT_HUB)
                .subscribeOn(Schedulers.elastic())
                .subscribe();
    }

    //TODO: caching
    private Collection<Queue> getActiveResidualQueues() throws BusyException {
        return PageProcessingUtility.getAllPages(
                continuation -> queueRepository.getQueueList(
                        true, true, DEFAULT_QUEUE_PAGE_SIZE, continuation));
    }

}
