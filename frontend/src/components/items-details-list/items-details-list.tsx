// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './item-details-list.scss';

import autobind from 'autobind-decorator';
import cn from 'classnames';
import { History } from 'history';
import { resolve } from 'inversify-react';
import { observer } from 'mobx-react';
import React, { Component } from 'react';

import { CommandButton } from '@fluentui/react/lib/Button';
import { ContextualMenuItemType, IContextualMenuItem } from '@fluentui/react/lib/ContextualMenu';
import {
    ColumnActionsMode,
    DetailsList,
    DetailsListLayoutMode,
    IColumn,
    SelectionMode
} from '@fluentui/react/lib/DetailsList';
import { Facepile } from '@fluentui/react/lib/Facepile';
import { PersonaSize } from '@fluentui/react/lib/Persona';
import { Spinner } from '@fluentui/react/lib/Spinner';
import { Text } from '@fluentui/react/lib/Text';

import NormalClockIcon from '../../assets/icon/normal-clock.svg';
import RiskClockIcon from '../../assets/icon/risk-clock.svg';
import NoOrdersIllustrationSvg from '../../assets/no-orders-illustration.svg';
import {
    AVAILABLE_SORTING_FIELDS,
    ITEM_LIST_COLUMN_KEYS,
    ITEM_SORTING_FIELD,
    ROUTES,
    SORTING_ORDER
} from '../../constants';
import { ItemSortSettingsDTO } from '../../data-services/api-services/models';
import { ITEM_STATUS, Item, Queue, } from '../../models';
import { TYPES } from '../../types';
import { CurrentUserStore, QueueStore } from '../../view-services';
import { ItemsLoadable } from '../../view-services/misc/items-loadable';
import { ErrorContent } from '../error-content';
import { QueueItemNote } from './queue-item-notes/queue-item-notes';
import { QueueItemTags } from './queue-item-tags/queue-item-tags';

export interface ItemsDetailsListProps {
    queueStore: QueueStore;
    storeForItemsLoading: ItemsLoadable<Item>;
    handleLoadMoreRowsClick: () => void;
    handleSortingUpdate?: (sortingObject: ItemSortSettingsDTO) => void;
    searchId?: string;
    sortingObject?: ItemSortSettingsDTO | undefined;
    selectedQueue?: Queue | null;
    loadingMessage?: string;
    noItemsMessage?: string
    className?: string;
}

const CN = 'items-details-list';
const HIGH_RISK_DAYS_COUNT = 2;

@observer
export class ItemsDetailsList extends Component<ItemsDetailsListProps, never> {
    static defaultProps = {
        searchId: null,
        sortingObject: undefined,
        selectedQueue: null,
        loadingMessage: null,
        noItemsMessage: null,
        className: ''
    };

    @resolve(TYPES.CURRENT_USER_STORE)
    private user!: CurrentUserStore;

    @resolve(TYPES.HISTORY)
    private history!: History;

    private readonly columns: IColumn[] = [
        {
            key: ITEM_LIST_COLUMN_KEYS.FRAUD_SCORE,
            name: 'Fraud score',
            minWidth: 50,
            maxWidth: 120,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text variant="medium" className={`${CN}__score-cell`}>
                    {item.decision?.riskScore}
                </Text>
            ),
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.ORDER_ID,
            name: 'Purchase ID',
            fieldName: 'id',
            minWidth: 50,
            isCollapsible: true,
            isRowHeader: true,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text variant="smallPlus" className={`${CN}__order-id-cell`} title={item.id}>
                    {item.id}
                </Text>
            )
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.ORIGINAL_ORDER_ID,
            name: 'Original order ID',
            minWidth: 50,
            maxWidth: 200,
            isCollapsible: true,
            isRowHeader: true,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text variant="smallPlus" className={`${CN}__order-id-cell`} title={item.purchase?.originalOrderId}>
                    {item.purchase?.originalOrderId}
                </Text>
            )
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.QUEUES,
            name: 'Queues',
            fieldName: 'queues',
            minWidth: 200,
            maxWidth: 300,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => this.renderQueues(item),
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.TIME_LEFT,
            name: 'Time left',
            fieldName: 'timeLeft',
            minWidth: 80,
            maxWidth: 150,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => this.renderTimeLeft(item),
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.IMPORT_DATE,
            name: 'Import date',
            fieldName: 'importDateTime',
            minWidth: 90,
            maxWidth: 90,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text variant="smallPlus">
                    {item.displayImportDateTime}
                </Text>
            )
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.AMOUNT,
            name: 'Amount',
            minWidth: 70,
            maxWidth: 70,
            isPadded: true,
            className: `${CN}__cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text variant="smallPlus">
                    {item.amount}
                </Text>
            )
        },

        {
            key: ITEM_LIST_COLUMN_KEYS.STATUS,
            name: 'Status',
            minWidth: 60,
            maxWidth: 80,
            data: 'string',
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <Text
                    style={{ width: 'auto' }}
                    variant="smallPlus"
                    className={cn({
                        [`${CN}__good-status`]: item.status === ITEM_STATUS.GOOD,
                        [`${CN}__bad-status`]: item.status === ITEM_STATUS.BAD,
                        [`${CN}__escalated-status`]: item.status === ITEM_STATUS.ESCALATED,
                        [`${CN}__awaiting-status`]: item.status === ITEM_STATUS.AWAITING,
                    })}
                >
                    {item.status}
                </Text>
            )
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.ANALYST,
            name: 'Analyst',
            minWidth: 70,
            maxWidth: 70,
            data: 'string',
            className: `${CN}__fully-aligned-cell`,
            headerClassName: `${CN}__fully-aligned-cell-header`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => {
                if (!item.analyst) {
                    return null;
                }

                return (
                    <Facepile
                        personaSize={PersonaSize.size24}
                        personas={item.analystAsPersons}
                        getPersonaProps={() => ({ hidePersonaDetails: true })}
                    />
                );
            }
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.TAGS,
            name: '',
            minWidth: 30,
            maxWidth: 30,
            isResizable: false,
            className: `${CN}__fully-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <QueueItemTags
                    className={cn(`${CN}__queue-item-tags`, `${CN}__value-with-opacity`, { translucent: !item.hasTags })}
                    tags={item.tags}
                    itemId={item.id}
                    tagsString={item.tagsJoined}
                />
            )
        },
        {
            key: ITEM_LIST_COLUMN_KEYS.NOTES,
            name: '',
            minWidth: 30,
            maxWidth: 30,
            isResizable: false,
            className: `${CN}__fully-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (item: Item) => (
                <QueueItemNote
                    className={cn(`${CN}__queue-item-notes`, `${CN}__value-with-opacity`, { translucent: !item.hasNotes })}
                    notes={item.notes}
                    itemId={item.id}
                />
            )
        }
    ];

    @autobind
    onItemQueueClick(item: Item, queueId: string) {
        item.selectQueueId(queueId);
        this.setItemToItemStore(item);
    }

    @autobind
    onColumnClick(ev: React.MouseEvent<HTMLElement>, column: IColumn) {
        const { handleSortingUpdate } = this.props;

        if (handleSortingUpdate) {
            handleSortingUpdate({
                field: column.key as ITEM_SORTING_FIELD,
                order: !column.isSortedDescending ? SORTING_ORDER.DESC : SORTING_ORDER.ASC,
            });
        }
    }

    private get residualQueue() {
        const { queueStore } = this.props;
        return queueStore.allQueues.find(queue => queue.residual === true);
    }

    @autobind
    setItemToItemStore(selectedItem: Item) {
        const { queueStore, selectedQueue, searchId } = this.props;
        const { lockedById, lockedOnQueueViewId, status } = selectedItem;
        const { user } = this.user;

        if (searchId && !selectedItem.active) {
            this.history.push({ pathname: ROUTES.build.searchInactiveItemDetails(searchId, selectedItem.id) });
            return;
        }

        const isEscalatedItem = status === ITEM_STATUS.ESCALATED || status === ITEM_STATUS.ON_HOLD;
        const queues = isEscalatedItem ? queueStore.escalatedQueues : queueStore.queues;

        let queueViewId = selectedQueue
            ? selectedQueue.viewId
            : queues?.find(queue => queue.queueId === selectedItem.selectedQueueId)?.viewId;
        // If queueViewId is null, which means item belongs to Residual Queue
        if (!queueViewId) {
            queueViewId = this.residualQueue?.viewId;
        }

        const isLockedInTheCurrentQueue = lockedOnQueueViewId === queueViewId;
        const isLockedByCurrent = lockedById === user?.id;

        if (selectedQueue && queueViewId) {
            const pathname = isLockedByCurrent && isLockedInTheCurrentQueue
                ? ROUTES.build.itemDetailsReviewConsole(queueViewId, selectedItem.id)
                : ROUTES.build.itemDetails(queueViewId, selectedItem.id);

            this.history.push({ pathname });
        }

        if (searchId && !selectedQueue && queueViewId) {
            const pathname = isLockedByCurrent && isLockedInTheCurrentQueue
                ? ROUTES.build.searchItemDetailsReviewConsole(searchId, queueViewId, selectedItem.id)
                : ROUTES.build.searchItemDetails(searchId, queueViewId, selectedItem.id);

            this.history.push({ pathname });
        }
    }

    getColumnList() {
        const { selectedQueue, handleSortingUpdate, sortingObject } = this.props;

        const notRenderedColumnsKeys: string[] = [];

        if (selectedQueue) {
            notRenderedColumnsKeys.push(ITEM_LIST_COLUMN_KEYS.QUEUES);
        }
        if (selectedQueue && !selectedQueue.processingDeadline) {
            notRenderedColumnsKeys.push(ITEM_LIST_COLUMN_KEYS.TIME_LEFT);
        }

        if (handleSortingUpdate) {
            return this.columns
                .filter(column => !notRenderedColumnsKeys.includes(column.key))
                .map(column => {
                    const preparedColumn = { ...column };
                    if (AVAILABLE_SORTING_FIELDS[column.key]) {
                        preparedColumn.columnActionsMode = ColumnActionsMode.clickable;
                        preparedColumn.onColumnClick = this.onColumnClick;
                        preparedColumn.isSorted = false;
                    }

                    if (column.key === sortingObject?.field) {
                        preparedColumn.isSorted = true;
                        preparedColumn.isSortedDescending = sortingObject?.order === SORTING_ORDER.DESC;
                    }

                    // It's better to add maxWidth for OrderId column when Queues column is shown
                    if (column.key === ITEM_LIST_COLUMN_KEYS.ORDER_ID) {
                        preparedColumn.maxWidth = 250;
                    }

                    return preparedColumn;
                });
        }

        return this.columns
            .filter(column => !notRenderedColumnsKeys.includes(column.key));
    }

    composeQueueOptionsForItem(item: Item, queueIds: string[]): IContextualMenuItem[] {
        const result: IContextualMenuItem[] = [];
        const { queueStore } = this.props;

        queueIds.forEach(queueId => {
            const foundQueue = queueStore.getQueueById(queueId);
            const daysLeft = queueStore.getDaysLeft(item, foundQueue);

            if (foundQueue) {
                result.push({
                    key: queueId,
                    text: foundQueue.name,
                    data: daysLeft,
                    onRender: menuItem => (
                        <button
                            type="button"
                            className={`${CN}__contextual-menu-link`}
                            onClick={() => this.onItemQueueClick(item, queueId)}
                        >
                            <div className={`${CN}__contextual-menu-link__content`}>
                                <div className={`${CN}__contextual-menu-link__text`}>
                                    {menuItem.text}
                                </div>
                                {this.composeDaysLeftView(menuItem.data)}
                            </div>
                        </button>
                    ),
                });
            }
        });

        return [
            { key: 'header', text: 'Open in', itemType: ContextualMenuItemType.Header },
            ...result.sort((optionA, optionB) => {
                // If daysLeft is equal null it means that no processing deadline was specified.
                // For the sorting we can assume that daysLeft is equal Infinity.
                const dataA = optionA.data === null ? Infinity : optionA.data;
                const dataB = optionB.data === null ? Infinity : optionB.data;
                const timeLeftDifference = dataA - dataB;

                // If queues have the same daysLeft values, we sorting alphabetically by queue name.
                return timeLeftDifference || optionA.text!.localeCompare(optionB.text!);
            })];
    }

    composeDaysLeftView(daysLeft: number | null) {
        if (daysLeft === null) {
            return (<Text variant="smallPlus">N/A</Text>);
        }

        const dayOrDaysText = daysLeft === 1 ? 'day' : 'days';
        const svgIcon = daysLeft <= HIGH_RISK_DAYS_COUNT
            ? (<RiskClockIcon />)
            : (<NormalClockIcon />);

        return (
            <div className={`${CN}__time-left`}>
                {svgIcon}
                {
                    (daysLeft < 0) ? (
                        <span className={`${CN}__time-left-text--overdue-text`}>
                            <abbr title={`${Math.abs(daysLeft)}`}>Overdue</abbr>
                        </span>
                    ) : (
                        <Text variant="smallPlus" className={`${CN}__time-left-text`}>
                            {daysLeft}
                            {' '}
                            {dayOrDaysText}
                        </Text>
                    )
                }
            </div>
        );
    }

    renderTimeLeft(item: Item) {
        const { selectedQueue, queueStore } = this.props;
        const queue = selectedQueue || queueStore.getQueueById(item.selectedQueueId!);
        const daysLeft = queueStore.getDaysLeft(item, queue);

        return this.composeDaysLeftView(daysLeft);
    }

    renderQueues(item: Item) {
        const { queueStore } = this.props;
        const { queueIds, selectedQueueId } = item;
        // In case item doesn't belong to active queue or one of the deleted queues, then selected queue will be residual queue
        const selectedQueueName = selectedQueueId
            ? queueStore.getQueueById(selectedQueueId!)?.name || `Deleted queue (ID ${selectedQueueId!.substr(0, 8)}...)`
            : this.residualQueue?.name;
        const queuesOptions: IContextualMenuItem[] = this.composeQueueOptionsForItem(item, queueIds);

        // The first option is the header "Open in", and only the second one is the real queue
        return queuesOptions.length > 2
            ? (
                <div
                    key={item.selectedQueueId}
                    className={`${CN}__queue`}
                >
                    <CommandButton
                        title="Select a queue to open the order in"
                        text={`${selectedQueueName}, +${queueIds.length - 1} more`}
                        className={`${CN}__queues-dropdown`}
                        menuProps={{ items: queuesOptions }}
                        onMouseDown={event => {
                            event!.stopPropagation();
                            event!.preventDefault();
                        }}
                    />
                </div>
            )
            : (
                <div className={`${CN}__queue`}>
                    <Text variant="smallPlus" title={selectedQueueName}>{selectedQueueName}</Text>
                </div>
            );
    }

    renderTable(queueItems: Item[]): JSX.Element {
        const { className } = this.props;

        return (
            <div className={className}>
                <DetailsList
                    onActiveItemChanged={this.setItemToItemStore}
                    items={queueItems}
                    columns={this.getColumnList()}
                    layoutMode={DetailsListLayoutMode.justified}
                    selectionMode={SelectionMode.none}
                    isHeaderVisible
                    className={cn(CN, `${CN}--with-row-cursor-pointer`)}
                    cellStyleProps={{
                        cellExtraRightPadding: 5,
                        cellLeftPadding: 10,
                        cellRightPadding: 10
                    }}
                />
                { this.renderLoadMoreBtn() }
            </div>
        );
    }

    renderLoadMoreBtn() {
        const {
            storeForItemsLoading,
            handleLoadMoreRowsClick
        } = this.props;
        const { loadingMoreItems, canLoadMore } = storeForItemsLoading;

        if (!canLoadMore) {
            return null;
        }

        return (
            <button
                type="button"
                className={`${CN}__load_more_orders`}
                onClick={() => handleLoadMoreRowsClick()}
                disabled={loadingMoreItems}
            >
                {
                    loadingMoreItems
                        ? <Spinner />
                        : <Text variant="medium">Load more items</Text>
                }
            </button>
        );
    }

    renderNoItems(): JSX.Element {
        const { noItemsMessage } = this.props;
        return (
            <div className={`${CN}__center-aligned`}>
                <ErrorContent
                    illustrationSvg={NoOrdersIllustrationSvg}
                    message={noItemsMessage || 'No items in the list'}
                />
            </div>
        );
    }

    renderIsLoading(): JSX.Element {
        const { loadingMessage } = this.props;
        return (
            <div className={`${CN}__center-aligned`}>
                <Spinner label={loadingMessage || 'Loading...'} />
            </div>
        );
    }

    render() {
        const { storeForItemsLoading } = this.props;
        if (!storeForItemsLoading.items.length && !storeForItemsLoading.wasFirstPageLoaded) {
            return this.renderIsLoading();
        }

        return storeForItemsLoading.items.length ? this.renderTable(storeForItemsLoading.items) : this.renderNoItems();
    }
}
