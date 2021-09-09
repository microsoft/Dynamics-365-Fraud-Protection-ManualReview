// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository;

import com.griddynamics.msd365fp.manualreview.queues.model.persistence.LinkAnalysis;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

public interface LinkAnalysisRepository extends CosmosRepository<LinkAnalysis, String> {

}
