// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.SizeHistoryBucket;
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
public class QueueSizeCalculationActivityRepositoryImpl implements QueueSizeCalculationActivityRepositoryCustomMethods {

    private final ExtendedCosmosContainer queueSizeCalculationActivityContainer;

    @Override
    public List<SizeHistoryBucket> getSizeHistory(@NonNull final OffsetDateTime startDateTime,
                                                   @NonNull final OffsetDateTime endDateTime,
                                                   @NonNull final Duration aggregation,
                                                   final Set<String> queueIds) {
        return queueSizeCalculationActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT f.queueId as id, c[\"size\"], f.bucket " +
                                "FROM c " +
                                "JOIN (" +
                                    "SELECT " +
                                    "max(c.calculated) as last, c.queueId, bucket " +
                                    "FROM c " +
                                    "JOIN (SELECT VALUE FLOOR((c.calculated-%1$s)/%3$s)) bucket " +
                                    "where " +
                                    "(c.calculated BETWEEN %1$s AND %2$s) " +
                                    "%4$s " +
                                    "GROUP BY c.queueId, bucket) f " +
                                "WHERE c.calculated=f.last " +
                                "AND c.queueId=f.queueId) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        aggregation.getSeconds(),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> queueSizeCalculationActivityContainer.castCosmosObjectToClassInstance(cip, SizeHistoryBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
