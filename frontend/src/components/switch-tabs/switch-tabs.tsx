// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autoBind from 'autobind-decorator';
import cx from 'classnames';
import { Tab } from './tab';

import './switch-tabs.scss';

const CN = 'switch-tabs';

interface DataHeaderProps<T> {
    className?: string
    onViewChange: (label: T) => void
    viewMap: Map<string, string>,
    buttonLook?: boolean
    activeViewTab: T;

}

@autoBind
export class SwitchTabs<T> extends Component<DataHeaderProps<T>, never> {
    static defaultProps = {
        buttonLook: false,
        className: ''
    };

    handleViewSwitch(label: T) {
        const { onViewChange } = this.props;

        onViewChange(label);
    }

    render() {
        const {
            viewMap, buttonLook, className, activeViewTab
        } = this.props;

        return (
            <div className={cx(CN, className)}>
                <div className={`${CN}__switch-items`}>
                    {
                        Array.from(viewMap.entries())
                            .map(([key, value]) => (
                                <div key={key} className={`${CN}__switch-item`}>
                                    <Tab
                                        <T>
                                        key={key}
                                        button={buttonLook}
                                        className={cx({ [`${CN}__switch-tab`]: !buttonLook })}
                                        onClick={this.handleViewSwitch}
                                        activeTab={activeViewTab}
                                        label={key as any}
                                        text={value}
                                    />
                                </div>
                            ))
                    }
                </div>
            </div>
        );
    }
}
