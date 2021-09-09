// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.ItemLabelActivityEntity;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

public interface ItemLabelActivityRepository extends CosmosRepository<ItemLabelActivityEntity, String>, ItemLabelActivityRepositoryCustomMethods {
}
