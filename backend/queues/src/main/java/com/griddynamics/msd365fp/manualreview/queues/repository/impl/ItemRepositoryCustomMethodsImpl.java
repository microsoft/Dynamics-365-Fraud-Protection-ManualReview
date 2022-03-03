// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.queues.model.*;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepositoryCustomMethods;
import com.griddynamics.msd365fp.manualreview.queues.validation.FieldConditionCombination;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.TOP_ELEMENT_IN_CONTAINER_CONTINUATION;
import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE;

/**
 * Custom queries implementation.
 * In accordance with:
 * - https://stackoverflow.com/questions/22866473/how-to-implement-only-specific-method-of-crudrepository-in-spring
 * - https://docs.microsoft.com/en-us/azure/developer/java/spring-framework/how-to-guides-spring-data-cosmosdb
 */
@Slf4j
@RequiredArgsConstructor
public class ItemRepositoryCustomMethodsImpl implements ItemRepositoryCustomMethods {

    @Qualifier("itemsContainer")
    private final ExtendedCosmosContainer itemsContainer;

    @Override
    public Map<String, Integer> countQueueViewSizes(@NonNull final Set<String> queueIds,
                                                    @NonNull final QueueViewType viewType) {
        Map<String, Integer> result = new HashMap<>();
        itemsContainer.runCrossPartitionQuery(
                String.format("SELECT VALUE root FROM (" +
                                "SELECT qid, count(1) As cnt FROM i " +
                                "JOIN (SELECT VALUE queueId FROM queueId IN i.queueIds WHERE queueId IN ('%s')) qid " +
                                "WHERE %s " +
                                "GROUP BY qid) " +
                                "AS root",
                        String.join("','", queueIds),
                        viewType.getQueryCondition()))
                .forEach(cip ->
                        result.compute(cip.get("qid").asText(), (key, val) -> {
                            int cnt = cip.get("cnt").asInt();
                            if (val == null) return cnt;
                            else return val + cnt;
                        }));
        return result;
    }


    @Override
    public int countResidualQueueViewSize(@NonNull final QueueViewType viewType) {
        return ItemQuery.constructor("i")
                .queueIdsAreEmpty()
                .and()
                .customQueryCondition(viewType.getQueryCondition())
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public PageableCollection<Item> findActiveItemsByQueueIdsEmpty(
            @NonNull final QueueViewType viewType,
            final int size,
            @Nullable final String continuationToken,
            @NonNull final Sort.Order order,
            @Nullable final Boolean locked,
            @Nullable final Boolean held) {

        return ItemQuery.constructor("i")
                .active(true)
                .and().queueIdsAreEmpty()
                .and().locked(locked)
                .and().held(held)
                .and().customQueryCondition(viewType.getQueryCondition())
                .order(order)
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public PageableCollection<Item> findUnreportedItems(
            final int size,
            @Nullable final String continuationToken) {
        return ItemQuery.constructor("i")
                .hasEvents()
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public int countActiveItems() {
        return ItemQuery.constructor("i")
                .active(true)
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public int countActiveItemsByQueueIdsEmpty() {
        return ItemQuery.constructor("i")
                .active(true)
                .and().queueIdsAreEmpty()
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public PageableCollection<Item> findActiveItemsRelatedToQueue(
            final String queueId,
            final Integer size,
            final String continuationToken) {
        return ItemQuery.constructor("i")
                .queueId(queueId)
                .or().lockedInQueue(queueId)
                .or().heldInQueue(queueId)
                .or().escalatedInQueue(queueId)
                .and().active(true)
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public PageableCollection<Item> findActiveItemsByQueueView(
            @NonNull final QueueViewType viewType,
            @NonNull final String queueId,
            final int size,
            @Nullable final String continuationToken,
            @NonNull final Sort.Order order,
            @Nullable final Boolean locked,
            @Nullable final Boolean held) {
        return ItemQuery.constructor("i")
                .queueId(queueId)
                .and().active(true)
                .and().locked(locked)
                .and().held(held)
                .and().customQueryCondition(viewType.getQueryCondition())
                .order(order)
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public PageableCollection<String> findActiveItemIds(
            final int size,
            final String continuationToken) {
        ExtendedCosmosContainer.Page res = itemsContainer.runCrossPartitionPageableQuery(
                "SELECT i.id FROM i WHERE i.active ORDER BY i._ts",
                size,
                continuationToken);
        List<String> queriedItems = res.getContent()
                .map(cip -> Optional.ofNullable((String) cip.get("id").asText()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new PageableCollection<>(queriedItems, res.getContinuationToken());
    }

    @Override
    public PageableCollection<String> findUnenrichedItemIds(
            final int size,
            final String continuationToken) {
        return findUnenrichedItemIds(OffsetDateTime.now(), size, continuationToken);
    }

    @Override
    public PageableCollection<String> findUnenrichedItemIds(
            final OffsetDateTime importedUpperBoundary,
            final int size,
            final String continuationToken) {
        ExtendedCosmosContainer.Page res = itemsContainer.runCrossPartitionPageableQuery(
                String.format("SELECT i.id FROM i WHERE IS_NULL(i.enriched) " +
                                "AND (" +
                                "   NOT IS_DEFINED(i.enrichmentFailed) " +
                                "   OR IS_NULL(i.enrichmentFailed) " +
                                "   OR NOT i.enrichmentFailed " +
                                ") AND i.imported <= %s " +
                                "ORDER BY i._ts",
                        importedUpperBoundary.toEpochSecond()),
                size,
                continuationToken);
        List<String> queriedItems = res.getContent()
                .map(cip -> Optional.ofNullable((String) cip.get("id").asText()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new PageableCollection<>(queriedItems, res.getContinuationToken());
    }

    @Override
    public PageableCollection<BasicItemInfo> findEnrichedItemInfoByIds(
            @NonNull final Set<String> ids,
            final int size,
            @Nullable final String continuationToken) {
        ExtendedCosmosContainer.Page res = itemsContainer.runCrossPartitionPageableQuery(
                "SELECT i.id, i.imported, i.enriched, i.active, " +
                        "i.label, i.queueIds, i.lock, i.escalation, i.hold " +
                        "FROM i WHERE IS_DEFINED(i.enriched) AND NOT IS_NULL(i.enriched) " +
                        "AND i.id IN ('" +
                        String.join("','", ids) + "')",
                size,
                continuationToken);
        List<BasicItemInfo> queriedItems = res.getContent()
                .map(cip -> itemsContainer.castCosmosObjectToClassInstance(cip, Item.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new PageableCollection<>(queriedItems, res.getContinuationToken());
    }

    @Override
    public PageableCollection<Item> findEnrichedItemsByIds(
            @NonNull final Set<String> ids,
            final int size,
            @Nullable final String continuationToken) {
        ExtendedCosmosContainer.Page res = itemsContainer.runCrossPartitionPageableQuery(
                "SELECT i " +
                        "FROM i WHERE IS_DEFINED(i.enriched) AND NOT IS_NULL(i.enriched) " +
                        "AND i.id IN ('" +
                        String.join("','", ids) + "')",
                size,
                continuationToken);
        List<Item> queriedItems = res.getContent()
                .map(cip -> itemsContainer.castCosmosObjectToClassInstance(cip.get("i"), Item.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        return new PageableCollection<>(queriedItems, res.getContinuationToken());
    }

    @Override
    public Optional<Item> findItemById(
            @NonNull final String id,
            @Nullable final Boolean active,
            @Nullable final QueueViewType viewType,
            @Nullable final String queueId,
            @Nullable final Boolean queueIsResidual) {
        ItemQuery.ItemQueryConstructor constructor = ItemQuery.constructor("i");
        constructor.id(id);
        if (active != null) constructor.and().active(active);
        if (viewType != null) constructor.and().customQueryCondition(viewType.getQueryCondition());
        if (queueId != null) {
            if (queueIsResidual != null && queueIsResidual) {
                constructor.and().queueIdsAreEmpty();
            } else {
                constructor.and().queueId(queueId);
            }
        }

        return constructor
                .constructSelectExecutor(itemsContainer)
                .execute(TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE, TOP_ELEMENT_IN_CONTAINER_CONTINUATION)
                .stream().findFirst();
    }

    @Override
    public PageableCollection<Item> findLockedItems(
            @Nullable final String ownerId,
            @Nullable final String queueViewId,
            @NonNull final Integer size,
            @Nullable final String continuationToken) {
        String query = String.format("SELECT i FROM i " +
                        "WHERE i.active=true " +
                        "AND NOT IS_NULL(i.lock.ownerId) " +
                        "AND %s " +
                        "AND %s " +
                        "ORDER BY i._ts ASC",
                ownerId == null ? "true" : String.format("i.lock.ownerId='%s'", ownerId),
                queueViewId == null ? "true" : String.format("i.lock.queueViewId='%s'", queueViewId));
        ExtendedCosmosContainer.Page res =
                itemsContainer.runCrossPartitionPageableQuery(query, size, continuationToken);
        List<Item> lockedItems = res.getContent()
                .map(cip -> itemsContainer.castCosmosObjectToClassInstance(cip.get("i"), Item.class))
                .flatMap(Optional::stream)
                .collect(Collectors.toList());
        return new PageableCollection<>(lockedItems, res.getContinuationToken());
    }


    @Override
    public PageableCollection<Item> findUnassignedItemsByItemFilters(
            final String id,
            final Set<ItemFilter> itemFilters,
            final OffsetDateTime enrichedSince,
            final int size,
            final String continuationToken,
            final Sort.Order order,
            final boolean includeLocked) {
        return ItemQuery.constructor("i")
                .all(itemFilters)
                .and().not().queueId(id)
                .and().active(true)
                .and().notEscalation()
                .and().updatedAfter(enrichedSince)
                .and().includeLocked(includeLocked)
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public Integer countActiveItemsUpdatedAfter(final OffsetDateTime time) {
        return ItemQuery.constructor("i")
                .active(true)
                .and().updatedAfter(time)
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public Integer countActiveItemsByItemFilters(Set<ItemFilter> itemFilters) {
        return ItemQuery.constructor("i")
                .all(itemFilters)
                .and().active(true)
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public Set<String> findFilterSamples(
            @NonNull final ItemFilterField field,
            @Nullable final OffsetDateTime enrichedAfter) {
        return ItemQuery.constructor("i")
                .filterFieldIsDefined(field)
                .and().enrichedAfter(enrichedAfter)
                .constructSampleExecutor(itemsContainer, field.getItemDataField())
                .execute();
    }


    @Override
    public Map<String, Long> countLockedItemsPerQueues(Collection<String> queueIds) {
        String query = "SELECT VALUE root FROM \n" +
                "(SELECT COUNT(1) as count, i.lock.queueId FROM i \n" +
                "WHERE i.lock.queueId IN ('" + String.join("','", queueIds) + "') " +
                "GROUP BY i.lock.queueId) as root";
        Stream<JsonNode> res = itemsContainer.runCrossPartitionQuery(query);
        return res
                .map(cip -> Collections.singletonMap(cip.get("queueId").asText(), cip.get("count").asLong()))
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public Integer countItemsImportedBeforeByQueue(
            final String queueId,
            final OffsetDateTime importedBefore) {
        return ItemQuery.constructor("i")
                .active(true)
                .and().importedBefore(importedBefore)
                .and().queueId(queueId)
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public Integer countItemsLockedBeforeByQueue(
            final String queueId,
            final OffsetDateTime lockedBefore) {
        return ItemQuery.constructor("i")
                .active(true)
                .and().lockedBefore(lockedBefore)
                .and().lockedInQueue(queueId)
                .constructCountExecutor(itemsContainer)
                .execute();
    }

    @Override
    public PageableCollection<Item> findUrgentItems(
            @NonNull final QueueViewType viewType,
            @NonNull final String queueId,
            @Nullable OffsetDateTime importedBefore,
            @Nullable OffsetDateTime lockedBefore,
            int size,
            String continuationToken) {
        return ItemQuery.constructor("i")
                .customQueryCondition(String.format("(%s OR %s)",
                        importedBefore == null ?
                                "false" :
                                String.format("i.imported <= %s", importedBefore.toEpochSecond()),
                        lockedBefore == null ?
                                "false" :
                                String.format("(i.lock.locked <= %s AND i.lock.queueId='%s')",
                                        lockedBefore.toEpochSecond(),
                                        queueId)))
                .and().active(true)
                .and().queueId(queueId)
                .and().customQueryCondition(viewType.getQueryCondition())
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

    @Override
    public Stream<Bucket> getRiskScoreDistribution(final int bucketSize,
                                                   final String queueId) {
        return itemsContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM ( "
                                + "SELECT "
                                + "    temp.risk_score_bucket * %1$s as lowerBound, "
                                + "    Count(1) as count "
                                + "FROM ("
                                + "SELECT "
                                + "    FLOOR(c.assessmentResult.RiskScore/%1$s) as risk_score_bucket "
                                + "FROM c "
                                + "WHERE "
                                + "    c.active "
                                + "    AND IS_DEFINED(c.assessmentResult.RiskScore) "
                                + "    AND NOT IS_NULL(c.assessmentResult.RiskScore) "
                                + "    %2$s "
                                + ") AS temp "
                                + "GROUP BY temp.risk_score_bucket "
                                + ") AS root",
                        bucketSize,
                        StringUtils.isEmpty(queueId) ? "" :
                                String.format("AND ARRAY_CONTAINS(c.queueIds, '%1$s')", queueId)
                )
        )
                .map(cip -> itemsContainer.castCosmosObjectToClassInstance(cip, Bucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public PageableCollection<Item> searchForItems(
            @Nullable Set<String> ids,
            @Nullable Set<String> originalOrderIds,
            @Nullable Set<String> queueIds,
            boolean residual,
            @Nullable Boolean isActive,
            @Nullable Set<@FieldConditionCombination ItemFilter> itemFilters,
            @Nullable Set<String> lockOwnerIds,
            @Nullable Set<String> holdOwnerIds,
            @Nullable Set<Label> labels,
            @Nullable Set<String> labelAuthorIds,
            @NonNull ItemDataField sortingField,
            @NonNull Sort.Direction sortingOrder,
            @Nullable Set<String> tags,
            final int size,
            @Nullable final String continuationToken) {
        return ItemQuery.constructor("i")
                //JOIN
                .collectionInCollectionField(ItemDataField.TAGS, tags)//no ".and" because here we use JOIN part, not WHERE one
                //WHERE
                .enriched()
                .and().inField(ItemDataField.ID, ids)
                .and().inField(ItemDataField.ORIGINAL_ORDER_ID, originalOrderIds)
                .and().queueIds(queueIds, residual)
                .and().active(isActive)
                .and().all(itemFilters)
                .and().inField(ItemDataField.LOCK_OWNER_ID, lockOwnerIds)
                .and().inField(ItemDataField.HOLD_OWNER_ID, holdOwnerIds)
                .and().label(labels)
                .and().inField(ItemDataField.LABEL_AUTHOR_ID, labelAuthorIds)
                //ORDER BY
                .order(new Sort.Order(sortingOrder, sortingField.getPath()))
                .constructSelectExecutor(itemsContainer)
                .execute(size, continuationToken);
    }

}
