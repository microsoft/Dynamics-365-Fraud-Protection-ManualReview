// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.AnalystDetails;
import com.griddynamics.msd365fp.manualreview.analytics.model.ItemLabelingBucket;
import com.griddynamics.msd365fp.manualreview.analytics.model.LabelBucket;
import com.griddynamics.msd365fp.manualreview.analytics.model.LabelingTimeBucket;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.model.Label;
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
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class ItemLabelActivityRepositoryImpl implements ItemLabelActivityRepositoryCustomMethods {

    private final ExtendedCosmosContainer itemLabelActivityContainer;

    private static final String labelsThatFormResolutionAsString = Label.getLabelsThatFormsResolution().stream()
            .map(Enum::name)
            .collect(Collectors.joining("','"));

    @Override
    public List<ItemLabelingBucket> getQueuePerformance(@NonNull final OffsetDateTime startDateTime,
                                                        @NonNull final OffsetDateTime endDateTime,
                                                        @NonNull final Duration aggregation,
                                                        final Set<String> analystIds,
                                                        final Set<String> queueIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.label, c.merchantRuleDecision, count(c.label) AS cnt, c.queueId AS id, FLOOR((c.labeled-%1$s)/%3$s) AS bucket " +
                                "FROM c WHERE " +
                                "(c.labeled BETWEEN %1$s AND %2$s) " +
                                "AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) " +
                                "%4$s " +
                                "%5$s " +
                                "group by c.queueId, FLOOR((c.labeled-%1$s)/%3$s), c.label, c.merchantRuleDecision) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        aggregation.getSeconds(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, ItemLabelingBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemLabelingBucket> getAnalystPerformance(@NonNull final OffsetDateTime startDateTime,
                                                          @NonNull final OffsetDateTime endDateTime,
                                                          @NonNull final Duration aggregation,
                                                          final Set<String> analystIds,
                                                          final Set<String> queueIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.label, c.merchantRuleDecision, count(c.label) AS cnt, c.analystId as id, FLOOR((c.labeled-%1$s)/%3$s) AS bucket " +
                                "FROM c WHERE " +
                                "(c.labeled BETWEEN %1$s AND %2$s) " +
                                "%4$s " +
                                "%5$s " +
                                "group by c.analystId, FLOOR((c.labeled-%1$s)/%3$s), c.label, c.merchantRuleDecision) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        aggregation.getSeconds(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, ItemLabelingBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<AnalystDetails> getAnalystDetails(@NonNull final OffsetDateTime startDateTime,
                                                  @NonNull final OffsetDateTime endDateTime,
                                                  final Set<String> analystIds) {
        String query = String.format(
                        "SELECT c.label, c.merchantRuleDecision, c.id, c.analystId " +
                        "FROM c WHERE " +
                        "(c.labeled BETWEEN %1$s AND %2$s) " +
                        " %3$s " +
                        "AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) ",

                startDateTime.toEpochSecond(),
                endDateTime.toEpochSecond(),
                CollectionUtils.isEmpty(analystIds) ? "" :
                        String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)));
        return itemLabelActivityContainer.runCrossPartitionQuery(query)
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, AnalystDetails.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemLabelingBucket> getTotalPerformance(@NonNull final OffsetDateTime startDateTime,
                                                        @NonNull final OffsetDateTime endDateTime,
                                                        final Set<String> analystIds,
                                                        final Set<String> queueIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.label, c.merchantRuleDecision, count(c.label) AS cnt " +
                                "FROM c WHERE " +
                                "(c.labeled BETWEEN %1$s AND %2$s) " +
                                "AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) " +
                                "%3$s " +
                                "%4$s " +
                                "group by c.label, c.merchantRuleDecision) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, ItemLabelingBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<ItemLabelingBucket> getBatchPerformance(@NonNull final OffsetDateTime startDateTime,
                                                        @NonNull final OffsetDateTime endDateTime,
                                                        final Set<String> analystIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.label, c.merchantRuleDecision, count(c.label) AS cnt " +
                                "FROM c WHERE " +
                                "(c.labeled BETWEEN %1$s AND %2$s) " +
                                "AND (NOT IS_DEFINED(c.queueId) OR IS_NULL(c.queueId)) " +
                                "%3$s " +
                                "group by c.label, c.merchantRuleDecision) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds))))
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, ItemLabelingBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public List<LabelingTimeBucket> getSpentTime(@NonNull final OffsetDateTime startDateTime,
                                                 @NonNull final OffsetDateTime endDateTime,
                                                 final Set<String> analystIds,
                                                 final Set<String> queueIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM " +
                                "(SELECT c.label, sum(c.decisionApplyingDuration) AS totalDuration, count(c.labeled) AS cnt " +
                                "FROM c WHERE " +
                                "(c.labeled BETWEEN %1$s AND %2$s) " +
                                "AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) " +
                                "%3$s " +
                                "%4$s " +
                                "group by c.label) " +
                                "AS root",
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds))))
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, LabelingTimeBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    @Override
    public Stream<LabelBucket> getRiskScoreDistribution(
            @NonNull final OffsetDateTime startDateTime,
            @NonNull final OffsetDateTime endDateTime,
            int bucketSize,
            Set<String> analystIds,
            Set<String> queueIds) {
        return itemLabelActivityContainer.runCrossPartitionQuery(
                String.format(
                        "SELECT VALUE root FROM ( "
                                + "SELECT "
                                + "    temp.risk_score_bucket * %1$s as lowerBound, "
                                + "    temp.label, "
                                + "    Count(1) as count "
                                + "FROM ("
                                + "SELECT "
                                + "    FLOOR(c.riskScore/%1$s) as risk_score_bucket, "
                                + "    c.label "
                                + "FROM c "
                                + "WHERE "
                                + "    (c.labeled BETWEEN %2$s AND %3$s)"
                                + "    AND IS_DEFINED(c.riskScore) "
                                + "    AND NOT IS_NULL(c.riskScore) "
                                + "    AND IS_DEFINED(c.queueId) AND NOT IS_NULL(c.queueId) "
                                + "    %4$s "
                                + "    %5$s "
                                + "    %6$s "
                                + ") AS temp "
                                + "GROUP BY temp.risk_score_bucket, temp.label "
                                + ") AS root",
                        bucketSize,
                        startDateTime.toEpochSecond(),
                        endDateTime.toEpochSecond(),
                        CollectionUtils.isEmpty(analystIds) ? "" :
                                String.format("AND c.analystId IN ('%1$s') ", String.join("','", analystIds)),
                        CollectionUtils.isEmpty(queueIds) ? "" :
                                String.format("AND c.queueId IN ('%1$s') ", String.join("','", queueIds)),
                        String.format("AND c.label IN ('%1$s') ", labelsThatFormResolutionAsString)
                )
        )
                .map(cip -> itemLabelActivityContainer.castCosmosObjectToClassInstance(cip, LabelBucket.class))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }
}
