// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemFilter;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueSortSettings;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueViewSettings;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.io.Serializable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.QUEUES_CONTAINER_NAME;


@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = QUEUES_CONTAINER_NAME)
public class Queue implements Serializable {
    @Id
    @PartitionKey
    private String id;
    private String name;

    /**
     * status of the queue. If false then ttl should be > 0
     */
    private boolean active;

    /**
     * Lifecycle checkpoints
     */
    private OffsetDateTime created;
    @JsonProperty(value = "_ts")
    private OffsetDateTime updated;
    private OffsetDateTime deleted;

    /**
     * Size of the queue that calculated periodically.
     * Only used in events.
     */
    private Integer size;

    /**
     * Set of labels that are allowed for
     * applying under this queue
     */
    @Builder.Default
    private Set<Label> allowedLabels = Collections.emptySet();

    /**
     * Set of views that are available for this queue
     */
    @Builder.Default
    private Set<QueueViewSettings> views = Collections.emptySet();

    /**
     * Sets of assigned users.
     * Defines access to queue, it's modification
     * and access to the stored items
     */
    @Builder.Default
    private Set<String> reviewers = Collections.emptySet();
    @Builder.Default
    private Set<String> supervisors = Collections.emptySet();

    /**
     * sorting settings that must be used for
     * providing the item lists
     */
    @Builder.Default
    private QueueSortSettings sorting = new QueueSortSettings();

    /**
     * Criterias for item placement at this queue.
     * Currentky checked peridically in the backgroung.
     */
    @Builder.Default
    private Set<ItemFilter> filters = Collections.emptySet();

    /**
     * The period for SLA calculation. Items that were
     * imported more than {@link this.processingDeadline} ago
     * are treated like overdue.
     */
    private Duration processingDeadline;

    /**
     * a mark for the residual queue
     */
    private boolean residual;

    /**
     * time which is used to automatically delete the queue.
     * isn't counted if -1
     */
    @Builder.Default
    private long ttl = -1;

    /**
     * the support for optimistic locking.
     * Changed by DB on each data update and
     * prohibit to save data with stale {@link this._etag}
     */
    @Version
    @SuppressWarnings("java:S116")
    String _etag;

    public void deactivate(long ttl) {

        this.deleted = OffsetDateTime.now();
        this.active = false;
        this.ttl = ttl;
    }
}
