// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.service;

import com.griddynamics.msd365fp.manualreview.analytics.config.properties.ApplicationProperties;
import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.*;
import com.griddynamics.msd365fp.manualreview.analytics.repository.*;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.DurableEventHubProcessorClientRegistry;
import com.griddynamics.msd365fp.manualreview.ehub.durable.model.HealthCheckProcessor;
import com.griddynamics.msd365fp.manualreview.model.event.internal.*;
import com.griddynamics.msd365fp.manualreview.model.event.type.ItemPlacementType;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.OVERALL_PLACEMENT_ID;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamService implements HealthCheckProcessor {

    public static final String EVENT_HUB_CONSUMER = "event-hub-consumer";
    public static final String EH_HEALTH_CHECK_PREFIX = "EH";

    private final ResolutionRepository resolutionRepository;
    private final ItemLockActivityRepository lockActivitiesRepository;
    private final CollectedQueueInfoRepository collectedQueueInfoRepository;
    private final QueueSizeCalculationActivityRepository queueSizeCalculationActivityRepository;
    private final ItemLabelActivityRepository itemLabelActivityRepository;
    private final ItemPlacementActivityRepository itemPlacementActivityRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final ApplicationProperties applicationProperties;

    private final ModelMapper modelMapper;
    @Setter(onMethod = @__({@Autowired}))
    private DurableEventHubProcessorClientRegistry processorRegistry;
    @Setter(onMethod = @__({@Autowired}))
    private StreamService thisService;

    @Value("${azure.cosmosdb.default-ttl}")
    private Duration defaultTtl;
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

    public void processItemLockEvent(ItemLockEvent event) {
        log.info("Item Lock event [{}] has been received from the Queues BE. [{}]", event.getId(), event);
        ItemLockActivityEntity activity = modelMapper.map(event, ItemLockActivityEntity.class);
        activity.setTtl(defaultTtl.toSeconds());
        lockActivitiesRepository.save(modelMapper.map(event, ItemLockActivityEntity.class));
    }

    public void processQueueSizeUpdateEvent(QueueSizeUpdateEvent event) {
        log.info("Queue Size Update event has been received from the Queues BE. [{}]", event);
        QueueSizeCalculationActivityEntity activity = modelMapper.map(event, QueueSizeCalculationActivityEntity.class);
        activity.setTtl(defaultTtl.toSeconds());
        queueSizeCalculationActivityRepository.save(modelMapper.map(event, QueueSizeCalculationActivityEntity.class));
    }

    public void processOverallSizeUpdateEvent(OverallSizeUpdateEvent event) {
        log.info("Overall Size Update event has been received from the Queues BE. [{}]", event);
        QueueSizeCalculationActivityEntity activityEntity =
                modelMapper.map(event, QueueSizeCalculationActivityEntity.class);
        activityEntity.setTtl(defaultTtl.toSeconds());
        queueSizeCalculationActivityRepository.save(activityEntity);
    }

    public void processItemAssignmentEvent(ItemAssignmentEvent event) {
        log.info("ItemAssignment event has been received from the Queues BE. [{}]", event);
        if (event.getId() == null) {
            log.error("ItemAssignment event is configured incorrectly. Event doesn't have itemId. [{}]", event);
            return;
        }
        saveItemAddedToQueueActivities(event);
        saveItemDeletedFromQueueActivities(event);
    }

    /**
     * Save activity entities for item that was added to a queues.
     */
    private void saveItemAddedToQueueActivities(ItemAssignmentEvent event) {
        if (CollectionUtils.isEmpty(event.getOldQueueIds())) {
            ItemPlacementActivityEntity activityEntity = ItemPlacementActivityEntity.builder()
                    .id(event.getId() + "-" + ItemPlacementType.ADDED + "-" + event.getActioned().toString())
                    .actioned(event.getActioned())
                    .type(ItemPlacementType.ADDED)
                    .itemId(event.getId())
                    .queueId(OVERALL_PLACEMENT_ID)
                    .ttl(defaultTtl.toSeconds())
                    .build();
            itemPlacementActivityRepository.save(activityEntity);
            log.info("[{}] overall assignment activity entity for the item [{}] has been saved",
                    activityEntity.getType(), activityEntity.getId());
        }
        SetUtils.difference(event.getNewQueueIds(), event.getOldQueueIds())
                .forEach(queueId -> {
                    ItemPlacementActivityEntity activityEntity = ItemPlacementActivityEntity.builder()
                            .id(event.getId() + "-" + queueId + "-" + event.getActioned().toString())
                            .actioned(event.getActioned())
                            .type(ItemPlacementType.ADDED)
                            .itemId(event.getId())
                            .queueId(queueId)
                            .ttl(defaultTtl.toSeconds())
                            .build();
                    itemPlacementActivityRepository.save(activityEntity);
                    log.info("[{}] assignment activity entity for the item [{}] in queue [{}] has been saved",
                            activityEntity.getType(), activityEntity.getId(), queueId);
                });
    }

    /**
     * Save activity entities for item that was deleted from a queue.
     */
    private void saveItemDeletedFromQueueActivities(ItemAssignmentEvent event) {
        if (CollectionUtils.isEmpty(event.getNewQueueIds())) {
            ItemPlacementActivityEntity activityEntity = ItemPlacementActivityEntity.builder()
                    .id(event.getId() + "-" + ItemPlacementType.RELEASED + "-" + event.getActioned().toString())
                    .actioned(event.getActioned())
                    .type(ItemPlacementType.RELEASED)
                    .itemId(event.getId())
                    .queueId(OVERALL_PLACEMENT_ID)
                    .ttl(defaultTtl.toSeconds())
                    .build();
            itemPlacementActivityRepository.save(activityEntity);
            log.info("[{}] overall assignment activity entity for the item [{}] has been saved",
                    activityEntity.getType(), activityEntity.getId());
        }
        SetUtils.difference(event.getOldQueueIds(), event.getNewQueueIds())
                .forEach(queueId -> {
                    ItemPlacementActivityEntity activityEntity = ItemPlacementActivityEntity.builder()
                            .id(event.getId() + "-" + queueId + "-" + event.getActioned().toString())
                            .actioned(event.getActioned())
                            .type(ItemPlacementType.RELEASED)
                            .itemId(event.getId())
                            .queueId(queueId)
                            .ttl(defaultTtl.toSeconds())
                            .build();
                    itemPlacementActivityRepository.save(activityEntity);
                    log.info("[{}] assignment activity entity for the item [{}] in queue [{}] has been saved",
                            activityEntity.getType(), activityEntity.getId(), queueId);
                });
    }

    public void processItemLabelEvent(ItemLabelEvent event) {
        log.info("ItemLabel event has been received from the Queues BE. [{}]", event);
        if (event.getId() == null) {
            log.error("ItemLabel event is configured incorrectly. Event has null itemId. [{}]", event);
            return;
        }
        saveItemLabelActivity(event);
    }

    private void saveItemLabelActivity(final ItemLabelEvent event) {
        ItemLabelActivityEntity entity = ItemLabelActivityEntity.builder()
                .id(event.getId() + "-" + event.getLabel().getLabeled())
                .analystId(event.getLabel().getAuthorId())
                .label(event.getLabel().getValue())
                .labeled(event.getLabel().getLabeled())
                .queueId(event.getLabel().getQueueId())
                .queueViewId(event.getLabel().getQueueViewId())
                .merchantRuleDecision(event.getAssesmentResult() == null ?
                        null :
                        event.getAssesmentResult().getMerchantRuleDecision())
                .decisionApplyingDuration(event.getDecisionApplyingDuration())
                .riskScore(event.getAssesmentResult().getRiskScore())
                .ttl(defaultTtl.toSeconds())
                .build();
        itemLabelActivityRepository.save(entity);
        log.info("[{}] label activity entity for the item [{}] has been saved", entity.getLabel(), entity.getId());
    }

    public void processItemResolutionEvent(ItemResolutionEvent event) {
        log.info("ItemResolution event has been received from the Queues BE. [{}]", event);
        if (event.getId() == null) {
            log.error("ItemResolution event is configured incorrectly. Event has null itemId. [{}]", event);
            return;
        }
        Resolution activity = modelMapper.map(event, Resolution.class);
        activity.setTtl(defaultTtl.toSeconds());
        resolutionRepository.save(modelMapper.map(event, Resolution.class));
    }

    public void processQueueUpdateEvent(QueueUpdateEvent event) {
        log.info("QueueUpdate event has been received from the Queues BE. [{}]", event);
        if (event.getId() == null) {
            log.error("QueueUpdate event is configured incorrectly. Event has null queueId. [{}]", event);
            return;
        }
        CollectedQueueInfoEntity infoEntity = collectedQueueInfoRepository.findById(event.getId())
                .orElseGet(CollectedQueueInfoEntity::new);
        if (event.getReviewers() != null) {
            infoEntity.getAllTimeReviewers().addAll(event.getReviewers());
        }
        if (event.getSupervisors() != null) {
            infoEntity.getAllTimeSupervisors().addAll(event.getSupervisors());
        }
        modelMapper.map(event, infoEntity);
        infoEntity.setTtl(defaultTtl.toSeconds());
        collectedQueueInfoRepository.save(modelMapper.map(event, CollectedQueueInfoEntity.class));
    }
}
