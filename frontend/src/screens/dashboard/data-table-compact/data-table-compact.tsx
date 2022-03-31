// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autoBind from 'autobind-decorator';
import { observer } from 'mobx-react';
import cx from 'classnames';

import { Text } from '@fluentui/react/lib/Text';
import { Checkbox } from '@fluentui/react/lib/Checkbox';
import { DetailsListLayoutMode, IColumn, SelectionMode, } from '@fluentui/react/lib/DetailsList';
import { Persona, PersonaSize, IPersonaProps } from '@fluentui/react/lib/Persona';
import { ShimmeredDetailsList } from '@fluentui/react/lib/ShimmeredDetailsList';

import { AnalystPerformance, BasicEntityPerformance } from '../../../models/dashboard';
import { DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER } from '../../../constants';
import './data-table-compact.scss';

const CN = 'data-table-compact';

interface DataGridListProps<T> {
    layoutMode?: DetailsListLayoutMode,
    isAnalystTable?: boolean;
    className?: string;
    isLoading: boolean;
    hideArrow?: boolean
    data: T[] | null;
    onRowClick?(selectedItem: any): void;
    onRowSelection(queueId: string, checked?: boolean): void
}

@observer
export class DataTableCompact<T extends BasicEntityPerformance> extends Component<DataGridListProps<T>, never> {
    static defaultProps = {
        isAnalystTable: false,
        layoutMode: undefined,
        className: '',
        onRowClick: undefined,
        hideArrow: false
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
            key: 'reviewed',
            name: 'Reviewed',
            minWidth: 100,
            maxWidth: 100,
            className: `${CN}__right-aligned-cell ${CN}__sorting-arrow`,
            onRender: ({ total }: T) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {total.reviewed}
                    </Text>
                </div>
            ),
        },
        {
            key: 'good',
            name: 'Good',
            minWidth: 100,
            maxWidth: 100,
            className: `${CN}__right-aligned-cell`,
            onRender: (queue: T) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {queue.total.good}
                    </Text>
                    <div className={cx(`${CN}__cell-label`, `${CN}__cell-label--good`)}>
                        <span>
                            {queue.goodDecisionsRatio}
                            %
                        </span>
                    </div>
                </div>
            ),
        },
        {
            key: 'watched',
            name: 'Watch',
            minWidth: 100,
            maxWidth: 100,
            className: `${CN}__right-aligned-cell`,
            onRender: (queue: T) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {queue.total.watched}
                    </Text>
                    <div className={cx(`${CN}__cell-label`, `${CN}__cell-label--watched`)}>
                        <span>
                            {queue.watchDecisionsRatio}
                            %
                        </span>
                    </div>
                </div>
            ),
        },
        {
            key: 'bad',
            name: 'Bad',
            minWidth: 100,
            maxWidth: 100,
            className: `${CN}__right-aligned-cell`,
            onRender: (queue: T) => (
                <div className={`${CN}__content-row`}>
                    <Text variant="medium" className={`${CN}__score-cell`}>
                        {queue.total.bad}
                    </Text>
                    <div className={cx(`${CN}__cell-label`, `${CN}__cell-label--bad`)}>
                        <span>
                            {queue.badDecisionsRatio}
                            %
                        </span>
                    </div>
                </div>
            ),
        }
    ];

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
                            className={`${CN}__first-column-content`}
                        >
                            <Text
                                role="button"
                                variant="medium"
                                onClick={onRowClick ? () => onRowClick(entity) : undefined}
                                className={cx(
                                    `${CN}__score-cell`,
                                    { [`${CN}__clickable-row`]: !!onRowClick }
                                )}
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
