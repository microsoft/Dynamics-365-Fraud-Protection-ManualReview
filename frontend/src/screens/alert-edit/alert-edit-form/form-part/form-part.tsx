// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { Text } from '@fluentui/react/lib/Text';
import classNames from 'classnames';
import React, { Component } from 'react';
import { observer } from 'mobx-react';
import './alert-edit-form-part.scss';

const CN = 'alert-edit-form-part';

interface AlertEditFormPartProps {
    className?: string;
    contentClassName?: string;
    title?: string;
    children: JSX.Element;
}

@observer
export class AlertEditFormPart extends Component<AlertEditFormPartProps, never> {
    static defaultProps = {
        title: null,
        contentClassName: '',
        className: ''
    };

    render() {
        const {
            title,
            children,
            className,
            contentClassName
        } = this.props;

        return (
            <div className={classNames(CN, className)}>
                { !!title && (
                    <Text
                        variant="large"
                        className={`${CN}__title`}
                    >
                        {title}
                    </Text>
                )}
                <div className={classNames(`${CN}__content`, contentClassName)}>
                    {children}
                </div>
            </div>
        );
    }
}
