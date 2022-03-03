// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.griddynamics.msd365fp.manualreview.queues.model.LinkAnalysisField;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.LINK_ANALYSIS_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = LINK_ANALYSIS_CONTAINER_NAME)
public class LinkAnalysis {
    @Id
    @PartitionKey
    private String id;
    private String ownerId;
    private Set<LinkAnalysisField> analysisFields;

    private Set<FieldLinks> fields;

    private int found;
    private int foundInMR;
    @Builder.Default
    private TreeSet<MRItemInfo> mrPurchaseIds = new TreeSet<>();
    @Builder.Default
    private TreeSet<String> dfpPurchaseIds = new TreeSet<>();

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FieldLinks {
        private LinkAnalysisField id;
        private String value;
        private int purchaseCounts;
        private Set<String> purchaseIds;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MRItemInfo implements Comparable<MRItemInfo> {
        private String id;
        private OffsetDateTime imported;

        @Override
        public int compareTo(@NonNull final MRItemInfo o) {
            return Comparator
                    .comparing(MRItemInfo::getImported).reversed()
                    .thenComparing(MRItemInfo::getId)
                    .compare(this, o);
        }
    }

    @Version
    @SuppressWarnings("java:S116")
    String _etag;
    private long ttl;
}
