// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React from 'react';
import cx from 'classnames';

import { Spinner, ISpinnerProps } from '@fluentui/react/lib/Spinner';

import './blur-loader.scss';

interface BlueLoaderProps {
    isLoading: boolean
    spinnerProps?: ISpinnerProps
    className?: string,
    children?: any
}

const CN = 'blur-loader';

export class BlurLoader extends React.Component<BlueLoaderProps, never> {
    static defaultProps = {
        children: undefined,
        spinnerProps: undefined,
        className: ''
    };

    renderSpinner() {
        const { spinnerProps } = this.props;

        return (
            <div className={`${CN}__spinner-container`}>
                <Spinner
                    className={cx(`${CN}__spinner`, spinnerProps!.className)}
                    /* eslint-disable-next-line react/jsx-props-no-spreading */
                    {...spinnerProps}
                />
            </div>
        );
    }

    render() {
        const { children, isLoading, className } = this.props;
        return (
            <div className={cx(CN, className)}>
                <div className={cx(`${CN}__content`, { [`${CN}__content--blured`]: isLoading })}>
                    {children}
                </div>
                {isLoading && this.renderSpinner()}
            </div>
        );
    }
}
