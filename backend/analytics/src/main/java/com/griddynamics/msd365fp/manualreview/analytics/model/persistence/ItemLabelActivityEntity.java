// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.griddynamics.msd365fp.manualreview.model.Label;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.Duration;
import java.time.OffsetDateTime;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.ITEM_LABEL_ACTIVITY_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Container(containerName = ITEM_LABEL_ACTIVITY_CONTAINER_NAME)
public class ItemLabelActivityEntity implements ActivityEntity {

    @Id
    @PartitionKey
    // The PurchaseId-labeled should be used as the ID
    private String id;
    private String queueId;
    private String queueViewId;
    private String analystId;
    private OffsetDateTime labeled;
    private Label label;
    private String merchantRuleDecision;
    private Duration decisionApplyingDuration;
    private Integer riskScore;

    @Builder.Default
    private long ttl = -1;
}
