// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.LockTimeBucket;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ItemLockActivityRepositoryImpl implements ItemLockActivityRepositoryCustomMethods {

    private final ExtendedCosmosContainer itemLockActivityContainer;


    @Override
    public List<LockTimeBucket> getSpentTime(@NonNull final OffsetDateTime startDateTime,
                                             @NonNull final OffsetDateTime endDateTime,
                                             final Set<String> analystIds,
                                             final Set<String> queueIds) {
        return itemLockActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.actionType, sum(c.released-c.locked) AS totalDuration, count(c.released) AS cnt " +
                                "FROM c where " +
                                "(c.released BETWEEN %1$s AND %2$s) " +
                                "AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) " +
                                "%3$s " +
                                "%4$s " +
                                "group by c.actionType) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.ownerId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemLockActivityContainer.castCosmosObjectToClassInstance(cip, LockTimeBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
}
