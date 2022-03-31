// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autoBind from 'autobind-decorator';
import { observer } from 'mobx-react';
import cx from 'classnames';

import { Text } from '@fluentui/react/lib/Text';
import { Checkbox } from '@fluentui/react/lib/Checkbox';
import { DetailsListLayoutMode, IColumn, SelectionMode } from '@fluentui/react/lib/DetailsList';
import { IPersonaProps, Persona, PersonaSize } from '@fluentui/react/lib/Persona';
import { ShimmeredDetailsList } from '@fluentui/react/lib/ShimmeredDetailsList';

import { AnalystPerformance, BasicEntityPerformance } from '../../../models/dashboard';
import {
    DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER,
    OVERTURN_DISPLAY_LABELS,
    OVERTURN_LABELS
} from '../../../constants';

import './accuracy-data-table.scss';

const CN = 'accuracy-data-table';

interface DataGridListProps<T> {
    isAnalystTable?: boolean;
    className?: string;
    isLoading: boolean;
    data: T[] | null;
    onRowClick?(selectedItem: any): void;
    onRowSelection(queueId: string, checked?: boolean): void
}

@observer
export class AccuracyDataTable<T extends BasicEntityPerformance> extends Component<DataGridListProps<T>, never> {
    static defaultProps = {
        onRowClick: undefined,
        isAnalystTable: false,
        className: ''
    };

    private readonly columns: IColumn[] = [
        {
            // eslint-disable-next-line react/destructuring-assignment
            key: this.props.isAnalystTable ? 'analyst' : ' queue',
            // eslint-disable-next-line react/destructuring-assignment
            name: this.props.isAnalystTable ? 'Analyst' : 'Queue',
            minWidth: 50,
            onRender: this.renderFirstColumn,

        },
        {
            key: 'good-decisions-applied',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.GOOD],
            minWidth: 90,
            maxWidth: 90,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ goodApplied }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {goodApplied}
                    </Text>
                </div>
            ),
        },
        {
            key: 'overturned-good-decisions',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.OVERTURNED_GOOD],
            minWidth: 90,
            maxWidth: 90,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ goodOverturned }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {goodOverturned}
                    </Text>
                </div>
            ),
        },
        {
            key: 'good-decision-overturn-rate',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.GOOD_DECISION_OVERTURN_RATE],
            minWidth: 110,
            maxWidth: 110,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ goodOverturnRate }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text
                        variant="medium"
                        className={cx(
                            `${CN}__score-cell`,
                            this.getColorClassName(goodOverturnRate)
                        )}
                    >
                        {goodOverturnRate}
                        %
                    </Text>
                </div>
            ),
        },
        {
            key: 'bad-decisions-applied',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.BAD],
            minWidth: 80,
            maxWidth: 80,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ badApplied }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {badApplied}
                    </Text>
                </div>
            ),
        }, {
            key: 'overturned-bad-decisions',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.OVERTURNED_BAD],
            minWidth: 80,
            maxWidth: 80,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ badOverturned }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {badOverturned}
                    </Text>
                </div>
            ),
        }, {
            key: 'bad-decision-overturn-rate',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.BAD_DECISION_OVERTURN_RATE],
            minWidth: 110,
            maxWidth: 110,
            className: `${CN}__right-aligned-cell`,
            onRender: ({ badOverturnRate }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text
                        variant="medium"
                        className={
                            cx(`${CN}__score-cell`,
                                this.getColorClassName(badOverturnRate))
                        }
                    >
                        {badOverturnRate}
                        %
                    </Text>
                </div>
            ),
        }, {
            key: 'average-overturn-rate',
            name: OVERTURN_DISPLAY_LABELS[OVERTURN_LABELS.AVERAGE_OVERTURN_RATE],
            minWidth: 110,
            maxWidth: 110,
            className: `${CN}__right-aligned-cell ${CN}__accuracy-sorting-arrow`,
            onRender: ({ averageOverturnRate }: BasicEntityPerformance) => (
                <div className={`${CN}__content-row`}>
                    <Text
                        variant="medium"
                        className={cx(
                            `${CN}__score-cell`,
                            `${CN}__good-column-text`,
                            this.getColorClassName(averageOverturnRate)
                        )}
                    >
                        {averageOverturnRate}
                        %
                    </Text>
                </div>
            ),
        },

    ];

    getColorClassName(score: number) {
        if (score <= 50) {
            return `${CN}__score-cell--low`;
        }

        if (score > 50 && score <= 80) {
            return `${CN}__score-cell--medium-low`;
        }

        if (score > 80 && score <= 90) {
            return `${CN}__score-cell--medium-high`;
        }

        if (score > 90) {
            return `${CN}__score-cell--high`;
        }

        return '';
    }

    renderUserRow(entity: BasicEntityPerformance) {
        const user = (entity as AnalystPerformance).analyst;
        if (user) {
            return (
                <Persona
                    showSecondaryText={!!(user.asPersona as IPersonaProps).secondaryText}
                    text={user.name}
                    secondaryText={(user.asPersona as IPersonaProps).secondaryText}
                    size={PersonaSize.size28}
                    className={`${CN}__analyst`}
                    imageUrl={user.imageUrl}
                    imageAlt={`analyst ${user.name}`}
                />
            );
        }

        return (
            <Persona
                text={`${entity.id}`}
                size={PersonaSize.size28}
                className={`${CN}__analyst`}
            />
        );
    }

    @autoBind
    renderFirstColumn(entity: BasicEntityPerformance) {
        const { onRowSelection, onRowClick, isAnalystTable } = this.props;

        const checkBoxStyles = {
            checkbox: {
                borderColor: `${entity.color} !important`,
            },
            checkmark: {
                color: 'white',
                backgroundColor: entity.color,
            }
        };

        return (
            <div className={`${CN}__content-row`}>
                <Checkbox
                    checked={entity.isChecked}
                    className={`${CN}__checkbox`}
                    onChange={(event, checked) => (onRowSelection(entity.id, checked))}
                    styles={checkBoxStyles}
                />

                {isAnalystTable
                    ? this.renderUserRow(entity)
                    : (
                        /* eslint-disable-next-line jsx-a11y/click-events-have-key-events */
                        <div
                            role="button"
                            tabIndex={-1}
                            className={`${CN}__first-column-content`}
                            onClick={onRowClick ? () => onRowClick(entity) : undefined}
                        >
                            <Text
                                variant="medium"
                                className={`${CN}__score-cell`}
                            >
                                {entity.name || entity.id}
                            </Text>
                        </div>
                    )}
            </div>
        );
    }

    render() {
        const {
            data, isLoading, className
        } = this.props;

        return (
            <div className={cx(CN, className)}>
                <ShimmeredDetailsList
                    enableShimmer={isLoading}
                    layoutMode={DetailsListLayoutMode.justified}
                    className={CN}
                    selectionMode={SelectionMode.none}
                    columns={this.columns}
                    items={data || []}
                    shimmerLines={DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER}
                />
            </div>
        );
    }
}
