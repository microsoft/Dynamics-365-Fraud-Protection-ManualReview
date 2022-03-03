// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.cosmos.utilities.IdUtility;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.model.ItemLabel;
import com.griddynamics.msd365fp.manualreview.model.ItemLock;
import com.griddynamics.msd365fp.manualreview.model.ItemNote;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.model.event.Event;
import com.griddynamics.msd365fp.manualreview.model.event.dfp.PurchaseEventBatch;
import com.griddynamics.msd365fp.manualreview.model.event.internal.ItemResolutionEvent;
import com.griddynamics.msd365fp.manualreview.model.event.type.LockActionType;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.model.exception.NotFoundException;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemDataField;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemEvent;
import com.griddynamics.msd365fp.manualreview.queues.model.dto.ItemDTO;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Queue;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.SearchQuery;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepository;
import com.griddynamics.msd365fp.manualreview.queues.repository.QueueRepository;
import com.griddynamics.msd365fp.manualreview.queues.repository.SearchQueryRepository;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemService {

    private final StreamService streamService;
    private final ItemRepository itemRepository;
    private final QueueRepository queueRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final ModelMapper modelMapper;

    @Setter(onMethod = @__({@Autowired}))
    private ItemService thisService;
    @Setter(onMethod = @__({@Value("${mr.items.unlock-timeout}")}))
    private Duration unlockTimeout;

    public void saveEmptyItem(PurchaseEventBatch eventBatch) {
        eventBatch.forEach(event -> {
            String purchaseId = event.getEventId();
            String itemId = IdUtility.encodeRestrictedChars(purchaseId);
            log.info("Event [{}] has been received from the DFP rule [{}].", purchaseId, event.getRuleName());

            if ("purchase".equalsIgnoreCase(event.getEventType())) {
                // Create and save the item
                Item item = Item.builder()
                        .id(itemId)
                        .active(false)
                        .imported(OffsetDateTime.now())
                        ._etag(UUID.randomUUID().toString())
                        .build();
                try {
                    itemRepository.save(item);
                    log.info("Item [{}] has been saved to the storage.", itemId);
                } catch (CosmosAccessException e) {
                    log.info("Item [{}] has not been saved to the storage because it's already exist.", itemId);
                } catch (Exception e) {
                    log.warn("Item [{}] has not been saved to the storage: {}", itemId, e.getMessage());
                }
            } else {
                log.info("The event type of [{}] is [{}]. The event has been ignored.", purchaseId, event.getEventType());
            }
        });
    }

    @Retry(name = "cosmosOptimisticUpdate")
    protected Optional<Item> unlockItem(final String itemId, LockActionType actionType) {
        // Get the item
        Iterator<Item> itemIterator = itemRepository
                .findByIdAndActiveTrueAndLock_OwnerIdNotNull(itemId)
                .iterator();

        // Delete the lock
        Optional<Item> unlockedItem;
        if (itemIterator.hasNext()) {
            Item item = itemIterator.next();
            ItemLock prevLock = SerializationUtils.clone(item.getLock());
            item.unlock();
            itemRepository.save(item);
            streamService.sendItemLockEvent(item, prevLock, actionType);
            log.info("Item with ID [{}] has been successfully unlocked. Previous lock owner [{}].",
                    item.getId(), prevLock.getOwnerId());
            return Optional.of(item);
        } else {
            log.warn("Failed to unlock item with ID [{}]. Check if the item ID exists, item is active and owner" +
                    "of the lock is not null.", itemId);
            unlockedItem = Optional.empty();
        }
        return unlockedItem;
    }

    /**
     * Reconciliation through all Queues and all items.
     * 1. Method finds all queues in the system
     * 2. For each queue the method searches for all improper
     * {@link Item#getQueueIds()} assignments through all active items.
     * 3. each found issue is corrected in accordance with {@link ItemService#updateQueueIdsForAllItemsRelatedTo}
     */
    public void reconcileAllItemAssignments() {
        Iterable<Queue> queues = queueRepository
                .findAll();

        log.info("Trying to update all item assignments in all queues.");
        updateQueueIdsForAllItemsRelatedTo(queues, null);
    }

    /**
     * Reconciliation for changed Queues.
     * 1. Method finds all queues that were updated since the specified time {@link OffsetDateTime}.
     * 2. For each found queue the method searches for related items
     * and updates {@link Item#getQueueIds()} in accordance
     * with {@link ItemService#updateQueueIdsForAllItemsRelatedTo}.
     *
     * @param since lower bound to query by {@link Queue#getCreated()} and {@link Queue#getDeleted()} fields
     */
    public void reconcileItemAssignmentsForChangedQueues(final OffsetDateTime since) throws BusyException {
        Collection<Queue> updatedQueues = PageProcessingUtility.getAllPages(
                continuation -> queueRepository.findQueuesCreatedOrDeletedAfter(since, DEFAULT_QUEUE_PAGE_SIZE, continuation));

        if (!updatedQueues.isEmpty()) {
            log.info("Trying to update item assignments in [{}] updated queues: [{}]", updatedQueues.size(),
                    updatedQueues.stream().map(Queue::getId).collect(Collectors.toList()));
            thisService.updateQueueIdsForAllItemsRelatedTo(updatedQueues, null);
        }
    }

    /**
     * Updates {@link Item#getQueueIds()} for the items contained by each of the specified queues
     * in accordance with {@link ItemService#updateQueueIdsForAllItemsRelatedTo}.
     *
     * @param queues set of queues for the updating
     * @param since  an optional lower bound to query by {@link Item#getEnriched()}
     */
    public void updateQueueIdsForAllItemsRelatedTo(
            @NonNull final Iterable<Queue> queues,
            @Nullable final OffsetDateTime since) {
        Set<String> updatedItemIds = new HashSet<>();
        queues.forEach(queue -> updatedItemIds.addAll(
                thisService.updateQueueIdsForAllItemsRelatedTo(queue, since)));
        if (updatedItemIds.isEmpty()) {
            log.info("All items assignments are up to date.");
        } else {
            log.info("Following items [{}] assignments were updated: [{}]", updatedItemIds.size(), updatedItemIds);
        }
    }

    /**
     * Update {@link Item#getQueueIds()} for all items related to the specified queue.
     * - For active queues items are tested against queue filters based on DB query. If
     * an item matches filters but doesn't have assignment to that queue then
     * the {@link Queue#getId()} will be added to {@link Item#getQueueIds()}
     * - For inactive queues items are tested on the keeping of {@link Queue#getId()}
     * in {@link Item#getQueueIds()}. If an assignment then it'll be removed
     *
     * @param queue a queue for the updating
     * @param since an optional lower bound to query by {@link Item#getEnriched()}
     * @return set of updated {@link Item#getId()}
     */
    @Retry(name = "cosmosOptimisticCapture")
    protected Set<String> updateQueueIdsForAllItemsRelatedTo(
            @NonNull final Queue queue,
            @Nullable final OffsetDateTime since) {
        Set<String> updatedItemIds = new HashSet<>();
        try {
            if (queue.isActive() && !CollectionUtils.isEmpty(queue.getFilters())) {
                log.info("Trying to update assignments for all items related to queue [{}].", queue.getId());
                PageProcessingUtility.executeForAllPages(
                        continuation -> itemRepository.findUnassignedItemsByItemFilters(
                                queue.getId(),
                                queue.getFilters(),
                                since,
                                DEFAULT_ITEM_PAGE_SIZE,
                                continuation,
                                new Sort.Order(queue.getSorting().getOrder(), queue.getSorting().getField().getPath()),
                                true),
                        itemCollection -> {
                            HashSet<Item> items = new HashSet<>(itemCollection.getValues());
                            items.forEach(item -> {
                                HashSet<String> oldQueueIds = new HashSet<>(item.getQueueIds());
                                item.getQueueIds().add(queue.getId());
                                itemRepository.save(item);
                                streamService.sendItemAssignmentEvent(item, oldQueueIds);
                            });
                            updatedItemIds.addAll(items.stream().map(Item::getId).collect(Collectors.toSet()));
                        });
            } else if (!queue.isActive()) {
                log.info("Trying to delete assignments for all items related to [{}] deactivated queue.",
                        queue.getId());
                PageProcessingUtility.executeForAllPages(
                        continuation -> itemRepository.findActiveItemsRelatedToQueue(
                                queue.getId(),
                                DEFAULT_ITEM_PAGE_SIZE,
                                continuation),
                        itemCollection -> {
                            HashSet<Item> items = new HashSet<>(itemCollection.getValues());
                            items.forEach(item -> {
                                Item oldItem = SerializationUtils.clone(item);
                                boolean unassigned = false;
                                if (item.getQueueIds() != null && item.getQueueIds().contains(queue.getId())) {
                                    item.getQueueIds().remove(queue.getId());
                                    unassigned = true;
                                }
                                boolean unlocked = false;
                                if (item.getLock() != null && queue.getId().equals(item.getLock().getQueueId())) {
                                    item.unlock();
                                    unlocked = true;
                                }
                                if (item.getHold() != null && queue.getId().equals(item.getHold().getQueueId())) {
                                    item.setHold(null);
                                    item.setLabel(new ItemLabel());
                                    item.getNotes().add(ItemNote.builder()
                                            .created(OffsetDateTime.now())
                                            .note(String.format("# Hold released due to queue [%s] deletion", queue.getId()))
                                            .userId(oldItem.getHold().getOwnerId())
                                            .build());
                                }
                                if (item.getEscalation() != null && queue.getId().equals(item.getEscalation().getQueueId())) {
                                    item.setEscalation(null);
                                    item.setLabel(new ItemLabel());
                                    item.getNotes().add(ItemNote.builder()
                                            .created(OffsetDateTime.now())
                                            .note(String.format("# Escalation released due to queue [%s] deletion", queue.getId()))
                                            .userId(oldItem.getEscalation().getReviewerId())
                                            .build());
                                }
                                itemRepository.save(item);
                                if (unassigned) {
                                    streamService.sendItemAssignmentEvent(item, oldItem.getQueueIds());
                                }
                                if (unlocked) {
                                    streamService.sendItemLockEvent(item, oldItem.getLock(), LockActionType.DELETION_RELEASE);
                                }
                            });
                        });
            } else {
                if (queue.isResidual()) {
                    log.info("Queue [{}] will have assignments updated in a separate process.", queue.getId());
                } else {
                    log.warn("Queue [{}] doesn't have filters and it's not a [{}]. " +
                                    "Thus it can not have assignments and should be deactivated.",
                            queue.getId(), RESIDUAL_QUEUE_NAME);
                }
            }
        } catch (BusyException e) {
            log.warn("Couldn't update queueIds for items in changed queue [{}]. Server is too busy.", queue.getId());
        }
        return updatedItemIds;
    }

    /**
     * Reconciliation for new Items.
     * 1. Method counts all items were enriched since the specified time {@link OffsetDateTime}.
     * 2. If new items exists then for each active queue the method searches
     * for new related items and updates their {@link Item#getQueueIds()}
     * in accordance with {@link ItemService#updateQueueIdsForAllItemsRelatedTo}.
     *
     * @param since lower bound to query by {@link Item#getEnriched()}
     */
    public void reconcileAssignmentsForNewItems(final OffsetDateTime since) throws BusyException {
        Integer count = itemRepository.countActiveItemsUpdatedAfter(since);

        if (count > 0) {
            Iterable<Queue> allActiveQueuesWithFilters = PageProcessingUtility.getAllPages(
                    continuation -> queueRepository.getQueueList(true, false, DEFAULT_QUEUE_PAGE_SIZE, continuation));

            log.info("Trying to update assignments for around [{}] recently updated items.", count);
            thisService.updateQueueIdsForAllItemsRelatedTo(allActiveQueuesWithFilters, since);
        }
    }

    /**
     * Initializes resolution sending.
     *
     * @return list of initialized sents
     */
    public boolean sendResolutions() throws BusyException {
        log.info("Start resolution sending.");
        PageProcessingUtility.executeForAllPages(
                continuation -> itemRepository.findUnreportedItems(
                        DEFAULT_ITEM_PAGE_SIZE,
                        continuation),
                itemCollection -> {
                    Set<ImmutablePair<String, ItemEvent>> eventCollection = itemCollection.stream()
                            .flatMap(item -> item.getEvents().stream()
                                    .filter(event -> ItemResolutionEvent.class.equals(event.getKlass()))
                                    .map(event -> new ImmutablePair<>(item.getId(), event)))
                            .collect(Collectors.toSet());
                    log.info("Start resolution sending for [{}].", eventCollection);
                    Set<Mono<Void>> executions = eventCollection.stream()
                            .map(eventTuple -> streamService.sendItemResolvedEvent(eventTuple.getRight().getEvent())
                                    .doOnSuccess(v -> thisService.deleteEventFromItem(eventTuple.left, eventTuple.right.getSendingId())))
                            .collect(Collectors.toSet());
                    Mono.zipDelayError(executions, r -> r)
                            .subscribeOn(Schedulers.boundedElastic())
                            .subscribe();
                });
        return true;
    }

    @Retry(name = "cosmosOptimisticUpdate")
    protected void deleteEventFromItem(String itemId, String sendingId) {
        Optional<Item> itemOptional = itemRepository.findById(itemId);
        itemOptional.ifPresent(item -> {
            if (item.getEvents().removeIf(event -> sendingId.equals(event.getSendingId()))) {
                itemRepository.save(item);
                log.info("Event [{}] were reported from item [{}].", sendingId, itemId);
            }
        });
    }

    /**
     * Unlocks all items with expired lock timestamps.
     *
     * @return list of unlocked items
     */
    public List<Item> unlockItemsByTimeout() {
        log.info("Trying to unlock items by timeout.");
        OffsetDateTime unlockTs = OffsetDateTime.now().minus(unlockTimeout);
        List<Item> itemsToUnlock = itemRepository.findByActiveTrueAndLock_LockedBefore(unlockTs.toEpochSecond());
        List<Item> unlockedItems = itemsToUnlock.stream()
                .map(item -> thisService.unlockItem(item.getId(), LockActionType.TIMEOUT_RELEASE))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        log.info("Unlocked items by timeout: [{}]",
                unlockedItems.stream().map(Item::getId).collect(Collectors.toSet()));
        return unlockedItems;
    }

    public int countActiveItems() {
        return itemRepository.countActiveItems();
    }

    public PageableCollection<ItemDTO> searchForItems(
            final String searchQueryId,
            final int pageSize,
            @Nullable final String continuationToken,
            final ItemDataField sortingField,
            final Sort.Direction sortingDirection
    ) throws BusyException, NotFoundException {
        SearchQuery searchQuery = searchQueryRepository.findById(searchQueryId)
                .orElseThrow(() -> new NotFoundException(String.format("Search Query not found for id [%s]", searchQueryId)));

        PageableCollection<Item> queriedItems = PageProcessingUtility.getNotEmptyPage(
                continuationToken,
                continuation -> itemRepository.searchForItems(
                        searchQuery.getIds(),
                        searchQuery.getOriginalOrderIds(),
                        searchQuery.getQueueIds(),
                        searchQuery.isResidual(),
                        searchQuery.getActive(),
                        searchQuery.getItemFilters(),
                        searchQuery.getLockOwnerIds(),
                        searchQuery.getHoldOwnerIds(),
                        searchQuery.getLabels(),
                        searchQuery.getLabelAuthorIds(),
                        sortingField,
                        sortingDirection,
                        searchQuery.getTags(),
                        pageSize,
                        continuation)
        );
        return new PageableCollection<>(
                queriedItems.getValues()
                        .stream()
                        .map(item -> modelMapper.map(item, ItemDTO.class))
                        .collect(Collectors.toList()),
                queriedItems.getContinuationToken());
    }

}
