// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueViewType;
import com.griddynamics.msd365fp.manualreview.queues.model.Bucket;
import com.griddynamics.msd365fp.manualreview.queues.model.dto.RiskScoreOverviewDTO;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Queue;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepository;
import com.griddynamics.msd365fp.manualreview.queues.repository.QueueRepository;
import com.griddynamics.msd365fp.manualreview.queues.util.QueueViewUtility;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final ItemRepository itemRepository;
    private final UserService userService;
    private final StreamService streamService;

    @Setter(onMethod = @__({@Autowired}))
    private QueueService thisService;

    /**
     * The residual queue reconciliation.
     * The method gets list of active residual queues and the list of managers.
     * Then if there is no residual queues then method creates one.
     * If residual queues exist then the method assign managers to each residual
     * queue if they aren't assigned yet.
     */
    public boolean reviseResidualQueue() throws BusyException {
        log.info("Starting reconciliation procedure for the residual queue.");
        Set<String> managers = new HashSet<>(userService.getActiveUserIds(Set.of(ADMIN_MANAGER_ROLE)));
        Collection<Queue> residualQueues = PageProcessingUtility.getAllPages(
                continuation -> queueRepository.getQueueList(
                        true, true, DEFAULT_QUEUE_PAGE_SIZE, continuation));
        if (residualQueues.isEmpty()) {
            log.info("No residual queue were found. A new one will be created with [{}] name.", RESIDUAL_QUEUE_NAME);
            Queue newResidualQueue = Queue.builder()
                    .name(RESIDUAL_QUEUE_NAME)
                    .id(UUID.randomUUID().toString())
                    .active(true)
                    .created(OffsetDateTime.now())
                    .allowedLabels(Set.of(Label.GOOD, Label.BAD, Label.WATCH_NA,
                            Label.WATCH_INCONCLUSIVE))
                    .supervisors(managers)
                    .residual(true)
                    .build();
            QueueViewUtility.addViewToQueue(newResidualQueue, QueueViewType.REGULAR);
            queueRepository.save(newResidualQueue);
            streamService.sendQueueUpdateEvent(newResidualQueue);
            log.info("New [{}] with ID [{}] has been created.", RESIDUAL_QUEUE_NAME, newResidualQueue.getId());
        } else if (!managers.isEmpty()) {
            for (Queue queue : residualQueues) {
                Set<String> assignments = SetUtils.union(
                        Objects.requireNonNullElse(queue.getReviewers(), Collections.emptySet()),
                        Objects.requireNonNullElse(queue.getSupervisors(), Collections.emptySet()));
                Set<String> unassignedManagers = SetUtils.difference(managers, assignments);
                if (!unassignedManagers.isEmpty()) {
                    Set<String> updatedSupervisors = SetUtils.union(
                            unassignedManagers,
                            Objects.requireNonNullElse(queue.getSupervisors(), Collections.emptySet()));
                    queue.setSupervisors(updatedSupervisors);
                    queueRepository.save(queue);
                    streamService.sendQueueUpdateEvent(queue);
                    log.info("Managers [{}] have been assigned as supervisors to [{}] with ID [{}].",
                            unassignedManagers, RESIDUAL_QUEUE_NAME, queue.getId());
                }
            }
        }
        return true;
    }

    /**
     * The queue assignments reconciliation.
     * The method gets list of active users and delete
     * from queues those assignments that contain deleted users.
     * @return ids of reconciled queues
     */
    public List<String> reconcileQueueAssignments() throws BusyException {
        log.info("Starting reconciliation procedure for queue assignments.");
        Set<String> users = new HashSet<>(userService.getActiveUserIds(Set.of(USER_ROLES_ALLOWED_FOR_QUEUE_PROCESSING)));
        Set<String> managers = new HashSet<>(userService.getActiveUserIds(Set.of(ADMIN_MANAGER_ROLE)));
        Collection<Queue> queues = PageProcessingUtility.getAllPages(
                continuation -> queueRepository.getQueueList(
                        true, null, DEFAULT_QUEUE_PAGE_SIZE, continuation));
        return queues.stream()
                .filter(queue -> thisService.reconcileQueueAssignments(queue, users, managers))
                .map(Queue::getId)
                .collect(Collectors.toList());

    }

    @Retry(name = "cosmosOptimisticUpdate")
    protected boolean reconcileQueueAssignments(Queue queue, Set<String> users, Set<String> managers) {
        boolean needToUpdate = false;
        if (CollectionUtils.isNotEmpty(queue.getReviewers())) {
            int size = queue.getReviewers().size();
            queue.getReviewers().retainAll(users);
            needToUpdate = size != queue.getReviewers().size();
        }
        if (CollectionUtils.isNotEmpty(queue.getSupervisors())) {
            int size = queue.getSupervisors().size();
            queue.getSupervisors().retainAll(users);
            needToUpdate = needToUpdate || (size != queue.getSupervisors().size());
        }
        if (CollectionUtils.isEmpty(queue.getSupervisors())) {
            queue.setSupervisors(managers);
            queue.getReviewers().removeAll(managers);
            needToUpdate = true;
        }
        if (needToUpdate) {
            log.info("The queue [{}] were updated in order to reconcile assignments.", queue.getId());
            queueRepository.save(queue);
            streamService.sendQueueUpdateEvent(queue);
        }
        return needToUpdate;
    }

    /**
     * Extracts count of items that apply for {@link Queue#getFilters()} and updates all active queues with
     * new {@link Queue#getSize}. Then update size for for the residual queue by queueIds in items.
     *
     * @return all IDs of queues with fetched {@link Queue#getSize} field
     */
    public Map<String, Integer> fetchSizesForQueues() throws BusyException {
        log.info("Fetching sizes for all active queues.");
        List<Queue> updatedQueues = PageProcessingUtility
                .getAllPages(continuation ->
                        queueRepository.getQueueList(
                                true, null, DEFAULT_QUEUE_PAGE_SIZE, continuation))
                .parallelStream()
                .flatMap(queue -> {
                    if (queue.isResidual()) {
                        queue.setSize(itemRepository.countActiveItemsByQueueIdsEmpty());
                    } else {
                        queue.setSize(itemRepository.countActiveItemsByItemFilters(queue.getFilters()));
                    }
                    try {
                        Queue updatedQueue = queueRepository.save(queue);
                        streamService.sendQueueSizeEvent(updatedQueue);
                        return Stream.of(queue);
                    } catch (CosmosAccessException e) {
                        log.warn("Size of the queue [{}] has not been updated due to optimistic lock conflict.",
                                queue.getId());
                        return Stream.empty();
                    }
                })
                .collect(Collectors.toList());
        return updatedQueues.stream().collect(Collectors.toMap(Queue::getId, Queue::getSize));
    }

    public RiskScoreOverviewDTO getRiskScoreOverview(int bucketSize, String queueId) {
        return new RiskScoreOverviewDTO(itemRepository.getRiskScoreDistribution(bucketSize, queueId)
                .collect(
                        Collectors.groupingBy(
                                Bucket::getLowerBound,
                                Collector.of(
                                        RiskScoreOverviewDTO.RiskScoreBucketDTO::new,
                                        (r, t) -> r.setCount(r.getCount() + t.getCount()),
                                        (r, b) -> {
                                            r.setCount(r.getCount() + b.getCount());
                                            return r;
                                        })
                        )
                )
        );
    }
}
