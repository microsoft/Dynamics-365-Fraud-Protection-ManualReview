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

import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.COLLECTED_ANALYST_INFO_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Container(containerName = COLLECTED_ANALYST_INFO_CONTAINER_NAME)
public class CollectedAnalystInfoEntity {
    @Id
    @PartitionKey
    // The User/Analyst/Principal ID should be used as the entity ID
    private String id;
    private String displayName;
    private Set<String> roles;
    private String mail;
    private String userPrincipalName;

    @Builder.Default
    private long ttl = -1;
}
