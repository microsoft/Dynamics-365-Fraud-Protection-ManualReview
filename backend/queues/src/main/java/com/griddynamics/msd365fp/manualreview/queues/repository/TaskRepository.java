// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository;

import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Task;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

public interface TaskRepository extends CosmosRepository<Task, String> {
}
