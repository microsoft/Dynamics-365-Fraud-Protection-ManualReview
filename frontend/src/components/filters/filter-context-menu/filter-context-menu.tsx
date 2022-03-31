// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import { observer } from 'mobx-react';
import cx from 'classnames';

import { CommandBarButton } from '@fluentui/react/lib/Button';
import { FilterContextualMenuItem } from '../../../models/filter/selectable-options';

import './filter-context-menu.scss';

interface FilterMenuComponentProps {
    /**
     * items - contextual menu options (items)
     */
    items: FilterContextualMenuItem[];

    /**
     * buttonText - text to be display on the button
     */
    buttonText?: string;
    className?: string;
}

const CN = 'filter-context-menu';

@observer
export class FilterContextMenu extends Component<FilterMenuComponentProps, never> {
    static defaultProps = {
        buttonText: null,
        className: ''
    };

    render() {
        const { buttonText, items, className } = this.props;

        return (
            <CommandBarButton
                className={cx(CN, className)}
                text={buttonText}
                iconProps={{ iconName: 'Add' }}
                menuProps={{
                    items,
                }}
            />
        );
    }
}
