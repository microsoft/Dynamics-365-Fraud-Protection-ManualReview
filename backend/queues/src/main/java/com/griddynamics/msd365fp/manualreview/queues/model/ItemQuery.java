// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model;

import com.google.common.base.Joiner;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.TOP_ELEMENT_IN_CONTAINER_CONTINUATION;
import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE;
import static lombok.AccessLevel.PRIVATE;

@Slf4j
@Builder(builderMethodName = "constructor", buildMethodName = "construct", builderClassName = "ItemQueryConstructor")
@NoArgsConstructor(access = PRIVATE)
public class ItemQuery {

    public static ItemQueryConstructor constructor(String alias) {
        return new ItemQueryConstructor().alias(alias);
    }

    public static class ItemQueryConstructor {

        private String alias;
        private final List<String> queryParts = new ArrayList<>();
        private final Map<String, List<String>> joinParts = new TreeMap<>();
        private String orderPart = "";

        ItemQueryConstructor alias(String alias) {
            this.alias = alias;
            return this;
        }

        public ItemQueryConstructor all(@Nullable Collection<ItemFilter> itemFilters) {
            if (CollectionUtils.isEmpty(itemFilters)) {
                queryParts.add("true");
                return this;
            }
            Iterator<ItemFilter> itemFilterIterator = itemFilters.iterator();
            itemFilter(itemFilterIterator.next());
            while (itemFilterIterator.hasNext()) {
                and();
                itemFilter(itemFilterIterator.next());
            }
            return this;
        }

        public ItemQueryConstructor id(String id) {
            queryParts.add(String.format(
                    "%s.id='%s'",
                    alias,
                    id)
            );
            return this;
        }

        public ItemQueryConstructor queueId(String queueId) {
            String condition = String.format(
                    "ARRAY_CONTAINS(%s.queueIds, \"%s\")",
                    alias,
                    queueId
            );
            queryParts.add(condition);
            return this;
        }

        public ItemQueryConstructor customQueryCondition(String condition) {
            queryParts.add(condition);
            return this;
        }

        public ItemQueryConstructor not() {
            queryParts.add("NOT");
            return this;
        }

        public ItemQueryConstructor and() {
            queryParts.add("AND");
            return this;
        }

        public ItemQueryConstructor or() {
            queryParts.add("OR");
            return this;
        }

        public ItemQueryConstructor filterFieldIsDefined(ItemFilterField filter) {
            FieldDecomposition decomposition = decomposeField(filter.getItemDataField());
            String condition = String.format(
                    "IS_DEFINED(%s%s)",
                    decomposition.getLocalAlias(),
                    decomposition.getPath());
            if (decomposition.getArray() != null) {
                List<String> parts = joinParts.computeIfAbsent(decomposition.getArray(), key -> new LinkedList<>());
                if (!parts.isEmpty()) parts.add("AND");
                parts.add(condition);
                queryParts.add("true");
            } else {
                queryParts.add(condition);
            }
            return this;
        }

        public ItemQueryConstructor itemFilter(ItemFilter itemFilter) {
            FieldDecomposition decomposition = decomposeField(itemFilter.getField().getItemDataField());
            String condition;
            switch (itemFilter.getCondition()) {
                case IN:
                    condition = String.format(
                            "%s%s IN ('%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            Joiner.on("', '").join(itemFilter.getValues())
                    );
                    break;
                case NOT_IN:
                    condition = String.format(
                            "%s%s NOT IN ('%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            Joiner.on("', '").join(itemFilter.getValues())
                    );
                    break;
                case CONTAINS:
                    condition = String.format(
                            "CONTAINS(%s%s, '%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0)
                    );
                    break;
                case IS_TRUE:
                    condition = String.format(
                            "%3$s (IS_DEFINED(%1$s%2$s) AND NOT IS_NULL(%1$s%2$s) AND %1$s%2$s)", //TODO: check
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            Boolean.parseBoolean(itemFilter.getValues().get(0)) ? "" : "NOT"
                    );
                    break;
                case REGEXP:
                    condition = String.format(
                            "udf.isMatchRegexp(%s%s, \"%s\")",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0)
                    );
                    break;
                case BETWEEN:
                    condition = String.format(
                            "(%s%s BETWEEN %s AND %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0),
                            itemFilter.getValues().get(1)
                    );
                    break;
                case NOT_BETWEEN:
                    condition = String.format(
                            "(%s%s NOT BETWEEN %s AND %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0),
                            itemFilter.getValues().get(1)
                    );
                    break;
                case BETWEEN_ALPH:
                    condition = String.format(
                            "(%s%s BETWEEN '%s' AND '%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0),
                            itemFilter.getValues().get(1)
                    );
                    break;
                case NOT_BETWEEN_ALPH:
                    condition = String.format(
                            "(%s%s NOT BETWEEN '%s' AND '%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            itemFilter.getValues().get(0),
                            itemFilter.getValues().get(1)
                    );
                    break;
                case BETWEEN_DATE:
                    condition = String.format(
                            "(%s%s BETWEEN %s AND %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            OffsetDateTime.parse(itemFilter.getValues().get(0)).toEpochSecond(),
                            OffsetDateTime.parse(itemFilter.getValues().get(1)).toEpochSecond()
                    );
                    break;
                case NOT_BETWEEN_DATE:
                    condition = String.format(
                            "(%s%s NOT BETWEEN %s AND %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            OffsetDateTime.parse(itemFilter.getValues().get(0)).toEpochSecond(),
                            OffsetDateTime.parse(itemFilter.getValues().get(1)).toEpochSecond()
                    );
                    break;
                case EQUAL:
                case NOT_EQUAL:
                case GREATER:
                case LESS:
                case GREATER_OR_EQUAL:
                case LESS_OR_EQUAL:
                    condition = String.format(
                            "(%s%s %s %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            getSignByComparisonCondition(itemFilter.getCondition()),
                            itemFilter.getValues().get(0));
                    break;
                case EQUAL_ALPH:
                case NOT_EQUAL_ALPH:
                case GREATER_ALPH:
                case LESS_ALPH:
                case GREATER_OR_EQUAL_ALPH:
                case LESS_OR_EQUAL_ALPH:
                    condition = String.format(
                            "(%s%s %s '%s')",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            getSignByComparisonCondition(itemFilter.getCondition()),
                            itemFilter.getValues().get(0));
                    break;
                case GREATER_DATE:
                case LESS_DATE:
                case GREATER_OR_EQUAL_DATE:
                case LESS_OR_EQUAL_DATE:
                    condition = String.format(
                            "(%s%s %s %s)",
                            decomposition.getLocalAlias(),
                            decomposition.getPath(),
                            getSignByComparisonCondition(itemFilter.getCondition()),
                            OffsetDateTime.parse(itemFilter.getValues().get(0)).toEpochSecond());
                    break;
                default:
                    throw new IncorrectFilterException(
                            String.format("Could not build query due to unexpected FilterCondition: %s",
                                    itemFilter.getCondition()));
            }
            if (decomposition.getArray() != null) {
                List<String> parts = joinParts.computeIfAbsent(decomposition.getArray(), key -> new LinkedList<>());
                if (!parts.isEmpty()) parts.add("AND");
                parts.add(condition);
                queryParts.add("true");
            } else {
                queryParts.add(condition);
            }
            return this;
        }

        private String getSignByComparisonCondition(final ItemDataFieldCondition condition) {
            switch (condition) {
                case EQUAL:
                case EQUAL_ALPH:
                    return "=";
                case NOT_EQUAL:
                case NOT_EQUAL_ALPH:
                    return "!=";
                case GREATER:
                case GREATER_ALPH:
                case GREATER_DATE:
                    return ">";
                case LESS:
                case LESS_ALPH:
                case LESS_DATE:
                    return "<";
                case GREATER_OR_EQUAL:
                case GREATER_OR_EQUAL_ALPH:
                case GREATER_OR_EQUAL_DATE:
                    return ">=";
                case LESS_OR_EQUAL:
                case LESS_OR_EQUAL_ALPH:
                case LESS_OR_EQUAL_DATE:
                    return "<=";
                default:
                    throw new IllegalArgumentException();
            }
        }

        public ItemQueryConstructor order(Sort.Order order) {
            orderPart = order.getProperty() + " " + order.getDirection();
            return this;
        }

        public ItemQueryConstructor active(@Nullable Boolean active) {
            if (active == null) {
                queryParts.add("true");
                return this;
            }

            queryParts.add(String.format(
                    "%s.active=%s",
                    alias,
                    active)
            );
            return this;
        }

        public ItemQueryConstructor importedBefore(OffsetDateTime time) {
            if (time != null) {
                queryParts.add(String.format(
                        "%s.imported<%s",
                        alias,
                        time.toEpochSecond()));
            } else {
                queryParts.add("true");
            }
            return this;
        }

        public ItemQueryConstructor lockedInQueue(String queue) {
            queryParts.add(String.format(
                    "%s.lock.queueId='%s'",
                    alias,
                    queue));
            return this;
        }

        public ItemQueryConstructor escalatedInQueue(String queue) {
            queryParts.add(String.format(
                    "%s.escalation.queueId='%s'",
                    alias,
                    queue));
            return this;
        }

        public ItemQueryConstructor heldInQueue(String queue) {
            queryParts.add(String.format(
                    "%s.hold.queueId='%s'",
                    alias,
                    queue));
            return this;
        }

        public ItemQueryConstructor lockedBefore(OffsetDateTime time) {
            if (time != null) {
                queryParts.add(String.format(
                        "%s.lock.locked<%s",
                        alias,
                        time.toEpochSecond()));
            } else {
                queryParts.add("true");
            }
            return this;
        }

        public ItemQueryConstructor updatedAfter(OffsetDateTime time) {
            if (time != null) {
                queryParts.add(String.format(
                        "%s._ts>=%s",
                        alias,
                        time.toEpochSecond())
                );
            } else {
                queryParts.add("true");
            }
            return this;
        }

        public ItemQueryConstructor enriched() {
            queryParts.add(
                    String.format("IS_DEFINED(%1$s.enriched) AND NOT IS_NULL(%1$s.enriched)", alias)
            );
            return this;
        }

        public ItemQueryConstructor enrichedAfter(OffsetDateTime time) {
            if (time != null) {
                queryParts.add(String.format(
                        "%s.enriched>=%s",
                        alias,
                        time.toEpochSecond())
                );
            } else {
                queryParts.add("true");
            }
            return this;
        }

        public ItemQueryConstructor includeLocked(boolean includeLocked) {
            if (!includeLocked) {
                queryParts.add(String.format(
                        "IS_NULL(%s.lock.ownerId)",
                        alias)
                );
            } else {
                queryParts.add("true");
            }
            return this;
        }

        @SuppressWarnings("java:S5411")
        public ItemQueryConstructor locked(@Nullable Boolean locked) {
            if (locked != null) {
                if (locked) {
                    queryParts.add(String.format(
                            "!IS_NULL(%s.lock.ownerId)",
                            alias));
                } else {
                    queryParts.add(String.format(
                            "(IS_NULL(%1$s.lock.ownerId) OR NOT IS_DEFINED(%1$s.lock.ownerId))",
                            alias)
                    );
                }
            } else {
                queryParts.add("true");
            }
            return this;
        }

        @SuppressWarnings("java:S5411")
        public ItemQueryConstructor held(@Nullable Boolean held) {
            if (held != null) {
                if (held) {
                    queryParts.add(String.format(
                            "!IS_NULL(%s.hold.ownerId)",
                            alias));
                } else {
                    queryParts.add(String.format(
                            "(IS_NULL(%1$s.hold.ownerId) OR NOT IS_DEFINED(%1$s.hold.ownerId))",
                            alias)
                    );
                }
            } else {
                queryParts.add("true");
            }
            return this;
        }

        public ItemQueryConstructor queueIdsAreEmpty() {
            queryParts.add(String.format(
                    "(IS_NULL(%1$s.queueIds) OR %1$s.queueIds = [])",
                    alias));
            return this;
        }

        public ItemQueryConstructor hasEvents() {
            queryParts.add(String.format(
                    "(ARRAY_LENGTH(%1$s.events) > 0)",
                    alias));
            return this;
        }

        //TODO: rework
        public ItemQueryConstructor notEscalation() {
            queryParts.add(String.format(
                    "(NOT IS_DEFINED(%1$s.escalation.queueId) OR IS_NULL(%1$s.escalation.queueId)) ",
                    alias));
            return this;
        }

        public String constructSelect() {
            String joinClause = getJoinClause();

            if (StringUtils.isEmpty(joinClause)) {
                return String.format(
                        "SELECT %1$s FROM %1$s WHERE %2$s %3$s",
                        alias,
                        Joiner.on(" ").join(queryParts),
                        getOrderByClause(""));
            } else {
                return String.format(
                        "SELECT VALUE root FROM (SELECT DISTINCT %1$s FROM %1$s %2$s WHERE %3$s) AS root %4$s",
                        alias,
                        joinClause,
                        Joiner.on(" ").join(queryParts),
                        getOrderByClause("root"));
            }
        }

        public String constructCount() {
            return String.format(
                    "SELECT VALUE COUNT(1) FROM %1$s %2$s WHERE %3$s",
                    alias,
                    getJoinClause(),
                    Joiner.on(" ").join(queryParts));
        }

        public String constructSample(ItemDataField field) {
            FieldDecomposition decomposition = decomposeField(field);
            return String.format(
                    "SELECT VALUE root FROM (SELECT DISTINCT %1$s%2$s as val FROM %3$s %4$s WHERE %5$s) AS root",
                    decomposition.getLocalAlias(),
                    decomposition.getPath(),
                    alias,
                    getJoinClause(),
                    Joiner.on(" ").join(queryParts));
        }

        private String getJoinClause() {
            return CollectionUtils.isEmpty(joinParts) ? "" :
                    joinParts.entrySet().stream()
                            .map(entry -> String.format(
                                    "JOIN (SELECT DISTINCT VALUE %1$s FROM %1$s IN %2$s.%3$s WHERE %4$s) %1$s",
                                    getJoinClauseAliasName(entry.getKey()),
                                    alias,
                                    entry.getKey(),
                                    Joiner.on(" ").join(entry.getValue())))
                            .collect(Collectors.joining(" "));
        }

        private String getOrderByClause(String rootAlias) {
            return StringUtils.isEmpty(orderPart) ? "" :
                    String.format(
                            "ORDER BY %s%s.%s",
                            StringUtils.isEmpty(rootAlias) ? "" : rootAlias + ".",
                            alias,
                            orderPart);
        }

        public ItemSelectQueryExecutor constructSelectExecutor(ExtendedCosmosContainer itemsContainer) {
            return (size, continuationToken) -> {
                String query = constructSelect();
                ExtendedCosmosContainer.Page res;
                try {
                    res = itemsContainer.runCrossPartitionPageableQuery(
                            query, size, continuationToken);
                } catch (Exception e) {
                    log.error("Constructed query execution ended with error. Query: [{}]", query);
                    throw e;
                }
                List<Item> queriedItems = res.getContent()
                        .map(cip -> itemsContainer.castCosmosObjectToClassInstance(cip.get(alias), Item.class))
                        .flatMap(Optional::stream)
                        .collect(Collectors.toList());
                return new PageableCollection<>(queriedItems, res.getContinuationToken());
            };
        }

        public ItemCountQueryExecutor constructCountExecutor(ExtendedCosmosContainer itemsContainer) {
            return () -> {
                String query = constructCount();
                ExtendedCosmosContainer.Page res;
                try {
                    res = itemsContainer.runCrossPartitionPageableQuery(query,
                            TOP_ELEMENT_IN_CONTAINER_PAGE_SIZE, TOP_ELEMENT_IN_CONTAINER_CONTINUATION);
                } catch (Exception e) {
                    log.error("Constructed query execution ended with error. Query: [{}]", query);
                    throw e;
                }
                Optional<Integer> optionalCount = res.getContent()
                        .map(cip -> (int) cip.get("_aggregate").asInt())
                        .findFirst();
                return optionalCount.orElse(0);
            };
        }


        public FilterSampleQueryExecutor constructSampleExecutor(ExtendedCosmosContainer itemsContainer, ItemDataField field) {
            return () -> {
                String query = constructSample(field);
                try {
                    return itemsContainer.runCrossPartitionQuery(query)
                            .map(cip -> cip.get("val").asText())
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    log.error("Constructed query execution ended with error. Query: [{}]", query);
                    throw e;
                }
            };
        }

        public ItemQueryConstructor inField(ItemDataField itemDataField, @Nullable Collection<String> collection) {
            if (CollectionUtils.isEmpty(collection)) {
                queryParts.add("true");
                return this;
            }

            if (itemDataField.isArrayField()) {
                throw new IllegalArgumentException(
                        String.format(
                                "inField() method work only with non-array fields. But field [%s] is an array one.",
                                itemDataField));
            }

            queryParts.add(String.format(
                    "%s.%s IN ('%s')",
                    alias,
                    itemDataField.getPath(),
                    String.join("','", collection))
            );
            return this;
        }

        public ItemQueryConstructor label(@Nullable Collection<Label> labels) {
            if (CollectionUtils.isEmpty(labels)) {
                queryParts.add("true");
                return this;
            }

            return inField(ItemDataField.LABEL_VALUE, labels.stream()
                    .map(Enum::name)
                    .collect(Collectors.toSet()));
        }

        public ItemQueryConstructor collectionInCollectionField(ItemDataField field, @Nullable Collection<String> collection) {
            if (CollectionUtils.isEmpty(collection)) {
                return this;
            }

            FieldDecomposition decomposition = decomposeField(field);

            String condition = String.format(
                    "%s%s IN ('%s')",
                    decomposition.getLocalAlias(),
                    decomposition.getPath(),
                    String.join("','", collection));

            List<String> parts = joinParts.computeIfAbsent(decomposition.getArray(), key -> new LinkedList<>());
            if (!parts.isEmpty()) {
                parts.add("AND");
            }
            parts.add(condition);

            return this;
        }

        public ItemQueryConstructor queueIds(@Nullable Collection<String> collection,
                                             boolean residual) {
            if (CollectionUtils.isEmpty(collection)) {
                queryParts.add("true");
                return this;
            }

            String labelQueueQuery = String.format(
                    "%s.%s IN ('%s')",
                    alias,
                    ItemDataField.LABEL_QUEUE_ID.getPath(),
                    String.join("','", collection));

            String queuesQuery = collection.stream()
                    .map(queueId ->
                            String.format(
                                    "ARRAY_CONTAINS(%s.%s, '%s')",
                                    alias,
                                    decomposeField(ItemDataField.QUEUE_IDS).getArray(),
                                    queueId))
                    .collect(Collectors.joining(" OR "));

            String residualQueueQuery = "false";
            if (residual) {
                residualQueueQuery = String.format("ARRAY_LENGTH(%1$s.%2$s) = 0 AND IS_NULL(%1$s.%3$s)",
                        alias,
                        decomposeField(ItemDataField.QUEUE_IDS).getArray(),
                        ItemDataField.LABEL_QUEUE_ID.getPath());
            }

            queryParts.add("("
                    + String.join(" OR ", labelQueueQuery, queuesQuery, residualQueueQuery)
                    + ")");
            return this;
        }

        private FieldDecomposition decomposeField(ItemDataField field) {
            FieldDecomposition result = new FieldDecomposition();

            if (field.isArrayField()) {
                // form "datainarray.field" from "data.in.array[].fiels"
                // and "datainarray" from "data.in.array[]"
                String fieldPath = field.getPath();
                result.setArray(fieldPath.substring(0, fieldPath.indexOf("[]")));
                result.setLocalAlias(getJoinClauseAliasName(result.getArray()));
                result.setPath(fieldPath.substring(fieldPath.indexOf("[]") + "[]".length()));
            } else {
                result.setPath("." + field.getPath());
                result.setLocalAlias(alias);
                result.setArray(null);
            }
            return result;
        }

        private String getJoinClauseAliasName(String field) {
            return field.replaceAll("\\.", "");
        }

        @Getter
        @Setter
        private static class FieldDecomposition {
            private String path;
            private String localAlias;
            private String array;
        }

        public static class IncorrectFilterException extends RuntimeException {
            public IncorrectFilterException(final String message) {
                super(message);
            }
        }

    }


    @FunctionalInterface
    public interface ItemSelectQueryExecutor {
        PageableCollection<Item> execute(int size, String continuationToken);
    }

    @FunctionalInterface
    public interface ItemCountQueryExecutor {
        Integer execute();
    }

    @FunctionalInterface
    public interface FilterSampleQueryExecutor {
        Set<String> execute();
    }
}
