// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model;

import lombok.AllArgsConstructor;

import java.io.Serializable;

@SuppressWarnings("unused")
@AllArgsConstructor
public enum ItemDataFieldCondition implements Serializable {
    IN,
    NOT_IN,
    CONTAINS,
    REGEXP,
    IS_TRUE,
    BETWEEN,
    BETWEEN_ALPH,
    BETWEEN_DATE,
    NOT_BETWEEN,
    NOT_BETWEEN_ALPH,
    NOT_BETWEEN_DATE,
    EQUAL,
    EQUAL_ALPH,
    NOT_EQUAL,
    NOT_EQUAL_ALPH,
    GREATER,
    GREATER_ALPH,
    GREATER_DATE,
    LESS,
    LESS_ALPH,
    LESS_DATE,
    GREATER_OR_EQUAL,
    GREATER_OR_EQUAL_ALPH,
    GREATER_OR_EQUAL_DATE,
    LESS_OR_EQUAL,
    LESS_OR_EQUAL_ALPH,
    LESS_OR_EQUAL_DATE
}
