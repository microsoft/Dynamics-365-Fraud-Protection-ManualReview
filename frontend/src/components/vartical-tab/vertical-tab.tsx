// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autobind from 'autobind-decorator';
import cn from 'classnames';
import { Text } from '@fluentui/react/lib/Text';
import { FontIcon } from '@fluentui/react/lib/Icon';
import './vertical-tab.scss';

export interface VerticalTabProps {
    name: string;
    icon: string;
    tabKey: string;
    isActive: boolean;
    onClick: (key: string) => void;
    disabled?: boolean;
    className?: string;
}

@autobind
export class VerticalTab extends Component<VerticalTabProps, never> {
    static defaultProps = {
        disabled: false,
        className: ''
    };

    handleClick() {
        const { onClick, tabKey } = this.props;
        onClick(tabKey);
    }

    render() {
        const {
            icon,
            name,
            isActive,
            disabled,
            className
        } = this.props;

        return (
            <button
                type="button"
                onClick={this.handleClick}
                className={cn(className, 'vertical-tab', { selected: isActive })}
                disabled={disabled}
            >
                <div className="inner-area">
                    <FontIcon iconName={icon} className="tab-icon" />
                    <Text>{name}</Text>
                </div>
            </button>
        );
    }
}
