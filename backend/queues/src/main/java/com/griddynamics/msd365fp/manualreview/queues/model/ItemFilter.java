// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Slf4j
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "condition", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ItemFilterIn.class, name = "IN"),
        @JsonSubTypes.Type(value = ItemFilterIn.class, name = "NOT_IN"),
        @JsonSubTypes.Type(value = ItemFilterContains.class, name = "CONTAINS"),
        @JsonSubTypes.Type(value = ItemFilterRegexp.class, name = "REGEXP"),
        @JsonSubTypes.Type(value = ItemFilterIsTrue.class, name = "IS_TRUE"),
        @JsonSubTypes.Type(value = ItemFilterBetween.class, name = "BETWEEN"),
        @JsonSubTypes.Type(value = ItemFilterBetweenAlph.class, name = "BETWEEN_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterBetweenDate.class, name = "BETWEEN_DATE"),
        @JsonSubTypes.Type(value = ItemFilterBetween.class, name = "NOT_BETWEEN"),
        @JsonSubTypes.Type(value = ItemFilterBetweenAlph.class, name = "NOT_BETWEEN_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterBetweenDate.class, name = "NOT_BETWEEN_DATE"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "EQUAL"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "NOT_EQUAL"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "GREATER"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "LESS"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "GREATER_OR_EQUAL"),
        @JsonSubTypes.Type(value = ItemFilterComparison.class, name = "LESS_OR_EQUAL"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "EQUAL_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "NOT_EQUAL_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "GREATER_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "LESS_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "GREATER_OR_EQUAL_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonAlph.class, name = "LESS_OR_EQUAL_ALPH"),
        @JsonSubTypes.Type(value = ItemFilterComparisonDate.class, name = "GREATER_DATE"),
        @JsonSubTypes.Type(value = ItemFilterComparisonDate.class, name = "LESS_DATE"),
        @JsonSubTypes.Type(value = ItemFilterComparisonDate.class, name = "GREATER_OR_EQUAL_DATE"),
        @JsonSubTypes.Type(value = ItemFilterComparisonDate.class, name = "LESS_OR_EQUAL_DATE")

})
public abstract class ItemFilter implements Serializable {
    @NotNull(message = "filtering field should be presented")
    private ItemFilterField field;
    @NotNull(message = "filtering condition should be presented")
    private ItemDataFieldCondition condition;
    private List<String> values;

}
