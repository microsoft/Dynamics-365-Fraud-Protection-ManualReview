// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.HEALTH_CHECK_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = HEALTH_CHECK_CONTAINER_NAME)
public class HealthCheck {
    @Id
    @PartitionKey
    private String id;
    private String type;
    private String details;
    private String generatedBy;
    private String receivedBy;
    private Boolean active;
    private Boolean result;
    private OffsetDateTime created;
    @JsonProperty(value = "_ts")
    private OffsetDateTime updated;

    @Version
    @SuppressWarnings("java:S116")
    private String _etag;

    @Builder.Default
    private long ttl = -1;
}
