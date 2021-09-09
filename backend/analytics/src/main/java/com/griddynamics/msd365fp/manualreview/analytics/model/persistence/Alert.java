// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.model.persistence;

import com.griddynamics.msd365fp.manualreview.analytics.model.AlertCheck;
import com.griddynamics.msd365fp.manualreview.analytics.model.AlertNotification;
import com.griddynamics.msd365fp.manualreview.analytics.model.MetricType;
import com.griddynamics.msd365fp.manualreview.analytics.model.ThresholdOperator;
import com.azure.spring.data.cosmos.core.mapping.Container;
import com.azure.spring.data.cosmos.core.mapping.PartitionKey;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.time.Duration;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.ALERT_CONTAINER_NAME;

/**
 * Class to represent Alert in Manual Review application.
 */
@Data
@NoArgsConstructor
@Container(containerName = ALERT_CONTAINER_NAME)
@EqualsAndHashCode(exclude = "_etag")
public class Alert {

    @Id
    @PartitionKey
    private String id;

    private String name;

    /**
     * Reflect if alert was created by user or by the system automatically.
     */
    private boolean custom;

    /**
     * Each alert relates to concrete user which will receive further
     * notifications if conditions of alert are met.
     */
    private String ownerId;

    /**
     * Enum which corresponds to dashboard values. Such as hit rate or accuracy.
     */
    private MetricType metricType;

    /**
     * Specified data where data should be searched.
     */
    private Duration period;

    /**
     * Condition of the alert with operator like {@link ThresholdOperator#LESS_THAN}
     * or {@link ThresholdOperator#GREATER_THAN}.
     */
    private ThresholdOperator thresholdOperator;

    /**
     * Value for alert condition.
     */
    private Double thresholdValue;

    /**
     * Lists of queues to watch for. If none selected then data about all of
     * them will be processed.
     */
    private Set<String> queues;

    /**
     * Lists of analysts to watch for. If none selected then data about all of
     * them will be processed.
     */
    private Set<String> analysts;

    /**
     * Alert could be enabled or disabled.
     */
    private boolean active;

    /**
     * Tracks when alert's condition was checked.
     */
    private AlertCheck lastCheck = new AlertCheck();

    /**
     * Tracks when alert was sent.
     * The info could be used to prevent notification spamming.
     */
    private AlertNotification lastNotification = new AlertNotification();

    @Version
    @SuppressWarnings("java:S116")
    private String _etag;

}
