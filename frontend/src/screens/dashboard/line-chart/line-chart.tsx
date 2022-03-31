// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './line-chart.scss';

import autoBind from 'autobind-decorator';
import cx from 'classnames';
import React, { Component } from 'react';

import { Spinner } from '@fluentui/react/lib/Spinner';
import {
    LineSvgProps,
    ResponsiveLine,
    SliceTooltipProps,
} from '@nivo/line';

import { WarningChartMessage } from '../warning-chart-message';
import { generateTicksValues } from './generate-ticks-values';
import { SliceTooltip } from './slice-tooltip';

const CN = 'line-chart';

const linearGradientDef = (id: string, colors: any[], options = {}) => ({
    id,
    type: 'linearGradient',
    colors,
    ...options,
});

interface LineChartProps extends LineSvgProps {
    /**
     * chartClassName - class name for the chart wrapper for the outside customization
     */
    chartClassName?: string;

    /**
     * noSelectedItemsWarningMessage - a warning message when no selection has been made
     */
    noSelectedItemsWarningMessage?: string;

    /**
     * noDataWarningMessage - a warning message when no data for requested period
     */
    noDataWarningMessage?: string;

    /**
     * hasSelectedItems - indicates whether user selected at least one item in
     * table, to display data on the chart, otherwise no selected items message will
     * be displayed
     */
    hasSelectedItems?: boolean

    /**
     * hasData - indicates whether itself store has a data [store data].length is
     * greater the 0
     */
    hasData?: boolean
    isLoading?: boolean;
    analystChart?: boolean;
    maxYTicksValue?: number;

    /**
     * maxTicksValuesCount - maximum number of ticks that could fit to the chart
     */
    maxTicksValuesCount?: number

    showPercentage?: boolean
}

@autoBind
export class LineChart extends Component<LineChartProps, never> {
    static defaultProps = {
        chartClassName: undefined,
        noSelectedItemsWarningMessage: undefined,
        noDataWarningMessage: undefined,
        hasSelectedItems: false,
        hasData: false,
        isLoading: false,
        analystChart: false,
        maxYTicksValue: 100,
        maxTicksValuesCount: 100,
        showPercentage: false
    };

    /**
     * Specify values to use for vertical grid lines
     *
     * By default all values that contains in single serie datum are taken
     * (showing all grid vertical lines for each datum)
     */
    getGridXValues() {
        const { data } = this.props;

        if (data.length) {
            const singleSerieData = data[0].data;
            return singleSerieData.map(datum => datum.x as Date);
        }

        return [];
    }

    renderSpinner() {
        return <Spinner className={`${CN}__spinner`} label="Wait! Loading chart data ..." />;
    }

    renderNoDataWarningMessage() {
        const { isLoading, hasData, noDataWarningMessage } = this.props;

        if (!hasData && !isLoading && noDataWarningMessage) {
            return (
                <WarningChartMessage message={noDataWarningMessage} />
            );
        }

        return null;
    }

    renderNoSelectedItemsWarningMessage() {
        const {
            isLoading, noSelectedItemsWarningMessage, hasSelectedItems, hasData
        } = this.props;

        const hasDataNoSelected = (hasData && !hasSelectedItems);

        if (hasDataNoSelected && !isLoading && noSelectedItemsWarningMessage) {
            return (
                <WarningChartMessage message={noSelectedItemsWarningMessage} />
            );
        }

        return null;
    }

    render() {
        const {
            isLoading,
            maxYTicksValue,
            analystChart,
            hasData,
            data,
            chartClassName,
            maxTicksValuesCount,
            showPercentage
        } = this.props;

        return (
            <div className={cx(CN, { [`${CN}--empty`]: !hasData && typeof hasData !== 'undefined' })}>
                <div className={cx(`${CN}__chart`, { [`${CN}__chart--blured`]: isLoading }, chartClassName)}>
                    <ResponsiveLine
                        /* eslint-disable-next-line react/jsx-props-no-spreading */
                        sliceTooltip={(props: SliceTooltipProps) => <SliceTooltip {...props} withAvatar={analystChart} showSummaryRow />}
                        xScale={{
                            type: 'time',
                            format: 'native',
                            useUTC: false,
                        }}
                        yScale={{
                            type: 'linear',
                            min: 0,
                            // TODO: Ticks might duplicated if the number < 10. Find a better way to fix this issue
                            max: maxYTicksValue
                        }}
                        gridXValues={this.getGridXValues()}
                        axisBottom={{
                            format: '%m/%d',
                            tickValues: data.length ? generateTicksValues(data, maxTicksValuesCount) : [],
                            legendOffset: -12,
                        }}
                        axisLeft={{
                            tickSize: 5,
                            format: showPercentage ? v => `${v}%` : 'd',
                            // TODO: A first digit always disappear if the number >= 1000. Find a better way to fix this issue
                            tickPadding: (maxYTicksValue && maxYTicksValue >= 1000) ? 0 : 5,
                        }}
                        margin={{
                            top: 30, bottom: 30, left: 40, right: 20
                        }}
                        isInteractive={hasData}
                        useMesh
                        enableSlices="x"
                        colors={{ datum: 'color' }}
                        defs={[
                            linearGradientDef('gradientA', [
                                { offset: 0, color: 'inherit' },
                                { offset: 100, color: 'inherit', opacity: 0 },
                            ]),
                        ]}
                        fill={[{ match: '*', id: 'gradientA' }]}
                        enablePoints={false}
                        /*  eslint-disable-next-line react/jsx-props-no-spreading */
                        {...this.props}
                    />
                </div>
                {isLoading && this.renderSpinner()}
                {this.renderNoDataWarningMessage()}
                {this.renderNoSelectedItemsWarningMessage()}
            </div>
        );
    }
}
