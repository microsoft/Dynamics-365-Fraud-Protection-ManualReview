// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './bar-chart.scss';

import autobind from 'autobind-decorator';
import cx from 'classnames';
import React from 'react';

import { BarExtendedDatum, BarSvgProps, ResponsiveBar, } from '@nivo/bar';

import {
    OVERTURNED_DECISIONS_DISPLAY_NAMES,
    OVERTURN_CHART_DATUM_KEYS,
    OVERTURN_CHART_KEYS,
    OVERTURN_LABELS_TO_OVERTURN_CHART_KEYS_COLORS
} from '../../../constants';
import { formatDateToFullMonthDayYear } from '../../../utils/date';
import { AccuracyChartDatum } from '../../../view-services/dashboard/base-overturned-performance-store';
import { WarningChartMessage } from '../warning-chart-message';
import { generateTicksValues } from './generate-ticks-values';

interface OverturnedChartDatumAccumulator {
    good: number,
    overturnedGood: number,
    bad: number,
    overturnedBad: number
}

/**
 * Returns dynamically calculated keys and colors for the responsive bar chart
 *
 * This function is intentionally create in order to resolve the issue when some of the datum points
 * has 0, nullish values if that is that case, then some of the provided colors under color property
 * of Responsive bar would be shifted in unpredictable orders, and it brakes representation of actual
 * data to be displayed on the dashboard, thus such solution will aim to avoid such issue
 *
 * For more details please refer:
 * @see https://github.com/plouc/nivo/issues/952
 * @see https://github.com/plouc/nivo/issues/952#issuecomment-688245940
 * @see https://github.com/plouc/nivo/issues/986
 * @see https://github.com/plouc/nivo/issues/1031
 *
 * @returns Object<{ colors, keys }>  -  colors - array of colors representing in a string, keys - array of keys
 */
function getBarChartKeysAndColorsValues(data: AccuracyChartDatum[]) {
    const keys: string[] = [];
    const colors: string[] = [];

    // order of the keys must be in the defined order for the representation
    const dataSum: OverturnedChartDatumAccumulator = data.reduce((acc, next) => ({
        good: acc.good + Math.abs(next.good),
        overturnedGood: acc.overturnedGood + Math.abs(next.overturnedGood),
        bad: acc.bad + Math.abs(next.bad),
        overturnedBad: acc.overturnedBad + Math.abs(next.overturnedBad),

    }), {
        overturnedGood: 0, good: 0, bad: 0, overturnedBad: 0
    });

    Object.keys(dataSum).forEach(key => {
        const value = dataSum[key as keyof OverturnedChartDatumAccumulator];

        // filter keys if every decision exists (overturnedGood, good, bad, overturnedBad)
        // total sum is greater then 0
        if (value > 0) {
            const overturnedChartDatumKey = OVERTURN_CHART_DATUM_KEYS[key as keyof OverturnedChartDatumAccumulator];
            const { color, label } = OVERTURN_LABELS_TO_OVERTURN_CHART_KEYS_COLORS.get(overturnedChartDatumKey)!;
            keys.push(label);
            colors.push(color);
        }
    });

    return {
        keys, colors
    };
}

interface BarChartProps extends BarSvgProps {
    className?: string;
    isDataLoading?: boolean;
    noSelectedItemsWarningMessage?: string;
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
}

const CN = 'bar-chart';

export class BarChart extends React.Component<BarChartProps, never> {
    static defaultProps = {
        isDataLoading: false,
        noSelectedItemsWarningMessage: undefined,
        noDataWarningMessage: undefined,
        hasSelectedItems: false,
        hasData: false
    };

    static getLabels() {
        const good = OVERTURNED_DECISIONS_DISPLAY_NAMES[OVERTURN_CHART_KEYS.GOOD];
        const overturnedGood = OVERTURNED_DECISIONS_DISPLAY_NAMES[OVERTURN_CHART_KEYS.OVERTURNED_GOOD];
        const bad = OVERTURNED_DECISIONS_DISPLAY_NAMES[OVERTURN_CHART_KEYS.BAD];
        const overturnedBad = OVERTURNED_DECISIONS_DISPLAY_NAMES[OVERTURN_CHART_KEYS.OVERTURNED_BAD];

        return {
            good,
            overturnedGood,
            bad,
            overturnedBad
        };
    }

    renderNoDataWarningMessage() {
        const { isDataLoading, hasData, noDataWarningMessage } = this.props;

        if (!hasData && !isDataLoading && noDataWarningMessage) {
            return (
                <WarningChartMessage
                    className={`${CN}__no-data-warning-message`}
                    message={noDataWarningMessage}
                />
            );
        }

        return null;
    }

    renderNoSelectedItemsWarningMessage() {
        const {
            isDataLoading, noSelectedItemsWarningMessage, hasSelectedItems, hasData
        } = this.props;

        const hasDataNoSelected = (hasData && !hasSelectedItems);

        if (hasDataNoSelected && !isDataLoading && noSelectedItemsWarningMessage) {
            return (
                <WarningChartMessage
                    className={`${CN}__no-selected-items-warning-message`}
                    message={noSelectedItemsWarningMessage}
                />
            );
        }

        return null;
    }

    renderTooltipColorIndicator(color: string) {
        return (
            <div
                className={`${CN}__color-indicator`}
                style={{
                    background: color
                }}
            />
        );
    }

    renderTopLegends() {
        const {
            good,
            overturnedGood,
            bad,
            overturnedBad
        } = BarChart.getLabels();

        return (
            <div className={`${CN}__top-legend`}>
                <div>
                    <div style={{ background: good.color }} className={`${CN}__legend-color-indicator`} />
                    <div
                        style={{ color: good.color, filter: 'brightness(0.9)' }}
                        className={`${CN}__legend-label-text`}
                    >
                        {good.label}
                    </div>
                </div>
                <div>
                    <div style={{ background: bad.color }} className={`${CN}__legend-color-indicator`} />
                    <div
                        style={{ color: bad.color, filter: 'brightness(0.9)' }}
                        className={`${CN}__legend-label-text`}
                    >
                        {bad.label}
                    </div>
                </div>
                <div>
                    <div style={{ background: overturnedGood.color }} className={`${CN}__legend-color-indicator`} />
                    <div
                        style={{ color: good.color }}
                        className={`${CN}__legend-label-text`}
                    >
                        {overturnedGood.label}
                    </div>
                </div>
                <div>
                    <div style={{ background: overturnedBad.color }} className={`${CN}__legend-color-indicator`} />
                    <div
                        style={{ color: bad.color }}
                        className={`${CN}__legend-label-text`}
                    >
                        {overturnedBad.label}
                    </div>
                </div>
            </div>
        );
    }

    @autobind
    renderTooltip(extendedDatum: BarExtendedDatum) {
        const formatValue = (value: number) => `${Math.abs(value)}%`;
        const {
            id,
            data: {
                originalDate,
                overturnedGood: overturnedGoodValue,
                good: goodValue,
                bad: badValue,
                overturnedBad: overturnedBadValue,
            }
        } = extendedDatum as unknown as { data: AccuracyChartDatum } & BarExtendedDatum;
        const {
            good,
            overturnedGood,
            bad,
            overturnedBad
        } = BarChart.getLabels();

        return (
            <div className={`${CN}__tooltip`} key={id}>
                <div className={`${CN}__tooltip-header`}>
                    <strong>{formatDateToFullMonthDayYear(new Date(originalDate))}</strong>
                </div>
                <div className={`${CN}__tooltip-content`}>
                    <div className={`${CN}__tooltip-row`}>
                        {this.renderTooltipColorIndicator(overturnedGood.color)}
                        <div>{overturnedGood.label}</div>
                        <div className={`${CN}__tooltip-value`}>{formatValue(overturnedGoodValue)}</div>
                    </div>
                    <div className={`${CN}__tooltip-row`}>
                        {this.renderTooltipColorIndicator(good.color)}
                        <div>{good.label}</div>
                        <div className={`${CN}__tooltip-value`}>{formatValue(goodValue)}</div>
                    </div>
                    <div className={`${CN}__tooltip-row`}>
                        {this.renderTooltipColorIndicator(bad.color)}
                        <div>{bad.label}</div>
                        <div className={`${CN}__tooltip-value`}>{formatValue(badValue)}</div>
                    </div>
                    <div className={`${CN}__tooltip-row`}>
                        {this.renderTooltipColorIndicator(overturnedBad.color)}
                        <div>{overturnedBad.label}</div>
                        <div className={`${CN}__tooltip-value`}>{formatValue(overturnedBadValue)}</div>
                    </div>
                </div>
            </div>
        );
    }

    render() {
        const { className, data } = this.props;
        const { keys, colors } = getBarChartKeysAndColorsValues(data as AccuracyChartDatum[]);

        return (
            <div className={cx(CN, className)}>
                <ResponsiveBar
                    tooltip={this.renderTooltip}
                    indexBy="key"
                    padding={0.2}
                    labelSkipWidth={32}
                    labelSkipHeight={14}
                    enableGridX
                    enableGridY
                    margin={{
                        top: 40, right: 50, bottom: 40, left: 40
                    }}
                    axisRight={{
                        tickSize: 5, tickPadding: 5, tickRotation: 0, legend: '', legendOffset: 0, format: v => `${v}%`
                    }}
                    axisLeft={null}
                    axisBottom={{
                        tickValues: generateTicksValues(data as AccuracyChartDatum[])
                    }}
                    minValue={-100}
                    maxValue={100}
                    keys={keys}
                    colors={colors}
                    labelFormat={v => `${v}%`}
                    label={d => `${Math.abs(+d.value)}`}
                    animate
                    isInteractive
                    /* eslint-disable-next-line react/jsx-props-no-spreading */
                    {...this.props}
                    theme={{
                        tooltip: {
                            container: {
                                minWidth: 280,
                                padding: 16,
                                background: 'white',
                                boxShadow: '0 6.4px 14.4px rgba(0, 0, 0, 0.13),0 1.2px 3.6px rgba(0, 0, 0, 0.1)',
                                borderRadius: 2
                            }
                        }
                    }}
                />
                <div className={`${CN}__good-label`}>GOOD</div>
                <div className={`${CN}__bad-label`}>BAD</div>
                {this.renderTopLegends()}
                {this.renderNoDataWarningMessage()}
                {this.renderNoSelectedItemsWarningMessage()}
                <div className={`${CN}__baseline`} />
            </div>
        );
    }
}
