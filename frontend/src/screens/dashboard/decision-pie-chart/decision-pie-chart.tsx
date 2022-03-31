// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import cx from 'classnames';

import { PieChart } from '../pie-chart';
import { BlurLoader } from '../blur-loader';
import { WarningChartMessage } from '../warning-chart-message';

import { DecisionPieDatum } from '../../../models/dashboard';

import './decision-pie-chart.scss';

interface DecisionPieChartComponentProps {
    className: string;
    data: DecisionPieDatum[];
    isDataLoading: boolean;
}

const CN = 'decision-pie-chart';

export class DecisionPieChart extends Component<DecisionPieChartComponentProps, never> {
    getFilteredNotZeroPieData(data: DecisionPieDatum[]) {
        return data.filter(datum => datum.value !== 0);
    }

    renderDecisionPieChart() {
        const { isDataLoading, data } = this.props;

        const isAllPieDatumsEqualsZero = data.every(datum => !datum.value);
        const isDataNotAvailable = isAllPieDatumsEqualsZero && !isDataLoading;

        if (isDataNotAvailable) {
            return (
                <div className={`${CN}__empty-placeholder`}>
                    <WarningChartMessage
                        message="Total decisions metrics are not available"
                    />
                </div>
            );
        }

        return (
            <div className={`${CN}__chart-container`}>
                <BlurLoader
                    isLoading={isDataLoading}
                    spinnerProps={{
                        label: 'Please, wait! Loading chart data ...'
                    }}
                >
                    <PieChart
                        enableLegends
                        innerText="All decisions for the period"
                        data={this.getFilteredNotZeroPieData(data)}
                        className={`${CN}__pie-chart`}
                    />
                </BlurLoader>
            </div>
        );
    }

    render() {
        const { className } = this.props;

        return (
            <div className={cx(CN, className)}>
                {this.renderDecisionPieChart()}
            </div>
        );
    }
}
