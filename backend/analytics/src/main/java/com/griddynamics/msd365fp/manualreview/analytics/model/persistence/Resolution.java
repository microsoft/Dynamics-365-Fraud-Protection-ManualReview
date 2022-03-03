// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.griddynamics.msd365fp.manualreview.analytics.model.DFPResolutionSendResult;
import com.griddynamics.msd365fp.manualreview.model.ItemEscalation;
import com.griddynamics.msd365fp.manualreview.model.ItemHold;
import com.griddynamics.msd365fp.manualreview.model.ItemLabel;
import com.griddynamics.msd365fp.manualreview.model.ItemNote;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.RESOLUTION_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = RESOLUTION_CONTAINER_NAME)
public class Resolution {
    @Id
    @PartitionKey
    private String id;
    private OffsetDateTime imported;
    @JsonProperty(value = "_ts")
    private OffsetDateTime updated;
    private OffsetDateTime sent;

    private OffsetDateTime nextRetry;
    private int retryCount;
    private Boolean sentSuccessful;
    private DFPResolutionSendResult lastSendResult;

    private ItemLabel label;
    private ItemEscalation escalation;
    private ItemHold hold;
    private Set<ItemNote> notes;
    private Set<String> tags;

    @Builder.Default
    private long ttl = -1;

    @Version
    @SuppressWarnings("java:S116")
    String _etag;
}
