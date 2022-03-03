// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.Task;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

public interface TaskRepository extends CosmosRepository<Task, String> {
}
