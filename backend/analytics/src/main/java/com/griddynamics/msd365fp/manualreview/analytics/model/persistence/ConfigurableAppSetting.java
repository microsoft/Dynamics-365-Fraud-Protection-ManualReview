// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.griddynamics.msd365fp.manualreview.analytics.model.AppSettingsType;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import nonapi.io.github.classgraph.json.Id;
import org.springframework.data.annotation.Version;

import java.util.Map;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.APP_SETTINGS_CONTAINER_NAME;

@NoArgsConstructor
@Data
@EqualsAndHashCode(exclude = "_etag")
@Container(containerName = APP_SETTINGS_CONTAINER_NAME)
public class ConfigurableAppSetting {

    @Id
    @PartitionKey
    private String id;
    private String name;
    private AppSettingsType type;
    private Map<String, String> value;

    @Version
    @SuppressWarnings("java:S116")
    private String _etag;
}
