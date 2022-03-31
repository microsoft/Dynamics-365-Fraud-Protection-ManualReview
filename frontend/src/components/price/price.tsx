// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { Text } from '@fluentui/react/lib/Text';
import React, { Component } from 'react';

interface PriceProps {
    className?: string;
    currencySign?: string;
    digitsAmount?: number;
    signAsSuffix?: boolean;
    value: number;
}

export class Price extends Component<PriceProps, never> {
    static defaultProps = {
        currencySign: null,
        digitsAmount: 0,
        signAsSuffix: false,
        className: ''
    };

    getPriceString() {
        const {
            value,
            currencySign,
            digitsAmount,
            signAsSuffix
        } = this.props;

        if (!value) return '$0.00';

        const humanValue = value.toFixed(digitsAmount || 2);

        if (signAsSuffix) {
            return `${humanValue}${currencySign || '$'}`;
        }

        return `${currencySign || '$'}${humanValue}`;
    }

    render() {
        const { className } = this.props;

        return (
            <Text variant="medium" className={className}>
                {this.getPriceString()}
            </Text>
        );
    }
}
