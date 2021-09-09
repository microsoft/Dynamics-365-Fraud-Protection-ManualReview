// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.griddynamics.msd365fp.manualreview.model.*;
import com.griddynamics.msd365fp.manualreview.model.dfp.AssesmentResult;
import com.griddynamics.msd365fp.manualreview.model.dfp.MainPurchase;
import com.griddynamics.msd365fp.manualreview.queues.model.BasicItemInfo;
import com.griddynamics.msd365fp.manualreview.queues.model.ItemEvent;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.ITEMS_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = ITEMS_CONTAINER_NAME)
public class Item implements BasicItemInfo, Serializable {
    @Id
    @PartitionKey
    private String id;
    private OffsetDateTime imported;
    private OffsetDateTime enriched;
    private int enrichmentAttempts;
    private boolean enrichmentFailed;
    private String enrichmentFailReason;
    @JsonProperty(value = "_ts")
    private OffsetDateTime updated;

    private boolean active;

    @Builder.Default
    private ItemLabel label = new ItemLabel();

    @Builder.Default
    private Set<ItemNote> notes = Collections.emptySet();
    @Builder.Default
    private Set<String> tags = Collections.emptySet();
    @Builder.Default
    private Set<String> queueIds = Collections.emptySet();

    private MainPurchase purchase;
    private AssesmentResult assessmentResult;
    private Decision decision;

    @Builder.Default
    private ItemLock lock = new ItemLock();
    private ItemEscalation escalation;
    private ItemHold hold;

    @Builder.Default
    private Set<String> reviewers = new HashSet<>();

    @Builder.Default
    private Set<ItemEvent> events = new HashSet<>();


    @Builder.Default
    private long ttl = -1;

    @Version
    @SuppressWarnings("java:S116")
    String _etag;

    public void deactivate(long ttl) {
        this.active = false;
        this.ttl = ttl;
    }

    public void lock(String queueId, String queueViewId, String actor) {
        this.lock.lock(queueId, queueViewId, actor);
        reviewers.add(actor);
    }

    public void unlock() {
        reviewers.remove(this.lock.getOwnerId());
        this.lock.unlock();
    }

}
