// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.repository;

import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.ConfigurableAppSetting;
import com.griddynamics.msd365fp.manualreview.analytics.model.AppSettingsType;
import com.azure.spring.data.cosmos.repository.CosmosRepository;

import java.util.List;

public interface ConfigurableAppSettingRepository extends CosmosRepository<ConfigurableAppSetting, String> {
    List<ConfigurableAppSetting> findByType(AppSettingsType type);
}
