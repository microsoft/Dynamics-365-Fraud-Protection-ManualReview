// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.griddynamics.msd365fp.manualreview.model.event.type.ItemPlacementType;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.OffsetDateTime;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.ITEM_PLACEMENT_ACTIVITY_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Container(containerName = ITEM_PLACEMENT_ACTIVITY_CONTAINER_NAME)
public class ItemPlacementActivityEntity implements ActivityEntity {
    @Id
    @PartitionKey
    // ID should have the itemId-queueId-actioned format
    private String id;
    private String itemId;
    private String queueId;
    private ItemPlacementType type;
    private OffsetDateTime actioned;

    @Builder.Default
    private long ttl = -1;

}
