// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './data-table.scss';

import cx from 'classnames';
import React from 'react';

import { IColumn, SelectionMode } from '@fluentui/react/lib/DetailsList';
import { Facepile } from '@fluentui/react/lib/Facepile';
import { FontIcon } from '@fluentui/react/lib/Icon';
import { PersonaSize } from '@fluentui/react/lib/Persona';
import { ShimmeredDetailsList } from '@fluentui/react/lib/ShimmeredDetailsList';
import { Text } from '@fluentui/react/lib/Text';
import { TooltipHost } from '@fluentui/react/lib/Tooltip';

import NormalClockIcon from '../../../../assets/icon/normal-clock.svg';
import RiskClockIcon from '../../../../assets/icon/risk-clock.svg';
import {
    DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER,
    DEFAULT_HIGH_RISK_DAYS_COUNT
} from '../../../../constants';
import { Item } from '../../../../models/item';

interface DataTableProps<T extends Item> {
    data: T[] | null;
    onClickCallback(item: T): void;
    isDataLoading?: boolean;
}

const CN = 'data-table';

export class DataTable<T extends Item> extends React.Component<DataTableProps<T>, never> {
    static defaultProps = {
        isDataLoading: false
    };

    private readonly columns: IColumn[] = [
        {
            key: 'fraud-score',
            name: 'Fraud score',
            minWidth: 80,
            maxWidth: 80,
            headerClassName: `${CN}__data-header-cell-fraud-score`,
            onRender: (item: Item) => (
                <div className={cx(`${CN}__data-cell`, `${CN}__data-cell-fraud-score`)}>
                    <Text variant="medium" className={cx(`${CN}__score-cell`, `${CN}__fraud-score`)}>
                        {item.decision?.riskScore}
                    </Text>
                </div>
            ),

        },
        {
            key: 'order-id',
            name: 'Purchase ID',
            minWidth: 100,
            headerClassName: `${CN}__data-header-cell`,
            onRender: (item: Item) => {
                const { onClickCallback } = this.props;

                return (
                    <div className={`${CN}__queue-name-cell`}>
                        <button type="button" className={`${CN}__go-to-queue-db-btn`} onClick={() => onClickCallback(item as T)}>
                            <Text variant="medium" className={`${CN}__score-cell`}>
                                {item.id}
                            </Text>
                        </button>
                    </div>
                );
            }
        },
        {
            key: 'amount',
            name: 'Amount',
            minWidth: 80,
            maxWidth: 80,
            headerClassName: `${CN}__data-header-cell`,
            onRender: ({ amount }) => (
                <div className={`${CN}__data-cell`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {amount}
                    </Text>
                </div>
            ),
        },
        {
            key: 'import-date',
            name: 'Import date',
            minWidth: 100,
            maxWidth: 100,
            headerClassName: `${CN}__data-header-cell`,
            onRender: ({ displayImportDateTime }) => (
                <div className={`${CN}__data-cell`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {displayImportDateTime}
                    </Text>
                </div>
            ),
        },
        {
            key: 'timeout',
            name: 'Timeout',
            minWidth: 70,
            maxWidth: 70,
            headerClassName: `${CN}__data-header-cell`,
            onRender: (item: Item) => {
                let displayTimeout: string | number | JSX.Element = this.renderAbsentValue();

                if (item.timeout !== undefined) {
                    displayTimeout = item.timeout > 0
                        ? (
                            <div>
                                {item.timeout}
                                <span className={`${CN}__min-text`}>min</span>
                            </div>
                        )
                        : (<abbr title="expired" className={`${CN}__time-left-text--overdue-text`}>expired</abbr>);
                }

                return (
                    <div className={cx(`${CN}__data-cell`, `${CN}__data-cell-timeout`)}>
                        <Text variant="medium" className={`${CN}__score-cell`}>
                            {displayTimeout}
                        </Text>
                    </div>
                );
            }
        },
        {
            key: 'time-left',
            name: 'Time left',
            minWidth: 80,
            maxWidth: 80,
            headerClassName: `${CN}__data-header-cell`,
            onRender: (item: T) => (
                <div className={`${CN}__data-cell`}>
                    { item.timeLeft && item.timeLeft.days && (this.renderTimeLeft(item.timeLeft.days)) }
                </div>
            ),
        },
        {
            key: 'analyst',
            name: 'Analyst',
            minWidth: 80,
            maxWidth: 80,
            headerClassName: cx(`${CN}__data-header-cell`, `${CN}__analysts-header-cell`),
            className: `${CN}__analysts-col`,
            onRender: (item: Item) => {
                if (!item.analyst) { return null; }

                return (
                    <Facepile
                        personaSize={PersonaSize.size24}
                        personas={item.analystAsPersons}
                        getPersonaProps={() => ({ hidePersonaDetails: true })}
                    />
                );
            }
        }
    ];

    renderTimeLeft(timeLeft: number) {
        const dayOrDaysText = timeLeft === 1 ? 'day' : 'days';

        const svgIcon = (timeLeft !== null) && (timeLeft <= DEFAULT_HIGH_RISK_DAYS_COUNT)
            ? (<RiskClockIcon />)
            : (<NormalClockIcon />);

        return (
            <div className={`${CN}__time-left`}>
                {svgIcon}
                {
                    (timeLeft <= 0) ? (
                        <span className={`${CN}__time-left-text--overdue-text`}>
                            <abbr title={`${Math.abs(timeLeft)}`}>Overdue</abbr>
                        </span>
                    ) : (
                        <Text variant="smallPlus" className={`${CN}__time-left-text`}>
                            {timeLeft}
                            {' '}
                            {dayOrDaysText}
                        </Text>
                    )
                }
            </div>
        );
    }

    renderAbsentValue() {
        return <div className={`${CN}__absent-value`} />;
    }

    renderTimeoutTooltipContent() {
        return (
            <div className={`${CN}__tooltip-content`}>
                <div>1,2..5</div>
                <div>- order will be unlocked in ... min</div>
                <div>{this.renderAbsentValue()}</div>
                <div>- order is not taken by an analyst</div>
                <div><abbr title="expired" className={`${CN}__risk-value`}>expired</abbr></div>
                <div>- lock time has just expired</div>
            </div>
        );
    }

    renderTimeoutInfoIcon() {
        return (
            <div className={`${CN}__info-icon`}>
                <TooltipHost content={this.renderTimeoutTooltipContent()}>
                    <FontIcon iconName="Info" />
                </TooltipHost>
            </div>
        );
    }

    render() {
        const { data } = this.props;
        return (

            <div className={`${CN}__table-wrap`}>
                <ShimmeredDetailsList
                    enableShimmer={!data}
                    className={`${CN}__table`}
                    selectionMode={SelectionMode.none}
                    columns={this.columns}
                    items={data || []}
                    shimmerLines={DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER}
                />
                {this.renderTimeoutInfoIcon()}
            </div>
        );
    }
}
