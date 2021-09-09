// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.dfpauth.util.UserPrincipalUtility;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.model.exception.EmptySourceException;
import com.griddynamics.msd365fp.manualreview.model.exception.NotFoundException;
import com.griddynamics.msd365fp.manualreview.queues.model.*;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Queue;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepository;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class PublicItemClient {

    private final ItemRepository itemRepository;
    private final DataSecurityService dataSecurityService;


    @PostFilter("@dataSecurityService.checkPermissionForItemReading(authentication, filterObject, #queueView)")
    public PageableCollection<Item> getQueueViewItemList(
            @NonNull QueueView queueView,
            int pageSize,
            @Nullable String continuationToken,
            @Nullable final ItemDataField sortingField,
            @Nullable final Sort.Direction sortingDirection) throws BusyException {

        Sort.Order sort = new Sort.Order(queueView.getSorting().getOrder(), queueView.getSorting().getField().getPath());

        if(sortingField!=null && sortingDirection!=null)
        {
            sort = new Sort.Order(sortingDirection, sortingField.getPath());
        }

        Sort.Order finalSort = sort;
        return PageProcessingUtility.getNotEmptyPage(
                continuationToken,
                continuation -> {
                    if (queueView.isResidual()) {
                        return itemRepository.findActiveItemsByQueueIdsEmpty(
                                queueView.getViewType(),
                                pageSize,
                                continuation,
                                finalSort,
                                null,
                                null);
                    } else {
                        return itemRepository.findActiveItemsByQueueView(
                                queueView.getViewType(),
                                queueView.getQueueId(),
                                pageSize,
                                continuation,
                                finalSort,
                                null,
                                null);
                    }
                });
    }


    @PostFilter("@dataSecurityService.checkPermissionForItemReading(authentication, filterObject, #queues)")
    public Collection<BasicItemInfo> getItemInfoByIds(
            @NonNull Set<String> ids,
            @Nullable Collection<Queue> queues) throws BusyException {
        return PageProcessingUtility.getAllPages(
                continuation -> itemRepository.findEnrichedItemInfoByIds(ids, DEFAULT_ITEM_INFO_PAGE_SIZE, continuation));
    }

    @PostFilter("@dataSecurityService.checkPermissionForItemReading(authentication, filterObject, #queues)")
    public Collection<Item> getItemListByIds(
            @NonNull Set<String> ids,
            @Nullable Collection<Queue> queues) throws BusyException {
        return PageProcessingUtility.getAllPages(
                continuation -> itemRepository.findEnrichedItemsByIds(ids, DEFAULT_ITEM_PAGE_SIZE, continuation));
    }




    @PostFilter("@dataSecurityService.checkPermissionForItemReading(authentication, filterObject, #queueView)")
    public PageableCollection<Item> getLockedItemPageableList(
            @Nullable String ownerId,
            @Nullable QueueView queueView,
            int pageSize,
            @Nullable String continuationToken) throws BusyException {
        return PageProcessingUtility.getNotEmptyPage(
                continuationToken,
                continuation -> itemRepository.findLockedItems(
                        ownerId,
                        queueView == null ? null : queueView.getViewId(),
                        pageSize,
                        continuation));
    }


    @PostFilter("@dataSecurityService.checkPermissionForItemReading(authentication, filterObject, #queueView)")
    public PageableCollection<Item> getUrgentItemPageableList(
            @NonNull QueueView queueView,
            @Nullable OffsetDateTime importedBefore,
            @Nullable OffsetDateTime lockedBefore,
            int pageSize,
            @Nullable String continuationToken) throws BusyException {
        return PageProcessingUtility.getNotEmptyPage(
                continuationToken,
                continuation -> itemRepository.findUrgentItems(
                        queueView.getViewType(),
                        queueView.getQueueId(),
                        importedBefore,
                        lockedBefore,
                        pageSize,
                        continuation));
    }


    @PostAuthorize("@dataSecurityService.checkPermissionForItemReading(authentication, returnObject, #queueView)")
    public Item getItem(
            @NonNull String id,
            @Nullable QueueView queueView,
            @Nullable Boolean active) throws NotFoundException {
        return itemRepository
                .findItemById(
                        id,
                        active,
                        queueView == null ? null : queueView.getViewType(),
                        queueView == null ? null : queueView.getQueueId(),
                        queueView == null ? null : queueView.isResidual())
                .orElseThrow(() -> new NotFoundException(MESSAGE_ITEM_NOT_FOUND));
    }


    @PostAuthorize("@dataSecurityService.checkPermissionForItemReading(authentication, returnObject, #queueView)")
    public Item getFirstFreeActiveItem(@NonNull QueueView queueView) throws BusyException, EmptySourceException {
        PageableCollection<Item> result;
        if (queueView.isResidual()) {
            result = PageProcessingUtility.getNotEmptyPage(
                    TOP_ELEMENT_IN_CONTAINER_CONTINUATION,
                    continuation -> itemRepository.findActiveItemsByQueueIdsEmpty(
                            queueView.getViewType(),
                            TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE,
                            continuation,
                            new Sort.Order(queueView.getSorting().getOrder(), queueView.getSorting().getField().getPath()),
                            false,
                            false));
        } else {
            result = PageProcessingUtility.getNotEmptyPage(
                    TOP_ELEMENT_IN_CONTAINER_CONTINUATION,
                    continuation -> itemRepository.findActiveItemsByQueueView(
                            queueView.getViewType(),
                            queueView.getQueueId(),
                            TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE,
                            continuation,
                            new Sort.Order(queueView.getSorting().getOrder(), queueView.getSorting().getField().getPath()),
                            false,
                            false));
        }
        if (result.getSize() < 1) throw new EmptySourceException();
        return result.getValues().iterator().next();
    }


    @PreAuthorize("@dataSecurityService.checkPermissionForItemUpdate(authentication, #oldVersion)")
    public void updateItem(@NonNull Item newVersion, @NonNull Item oldVersion) {
        itemRepository.save(newVersion);
        if (newVersion.getLock().getOwnerId() == null && oldVersion.getLock().getOwnerId() != null) {
            log.info("Item [{}] has been unlocked from queue [{}] by [{}].",
                    newVersion.getId(), oldVersion.getLock().getQueueId(), oldVersion.getLock().getOwnerId());
        } else if (!newVersion.isActive() && newVersion.getLabel().getValue() != null
                && newVersion.getLabel().getValue().isFormsResolution()) {
            log.info("Item [{}] has been labeled with resolution label [{}] in queue [{}] by [{}].",
                    newVersion.getId(), newVersion.getLabel().getValue(), newVersion.getLabel().getQueueId(),
                    newVersion.getLabel().getAuthorId());
        } else if (newVersion.getEscalation() != null) {
            log.info("Item [{}] has been labeled with label [{}] in queue [{}] by [{}].",
                    newVersion.getId(), newVersion.getLabel().getValue(), newVersion.getLabel().getQueueId(),
                    newVersion.getLabel().getAuthorId());
        } else if (newVersion.getTags().size() > oldVersion.getTags().size()) {
            log.info("Item [{}] acquired new tag: [{}]",
                    newVersion.getId(), CollectionUtils.subtract(newVersion.getTags(), oldVersion.getTags()));
        } else if (newVersion.getTags().size() < oldVersion.getTags().size()) {
            log.info("Item [{}] tag has been removed: [{}]",
                    newVersion.getId(), CollectionUtils.subtract(oldVersion.getTags(), newVersion.getTags()));
        } else if (newVersion.getNotes().size() > oldVersion.getTags().size()) {
            log.info("Item [{}] acquired new note: [{}]",
                    newVersion.getId(), CollectionUtils.subtract(newVersion.getNotes(), oldVersion.getNotes()));
        } else if (newVersion.getNotes().size() < oldVersion.getTags().size()) {
            log.info("Item [{}] note has been removed: [{}]",
                    newVersion.getId(), CollectionUtils.subtract(oldVersion.getNotes(), newVersion.getNotes()));
        } else {
            log.info("Item [{}] has been modified.", newVersion.getId());
        }
    }


    public <T> Collection<BatchUpdateResult> batchUpdate(
            @NonNull Set<String> ids,
            @Nullable Collection<Queue> queues,
            @NonNull Function<Item, T> modifier,
            @NonNull BiConsumer<Item, T> postprocessor) throws BusyException {
        Map<String, BatchUpdateResult> results = new HashMap<>();
        PageProcessingUtility.executeForAllPages(continuationToken ->
                        itemRepository.findEnrichedItemsByIds(ids, DEFAULT_ITEM_PAGE_SIZE, continuationToken),
                items -> {
                    for (Item item : items) {
                        String check = dataSecurityService.checkPermissionRestrictionForItemUpdateWithoutLock(UserPrincipalUtility.getAuth(), item, queues);
                        if (check != null) {
                            results.put(item.getId(), new BatchUpdateResult(item.getId(), false, check));
                        } else {
                            try {
                                T context = modifier.apply(item);
                                item = itemRepository.save(item);
                                postprocessor.accept(item, context);
                                results.put(item.getId(), new BatchUpdateResult(item.getId(), true, "Successfully updated."));
                                log.info("Item [{}] has been modified in batch operation.", item.getId());
                            } catch (CosmosAccessException e) {
                                results.put(item.getId(), new BatchUpdateResult(item.getId(), false, "Item has been modified by another process."));
                            } catch (Exception e) {
                                log.warn("Exception during bulk operation for item [{}]: {}", item.getId(), e.getMessage());
                                log.warn("Exception during bulk operation for item [{}]", item.getId(), e);
                                results.put(item.getId(), new BatchUpdateResult(item.getId(), false, "Internal exception."));
                            }
                        }
                    }
                }
        );


        ids.stream()
                .filter(id -> !results.containsKey(id))
                .forEach(id -> results.put(id, new BatchUpdateResult(id, false, "Item not found.")));

        return results.values();

    }


    @PreAuthorize("@dataSecurityService.checkPermissionForItemLock(authentication, #item, #queueView)")
    public void lockItem(@NonNull QueueView queueView, @NonNull Item item) {
        item.lock(queueView.getQueueId(), queueView.getViewId(), UserPrincipalUtility.getUserId());
        itemRepository.save(item);
        log.info("Item [{}] has been locked in queue view [{}].", item.getId(), queueView.getViewId());
    }


    @PreFilter("@dataSecurityService.checkPermissionForQueueViewReading(authentication, filterObject)")
    public void recalculateQueueViewSizes(Collection<QueueView> queueViews) {
        Map<QueueViewType, List<QueueView>> listsByViewTypes = queueViews.stream()
                .collect(Collectors.groupingBy(QueueView::getViewType));

        listsByViewTypes.forEach((type, queueViewSubList) -> {

            Set<QueueView> assignableQueues = queueViewSubList.stream().filter(q -> !q.isResidual()).collect(Collectors.toSet());
            if (!assignableQueues.isEmpty()) {
                Map<String, Integer> sizes = itemRepository.countQueueViewSizes(
                        assignableQueues.stream().map(QueueView::getQueueId).collect(Collectors.toSet()),
                        type);
                assignableQueues.forEach(queueView -> {
                    Integer newSize = sizes.getOrDefault(queueView.getQueueId(), 0);
                    log.debug("Revising size of queue view [{}] from [{}] to [{}].",
                            queueView.getViewId(), queueView.getSize(), newSize);
                    queueView.setSize(newSize);
                });
            }

            Set<QueueView> residualQueues = queueViewSubList.stream().filter(QueueView::isResidual).collect(Collectors.toSet());
            if (!residualQueues.isEmpty()) {
                int size = itemRepository.countResidualQueueViewSize(type);
                residualQueues.forEach(queue -> {
                    log.debug("Revising size of the residual queue [{}] from [{}] to [{}].",
                            RESIDUAL_QUEUE_NAME, queue.getSize(), size);
                    queue.setSize(size);
                });
            }

        });

    }

    @PreAuthorize("@dataSecurityService.checkPermissionForQueueViewReading(authentication, #queueView)")
    public Integer countItemsImportedBeforeByQueue(
            QueueView queueView,
            OffsetDateTime importedBefore) {
        return itemRepository.countItemsImportedBeforeByQueue(queueView.getQueueId(), importedBefore);
    }

    @PreAuthorize("@dataSecurityService.checkPermissionForQueueViewReading(authentication, #queueView)")
    public Integer countItemsLockedBeforeByQueue(
            QueueView queueView,
            OffsetDateTime lockedBefore) {
        return itemRepository.countItemsLockedBeforeByQueue(queueView.getQueueId(), lockedBefore);
    }

    @PreFilter("@dataSecurityService.checkPermissionForQueueViewReading(authentication, filterObject)")
    public Map<String, Long> countLockedItemsPerQueues(Collection<QueueView> queueViews) {
        return itemRepository.countLockedItemsPerQueues(queueViews.stream().map(QueueView::getQueueId).collect(Collectors.toSet()));
    }
}
