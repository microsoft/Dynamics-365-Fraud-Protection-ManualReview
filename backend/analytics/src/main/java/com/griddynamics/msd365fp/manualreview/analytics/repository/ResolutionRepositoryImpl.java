// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.Resolution;
import com.griddynamics.msd365fp.manualreview.cosmos.utilities.ExtendedCosmosContainer;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ResolutionRepositoryImpl implements ResolutionRepositoryCustomMethods {

    private final ExtendedCosmosContainer resolutionContainer;

    @Override
    public PageableCollection<String> getResolutionIdsForRetry(
            @NonNull final OffsetDateTime beforeTime,
            @NonNull final Integer maxRetries,
            final String continuationToken,
            int size) {
        ExtendedCosmosContainer.Page res = resolutionContainer.runCrossPartitionPageableQuery(
                String.format("SELECT r.id FROM r " +
                                "WHERE (IS_NULL(r.sentSuccessful) OR NOT r.sentSuccessful)" +
                                "AND (IS_NULL(r.retryCount) OR r.retryCount <= %s)" +
                                "AND (IS_NULL(r.nextRetry) OR r.nextRetry <= %s)",
                        maxRetries,
                        beforeTime.toEpochSecond()),
                size,
                continuationToken);
        return new PageableCollection<>(
                res.getContent()
                        .map(cip -> Optional.ofNullable(cip.get("id").asText()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet()),
                res.getContinuationToken());
    }

    @Override
    public PageableCollection<Resolution> getResolutionsByLastUpdateDuration(
            @NonNull final OffsetDateTime startDateTime,
            @NonNull final OffsetDateTime endDateTime,
            final String continuationToken,
            int size) {
        ExtendedCosmosContainer.Page res = resolutionContainer.runCrossPartitionPageableQuery(
                "SELECT r FROM r " +
                        "WHERE r.label.labeled BETWEEN " + startDateTime.toEpochSecond() +
                        " AND " + endDateTime.toEpochSecond(),
                size,
                continuationToken);
        return convertToPageableCollection(res);
    }

    private PageableCollection<Resolution> convertToPageableCollection(ExtendedCosmosContainer.Page queryResult) {
        return new PageableCollection<>(
                queryResult.getContent()
                        .map(cip -> resolutionContainer.castCosmosObjectToClassInstance(cip.get("r"), Resolution.class))
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet()),
                queryResult.getContinuationToken());
    }
}
