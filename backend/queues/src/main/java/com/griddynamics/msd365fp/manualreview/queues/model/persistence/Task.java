// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model.persistence;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.griddynamics.msd365fp.manualreview.model.TaskStatus;
import com.griddynamics.msd365fp.manualreview.model.jackson.ISOStringDateTimeSerializer;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.TASK_CONTAINER_NAME;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = TASK_CONTAINER_NAME)
public class Task {
    @Id
    @PartitionKey
    private String id;
    private TaskStatus status;
    @JsonSerialize(using = ISOStringDateTimeSerializer.class)
    private OffsetDateTime previousRun;
    @JsonSerialize(using = ISOStringDateTimeSerializer.class)
    private OffsetDateTime currentRun;
    @JsonSerialize(using = ISOStringDateTimeSerializer.class)
    private OffsetDateTime previousSuccessfulRun;
    private Duration previousSuccessfulExecutionTime;
    private Map<String, String> variables;
    private String lastFailedRunMessage;
    private String instanceId;

    @Version
    @SuppressWarnings("java:S116")
    String _etag;
}
