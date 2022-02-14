// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import {
    ACCEPTABLE_CONDITIONS,
    CONDITION_TILE_DISPLAY_NAME,
    CONDITION_TILE_NAMES,
    DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS,
    TILE_GROUPS
} from '../../../constants';

import { formatter } from './formatter-fn';

export const ON_OFF_STATE_TEXT = {
    ON: 'Yes',
    OFF: 'No'
};

export function getConditionDisplayNameFormatter(type: ACCEPTABLE_CONDITIONS): formatter {
    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.BETWEEN].includes(type)) {
        return ([min, max]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.BETWEEN]} (${min} - ${max})`;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.CONTAINS].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.CONTAINS]} ${value}`;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.EQUAL].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.EQUAL]} ${value}`;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.GREATER].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.GREATER]} ${value} `;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.GREATER_OR_EQUAL].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.GREATER_OR_EQUAL]} ${value} `;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.IN].includes(type)) {
        return (values: string[] | Date[]) => {
            const concatenatedValues = values.join(', ');

            return `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.IN]} (${concatenatedValues}) `;
        };
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.IS_TRUE].includes(type)) {
        return ([value]) => {
            const isChecked = value === 'true';

            return isChecked
                ? ON_OFF_STATE_TEXT.ON
                : ON_OFF_STATE_TEXT.OFF;
        };
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.LESS].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.LESS_THAN]} ${value} `;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.LESS_OR_EQUAL].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.LESS_OR_EQUAL]} ${value} `;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.NOT_EQUAL].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.NOT_EQUAL]} ${value}`;
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.NOT_BETWEEN].includes(type)) {
        return (([min, max]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.NOT_BETWEEN]} (${min} - ${max})`);
    }

    if (DISPLAY_TILES_ACCEPTABLE_CONDITIONS_GROUPS[TILE_GROUPS.REGEXP].includes(type)) {
        return ([value]) => `${CONDITION_TILE_DISPLAY_NAME[CONDITION_TILE_NAMES.REGEXP]} (${value})`;
    }

    throw new Error(`No matched function found for acceptable condition type: ${type}, in`
        + 'getSymbolicAcceptableConditionFormatterFunc mapper function');
}
