// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.controller;

import com.azure.cosmos.CosmosClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.*;
import com.griddynamics.msd365fp.manualreview.analytics.repository.*;
import com.griddynamics.msd365fp.manualreview.analytics.service.TaskService;
import com.griddynamics.msd365fp.manualreview.azuregraph.client.AnalystClient;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.dfpauth.util.UserPrincipalUtility;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.model.event.type.ItemPlacementType;
import com.griddynamics.msd365fp.manualreview.model.event.type.LockActionType;
import com.griddynamics.msd365fp.manualreview.model.exception.IncorrectConfigurationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.*;
import static com.griddynamics.msd365fp.manualreview.analytics.config.ScheduledJobsConfig.COLLECT_ANALYST_INFO_TASK_NAME;

//TODO: temporary!
@RestController
@RequestMapping("/api/testing")
@Tag(name = "testing", description = "The API for testing purposes")
@Slf4j
@RequiredArgsConstructor
@SecurityRequirement(name = SECURITY_SCHEMA_IMPLICIT)
@Secured({ADMIN_MANAGER_ROLE})
public class TestingController {

    Random random = new Random();
    private final ItemLabelActivityRepository performanceRepository;
    private final ItemLockActivityRepository lockActivitiesRepository;
    private final ItemPlacementActivityRepository placementActivitiesRepository;
    private final CollectedAnalystInfoRepository analystInfoRepository;
    private final CollectedQueueInfoRepository queueInfoRepository;
    private final QueueSizeCalculationActivityRepository sizeCalculationActivitiesRepository;
    private final AnalystClient analystClient;
    private final CosmosClient cosmosClient;
    private final TaskService taskService;

    @Qualifier("cosmosdbObjectMapper")
    private final ObjectMapper jsonMapper;

    @Data
    public static class Queue {
        private String id;
        private String name;
    }

    @Operation(summary = "generate random performance entries")
    @PostMapping(value = "/performance-entries", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ItemLabelActivityEntity> generatePerformanceEntries(
            @RequestParam Integer amount,
            @RequestParam Integer maxOldnessInDays) throws IncorrectConfigurationException {
        List<String> analysts = analystClient.getAnalystIds(Set.of(ROLES_ALLOWED_FOR_ACCESS));
        List<ItemLabelActivityEntity> generated = new ArrayList<>(amount);
        List<ItemLockActivityEntity> generatedLocks = new ArrayList<>(amount);

        ExtendedCosmosContainer queueCon = new ExtendedCosmosContainer(cosmosClient
                .getDatabase("QueuesDB")
                .getContainer("Queues"),
                jsonMapper);
        List<Queue> queues = queueCon.runCrossPartitionQuery("SELECT VALUE root FROM " +
                "(SELECT c.id, c.name " +
                "FROM c where " +
                "c.active=true) " +
                "AS root")
                .map(cip -> queueCon.castCosmosObjectToClassInstance(cip, Queue.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        for (int i = 0; i < amount; i++) {
            Duration oldness = Duration.ofDays(random.nextInt(maxOldnessInDays)).plus(Duration.ofHours(random.nextInt(24)));
            ItemLabelActivityEntity entry = ItemLabelActivityEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .labeled(OffsetDateTime.now().minus(oldness))
                    .queueId(queues.get(random.nextInt(queues.size())).getId())
                    .analystId(analysts.get(random.nextInt(analysts.size())))
                    .merchantRuleDecision(random.nextBoolean() ? "Approve" : "Reject")
                    .label(Label.values()[random.nextInt(Label.values().length)])
                    .decisionApplyingDuration(Duration.ofMinutes(random.nextInt(10)))
                    .build();
            OffsetDateTime locked = entry.getLabeled().minus(Duration.ofMinutes(1L + random.nextInt(30)));
            ItemLockActivityEntity lockEntry = ItemLockActivityEntity.builder()
                    .id(entry.getId() + "-" + locked.toString())
                    .locked(locked)
                    .released(entry.getLabeled())
                    .queueId(entry.getQueueId())
                    .ownerId(entry.getAnalystId())
                    .actionType(LockActionType.LABEL_APPLIED_RELEASE)
                    .build();
            generated.add(entry);
            generatedLocks.add(lockEntry);
            if (random.nextInt(5) < 1) {
                locked = locked.minus(Duration.ofHours(1L + random.nextInt(24)));
                LockActionType reason = random.nextBoolean()
                        ? LockActionType.MANUAL_RELEASE
                        : LockActionType.TIMEOUT_RELEASE;
                generatedLocks.add(ItemLockActivityEntity.builder()
                        .id(entry.getId() + "-" + locked.toString())
                        .locked(locked)
                        .released(locked.plus(reason.equals(LockActionType.TIMEOUT_RELEASE)
                                ? Duration.ofMinutes(30)
                                : Duration.ofMinutes(random.nextInt(30))))
                        .queueId(entry.getQueueId())
                        .ownerId(entry.getAnalystId())
                        .actionType(reason)
                        .build());
            }
        }
        queueInfoRepository.saveAll(queues.stream()
                .map(queue -> CollectedQueueInfoEntity.builder()
                        .id(queue.getId())
                        .name(queue.getName())
                        .build())
                .collect(Collectors.toSet()));
        analystInfoRepository.saveAll(analysts.stream()
                .map(id -> CollectedAnalystInfoEntity.builder().id(id).build())
                .collect(Collectors.toSet()));
        lockActivitiesRepository.saveAll(generatedLocks);
        performanceRepository.saveAll(generated);
        taskService.forceTaskRunByName(COLLECT_ANALYST_INFO_TASK_NAME);
        return generated;
    }


    @Operation(summary = "generate random demand/supply entries")
    @PostMapping(value = "/demand-supply-entries", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<ItemPlacementActivityEntity> generateDemandSupplyEntries(@RequestParam Integer amount,
                                                                         @RequestParam Integer maxOldnessInDays) {
        ExtendedCosmosContainer queueCon = new ExtendedCosmosContainer(cosmosClient
                .getDatabase("QueuesDB")
                .getContainer("Queues"),
                jsonMapper);
        List<Queue> queues = queueCon.runCrossPartitionQuery("SELECT VALUE root FROM " +
                "(SELECT c.id, c.name " +
                "FROM c where " +
                "c.active=true) " +
                "AS root")
                .map(cip -> queueCon.castCosmosObjectToClassInstance(cip, Queue.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        List<ItemPlacementActivityEntity> generated = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            Duration oldness = Duration.ofDays(random.nextInt(maxOldnessInDays)).plus(Duration.ofHours(random.nextInt(24)));
            ItemPlacementActivityEntity entry = ItemPlacementActivityEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .actioned(OffsetDateTime.now().minus(oldness))
                    .queueId(queues.get(random.nextInt(queues.size())).getId())
                    .type(ItemPlacementType.values()[random.nextInt(ItemPlacementType.values().length)])
                    .build();
            generated.add(entry);
        }
        queueInfoRepository.saveAll(queues.stream()
                .map(queue -> CollectedQueueInfoEntity.builder()
                        .id(queue.getId())
                        .name(queue.getName())
                        .build())
                .collect(Collectors.toSet()));
        placementActivitiesRepository.saveAll(generated);
        return generated;
    }

    @Operation(summary = "generate random size-history entries")
    @PostMapping(value = "/size-entries", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<QueueSizeCalculationActivityEntity> generateSizeEntries(@RequestParam int oldnessInDays) {
        ExtendedCosmosContainer queueCon = new ExtendedCosmosContainer(cosmosClient
                .getDatabase("QueuesDB")
                .getContainer("Queues"),
                jsonMapper);
        List<Queue> queues = queueCon.runCrossPartitionQuery("SELECT VALUE root FROM " +
                "(SELECT c.id, c.name " +
                "FROM c where " +
                "c.active=true) " +
                "AS root")
                .map(cip -> queueCon.castCosmosObjectToClassInstance(cip, Queue.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        List<QueueSizeCalculationActivityEntity> generated = new ArrayList<>(7);
        for (int i = 0; i < queues.size(); i++) {
            int prevPoint = random.nextInt(500);
            for (int j = 0; j < oldnessInDays * 24; j++) {
                prevPoint += random.nextInt(prevPoint / 4) - prevPoint / 8;
                QueueSizeCalculationActivityEntity entry = QueueSizeCalculationActivityEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .calculated(OffsetDateTime.now().minusHours(j))
                        .queueId(queues.get(i).getId())
                        .size(prevPoint)
                        .build();
                generated.add(entry);
            }
        }
        queueInfoRepository.saveAll(queues.stream()
                .map(queue -> CollectedQueueInfoEntity.builder()
                        .id(queue.getId())
                        .name(queue.getName())
                        .build())
                .collect(Collectors.toSet()));
        sizeCalculationActivitiesRepository.saveAll(generated);
        return generated;
    }

    @Operation(summary = "hard delete performance entries by ids")
    @DeleteMapping("/performance-entries")
    public void deletePerformanceEntries(@Parameter(hidden = true)
                                         @AuthenticationPrincipal PreAuthenticatedAuthenticationToken principal,
                                         @RequestBody List<Map<String, String>> ids) {
        List<String> toDelete = ids.stream().map(mp -> mp.get("id")).collect(Collectors.toList());
        performanceRepository.deleteAll(performanceRepository.findAllById(toDelete));
        log.info("User [{}] has deleted performance-entries [{}].", UserPrincipalUtility.extractUserId(principal), toDelete);
    }

    @Operation(summary = "hard delete performance entries by ids")
    @DeleteMapping("/lock-activity-entries")
    public void deleteLockActivityEntries(@Parameter(hidden = true)
                                          @AuthenticationPrincipal PreAuthenticatedAuthenticationToken principal,
                                          @RequestBody List<Map<String, String>> ids) {
        List<String> toDelete = ids.stream().map(mp -> mp.get("id")).collect(Collectors.toList());
        lockActivitiesRepository.deleteAll(lockActivitiesRepository.findAllById(toDelete));
        log.info("User [{}] has deleted lock-activity-entries [{}].", UserPrincipalUtility.extractUserId(principal), toDelete);
    }

    @Operation(summary = "hard delete demand/supply entries by ids")
    @DeleteMapping("/demand-supply-entries")
    public void deleteDemandSupplyEntries(@Parameter(hidden = true)
                                          @AuthenticationPrincipal PreAuthenticatedAuthenticationToken principal,
                                          @RequestBody List<Map<String, String>> ids) {
        List<String> toDelete = ids.stream().map(mp -> mp.get("id")).collect(Collectors.toList());
        placementActivitiesRepository.deleteAll(placementActivitiesRepository.findAllById(toDelete));
        log.info("User [{}] has deleted demand/supply entries [{}].", UserPrincipalUtility.extractUserId(principal), toDelete);
    }

    @Operation(summary = "hard delete demand/supply entries by ids")
    @DeleteMapping("/size-entries")
    public void deleteSizeEntries(@Parameter(hidden = true)
                                  @AuthenticationPrincipal PreAuthenticatedAuthenticationToken principal,
                                  @RequestBody List<Map<String, String>> ids) {
        List<String> toDelete = ids.stream().map(mp -> mp.get("id")).collect(Collectors.toList());
        sizeCalculationActivitiesRepository.deleteAll(sizeCalculationActivitiesRepository.findAllById(toDelete));
        log.info("User [{}] has deleted demand/supply entries [{}].", UserPrincipalUtility.extractUserId(principal), toDelete);
    }


}
