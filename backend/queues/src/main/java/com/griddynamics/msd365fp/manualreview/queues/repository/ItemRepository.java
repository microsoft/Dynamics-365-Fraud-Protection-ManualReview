// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.repository;

import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

import java.util.List;

@SuppressWarnings("java:S100")
public interface ItemRepository extends CosmosRepository<Item, String>, ItemRepositoryCustomMethods {

    List<Item> findByActiveTrueAndLock_LockedBefore(Long unlockTsEpochSeconds);

    Iterable<Item> findByIdAndActiveTrueAndLock_OwnerIdNotNull(String itemId);
}
