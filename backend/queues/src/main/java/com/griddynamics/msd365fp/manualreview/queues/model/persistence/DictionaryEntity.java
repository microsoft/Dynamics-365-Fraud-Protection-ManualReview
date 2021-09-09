// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.griddynamics.msd365fp.manualreview.queues.model.DictionaryType;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.OffsetDateTime;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.DICTIONARIES_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = DICTIONARIES_CONTAINER_NAME)
public class DictionaryEntity {

    @Id
    @PartitionKey
    private String id;
    private DictionaryType type;
    private String value;
    @JsonProperty(value = "_ts")
    private OffsetDateTime created;
    private OffsetDateTime confirmed;
    private long ttl;
    @Version
    @SuppressWarnings("java:S116")
    private String _etag;

}
