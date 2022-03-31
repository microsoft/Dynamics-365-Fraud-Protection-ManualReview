// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './data-grid-list.scss';

import { DetailsListLayoutMode, IColumn, SelectionMode, } from '@fluentui/react/lib/DetailsList';
import { IPersonaProps, Persona, PersonaSize } from '@fluentui/react/lib/Persona';
import React, { Component } from 'react';

import { Checkbox } from '@fluentui/react/lib/Checkbox';
import { ShimmeredDetailsList } from '@fluentui/react/lib/ShimmeredDetailsList';
import { Text } from '@fluentui/react/lib/Text';
import autoBind from 'autobind-decorator';
import cx from 'classnames';
import { observer } from 'mobx-react';
import { observable } from 'mobx';
import { DEFAULT_DATA_LIST_SHIMMER_LINES_NUMBER } from '../../../constants';
import { AnalystPerformance, BasicEntityPerformance } from '../../../models/dashboard';
import { CSVReportBuilder } from '../../../utility-services';
import { AnalystPerformanceDetails } from '../../../models/dashboard/analyst-performance-details';
import { convertToCSVString } from '../../../utility-services/convert-service';
import { CSVDownloadButton } from '../../../components/csv-download-button/csv-download-button';

const CN = 'data-grid-list';

interface DataGridListProps<T> {
    layoutMode?: DetailsListLayoutMode,
    isAnalystTable?: boolean;
    className?: string;
    isLoading: boolean;
    data: T[] | null;
    onRowClick?(selectedItem: any): void;
    onRowSelection(queueId: string, checked?: boolean): void;
}

@observer
export class DataGridList<T extends BasicEntityPerformance> extends Component<DataGridListProps<T>, never> {
    static defaultProps = {
        isAnalystTable: false,
        layoutMode: undefined,
        className: '',
        onRowClick: undefined
    };

    @observable
    protected csvReportBuilder = new CSVReportBuilder();

    private get columns(): IColumn[] {
        const result = [
            {
            // eslint-disable-next-line react/destructuring-assignment
                key: this.props.isAnalystTable ? 'analyst' : ' queue',
                // eslint-disable-next-line react/destructuring-assignment
                name: this.props.isAnalystTable ? 'Analyst' : 'Queue',
                minWidth: 50,
                maxWidth: 600,
                onRender: this.renderFirstColumn,
            },
            {
                key: 'reviewed',
                name: 'Reviewed',
                minWidth: 50,
                maxWidth: 80,
                className: `${CN}__right-aligned-cell`,
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
                minWidth: 50,
                maxWidth: 80,
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
                minWidth: 50,
                maxWidth: 80,
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
                minWidth: 50,
                maxWidth: 80,
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
            },
            {
                key: 'badDecisionsRate',
                name: 'Bad decision rate',
                minWidth: 80,
                maxWidth: 120,
                className: `${CN}__right-aligned-cell`,
                onRender: (queue: T) => (
                    <div className={`${CN}__content-row`}>
                        <Text variant="medium" className={`${CN}__score-cell`}>
                            {queue.badDecisionsRatio}
                            %
                        </Text>
                    </div>
                ),
            }

        ];

        const { isAnalystTable } = this.props;

        if (isAnalystTable) {
            result.push(
                {
                    key: 'totalActionsApplied',
                    name: 'Total actions applied',
                    minWidth: 100,
                    maxWidth: 100,
                    className: `${CN}__right-aligned-cell`,
                    onRender: (queue: T) => (<CSVDownloadButton csvData={this.DataForDownload(queue.details)} fileName={queue.name} />),
                },
            );
        }

        return result;
    }

    DataForDownload(data: AnalystPerformanceDetails[] | undefined): string {
        if (data) {
            const dataForConversion = data.map(x => ({
                PurchaseId: x.id,
                Label: x.label,
                Decision: x.merchantRuleDecision,
                Link: `${window.location.origin}/search/xx/item/${x.id}`
            }));
            return convertToCSVString(dataForConversion);
        }
        return '';
    }

    renderUserRow(entity: BasicEntityPerformance) {
        const { onRowClick } = this.props;
        const user = (entity as AnalystPerformance).analyst;

        if (user) {
            return (
                <Persona
                    onClick={onRowClick ? () => onRowClick(user) : undefined}
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
            data, isLoading, className, layoutMode
        } = this.props;

        return (
            <div className={cx(CN, className)}>
                <ShimmeredDetailsList
                    enableShimmer={isLoading}
                    layoutMode={layoutMode || DetailsListLayoutMode.justified}
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
