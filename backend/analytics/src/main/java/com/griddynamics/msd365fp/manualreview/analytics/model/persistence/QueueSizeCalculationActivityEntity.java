// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.OffsetDateTime;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.QUEUE_SIZE_CALCULATION_ACTIVITY_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Container(containerName = QUEUE_SIZE_CALCULATION_ACTIVITY_CONTAINER_NAME)
public class QueueSizeCalculationActivityEntity implements ActivityEntity {
    @Id
    @PartitionKey
    // ID should have the queueId-calculated format
    private String id;
    private String queueId;
    private OffsetDateTime calculated;
    private int size;

    @Builder.Default
    private long ttl = -1;
}
