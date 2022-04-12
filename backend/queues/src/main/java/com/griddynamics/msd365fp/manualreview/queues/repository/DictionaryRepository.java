// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository;

import com.griddynamics.msd365fp.manualreview.queues.model.DictionaryType;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.DictionaryEntity;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

import java.util.List;

public interface DictionaryRepository extends CosmosRepository<DictionaryEntity, String> {

    List<DictionaryEntity> findAllByType(DictionaryType type);

}
