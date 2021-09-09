// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.Alert;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

import java.util.List;
import java.util.Optional;

public interface AlertRepository extends CosmosRepository<Alert, String>  {
    List<Alert> findByOwnerId(String userId);
    List<Alert> findByActiveTrue();
    List<Alert> findByOwnerIdAndId(String userId, String id);
    void deleteByOwnerIdAndId(String userId, String id);
}
