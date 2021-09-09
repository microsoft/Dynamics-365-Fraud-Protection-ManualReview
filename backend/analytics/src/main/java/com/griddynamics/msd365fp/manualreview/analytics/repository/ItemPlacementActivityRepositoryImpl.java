// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.ItemPlacementBucket;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ItemPlacementActivityRepositoryImpl implements ItemPlacementActivityRepositoryCustomMethods {

    private final ExtendedCosmosContainer itemPlacementActivityContainer;

    @Override
    public List<ItemPlacementBucket> getPlacementMetrics(@NonNull final OffsetDateTime startDateTime,
                                                         @NonNull final OffsetDateTime endDateTime,
                                                         @NonNull final Duration aggregation,
                                                         final Set<String> queueIds) {
        return itemPlacementActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.type, count(c.type) AS cnt, c.queueId AS id, FLOOR((c.actioned-%1$s)/%3$s) AS bucket " +
                                "FROM c where " +
                                "(c.actioned BETWEEN %1$s AND %2$s) " +
                                "%4$s " +
                                "group by c.queueId, FLOOR((c.actioned-%1$s)/%3$s), c.type) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        aggregation.getSeconds(),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemPlacementActivityContainer.castCosmosObjectToClassInstance(cip, ItemPlacementBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
