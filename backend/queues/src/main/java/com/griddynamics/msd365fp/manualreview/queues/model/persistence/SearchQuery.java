// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemFilter;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.io.Serializable;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.SEARCH_QUERIES_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = SEARCH_QUERIES_CONTAINER_NAME)
public class SearchQuery implements Serializable {
    @Id
    @PartitionKey
    private String id;

    private Set<String> ids;
    private Set<String> originalOrderIds;
    private Boolean active;
    private Set<String> queueIds;
    @Builder.Default
    private boolean residual = false;
    private Set<ItemFilter> itemFilters;
    private Set<String> lockOwnerIds;
    private Set<String> holdOwnerIds;
    private Set<Label> labels;
    private Set<String> tags;
    private Set<String> labelAuthorIds;

    @Version
    @SuppressWarnings("java:S116")
    String _etag;
    private long ttl;
}
