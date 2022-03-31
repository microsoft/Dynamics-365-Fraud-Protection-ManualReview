// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autobind from 'autobind-decorator';
import cn from 'classnames';
import './tab.scss';

export interface TabsProps<T> {
    className?: string;
    /**
     * button - indicates that tab looks like a big button
     */
    button?: boolean;
    label: T;
    text: string;
    activeTab: any;
    onClick: (label: T) => void;
}

const CN = 'tab';

@autobind
export class Tab<T> extends Component<TabsProps<T>, never> {
    static defaultProps = {
        button: false,
        className: ''
    };

    handleClick() {
        const { onClick, label } = this.props;
        onClick(label);
    }

    render() {
        const {
            label,
            activeTab,
            text,
            className,
            button
        } = this.props;

        const isActiveLabelMatch = activeTab === label;

        return (
            <button
                key={`${label}`}
                type="button"
                onClick={this.handleClick}
                className={cn(
                    CN,
                    { [`${CN}--border`]: !button },
                    { [`${CN}-button`]: button },
                    { [`${CN}-button--is-active`]: isActiveLabelMatch && button },
                    { [`${CN}--is-active`]: isActiveLabelMatch && !button },
                    className
                )}
            >
                {text}
            </button>
        );
    }
}
