// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

/**
 * All available acceptable conditions for filters
 */
export enum ACCEPTABLE_CONDITIONS {
    /**
     * Number filed
     */
    BETWEEN = 'BETWEEN',

    /**
     * Calendar field with two date inputs
     */
    BETWEEN_DATE = 'BETWEEN_DATE',

    /**
     * String field
     */
    BETWEEN_ALPH = 'BETWEEN_ALPH',

    /**
     * String field
     */
    CONTAINS = 'CONTAINS',

    /**
     * Number filed
     */
    EQUAL = 'EQUAL',

    /**
     * String field
     */
    EQUAL_ALPH = 'EQUAL_ALPH',

    /**
     * Number field
     */
    GREATER = 'GREATER',

    /**
     * String field
     */
    GREATER_ALPH = 'GREATER_ALPH',

    /**
     * Calendar field with one date input
     */
    GREATER_DATE = 'GREATER_DATE',

    /**
     * Number filed
     */
    GREATER_OR_EQUAL = 'GREATER_OR_EQUAL',

    /**
     * String field
     */
    GREATER_OR_EQUAL_ALPH = 'GREATER_OR_EQUAL_ALPH',

    /**
     * Calendar field with one date input
     */
    GREATER_OR_EQUAL_DATE = 'GREATER_OR_EQUAL_DATE',

    /**
     * String field with multiple input and suggestions list (hints)
     */
    IN = 'IN',

    /**
    * String field with multiple input and suggestions list (hints)
    */
    NOT_IN = 'NOT_IN',

    /**
     * Toggle field (checkbox)
     */
    IS_TRUE = 'IS_TRUE',

    /**
     * Number filed
     */
    LESS = 'LESS',

    /**
     * String field
     */
    LESS_ALPH = 'LESS_ALPH',

    /**
     * Calendar field with one date input
     */
    LESS_DATE = 'LESS_DATE',

    /**
     * Number filed
     */
    LESS_OR_EQUAL = 'LESS_OR_EQUAL',

    /**
     * String field
     */
    LESS_OR_EQUAL_ALPH = 'LESS_OR_EQUAL_ALPH',

    /**
     * Calendar field with one date input
     */
    LESS_OR_EQUAL_DATE = 'LESS_OR_EQUAL_DATE',

    /**
     * Number field with two inputs
     */
    NOT_BETWEEN = 'NOT_BETWEEN',

    /**
     * Number field with single input
     */
    NOT_EQUAL = 'NOT_EQUAL',

    /**
     * String field with single input
     */
    NOT_EQUAL_ALPH = 'NOT_EQUAL_ALPH',

    /**
     * String field with wto inputs
     */
    NOT_BETWEEN_ALPH = 'NOT_BETWEEN_ALPH',

    /**
     * Calendar field with two date inputs
     */
    NOT_BETWEEN_DATE = 'NOT_BETWEEN_DATE',

    /**
     * String field
     */
    REGEXP = 'REGEXP',
}

/**
 * Represents display names for each of acceptable conditions
 *
 * Represents Acceptable Conditions as a display text
 * for dropdown options, and context menu items
 */
export const DISPLAY_ACCEPTABLE_CONDITIONS = {
    [ACCEPTABLE_CONDITIONS.BETWEEN]: 'Between',
    [ACCEPTABLE_CONDITIONS.BETWEEN_ALPH]: 'Between a-z',
    [ACCEPTABLE_CONDITIONS.BETWEEN_DATE]: 'Between date',

    [ACCEPTABLE_CONDITIONS.CONTAINS]: 'Contains',

    [ACCEPTABLE_CONDITIONS.EQUAL]: 'Equal',
    [ACCEPTABLE_CONDITIONS.EQUAL_ALPH]: 'Equal a-z',

    [ACCEPTABLE_CONDITIONS.GREATER]: 'Greater',
    [ACCEPTABLE_CONDITIONS.GREATER_ALPH]: 'Greater a-z',
    [ACCEPTABLE_CONDITIONS.GREATER_DATE]: 'Greater date',

    [ACCEPTABLE_CONDITIONS.GREATER_OR_EQUAL]: 'Greater or equal',
    [ACCEPTABLE_CONDITIONS.GREATER_OR_EQUAL_ALPH]: 'Greater or equal a-z',
    [ACCEPTABLE_CONDITIONS.GREATER_OR_EQUAL_DATE]: 'Greater or equal date',

    [ACCEPTABLE_CONDITIONS.IN]: 'In',
    [ACCEPTABLE_CONDITIONS.NOT_IN]: 'Not in',

    [ACCEPTABLE_CONDITIONS.IS_TRUE]: 'Yes/No',

    [ACCEPTABLE_CONDITIONS.LESS]: 'Less',
    [ACCEPTABLE_CONDITIONS.LESS_ALPH]: 'Less a-z',
    [ACCEPTABLE_CONDITIONS.LESS_DATE]: 'Less date',

    [ACCEPTABLE_CONDITIONS.LESS_OR_EQUAL]: 'Less or equal',
    [ACCEPTABLE_CONDITIONS.LESS_OR_EQUAL_ALPH]: 'Less or equal a-z',
    [ACCEPTABLE_CONDITIONS.LESS_OR_EQUAL_DATE]: 'Less or equal date',

    [ACCEPTABLE_CONDITIONS.NOT_BETWEEN]: 'Not between',
    [ACCEPTABLE_CONDITIONS.NOT_BETWEEN_ALPH]: 'Not between a-z',
    [ACCEPTABLE_CONDITIONS.NOT_EQUAL]: 'Not equal',
    [ACCEPTABLE_CONDITIONS.NOT_EQUAL_ALPH]: 'Not equal a-z',
    [ACCEPTABLE_CONDITIONS.NOT_BETWEEN_DATE]: 'Not between date',

    [ACCEPTABLE_CONDITIONS.REGEXP]: 'Regexp'

};
