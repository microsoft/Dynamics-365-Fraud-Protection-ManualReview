// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './queue-details.scss';

import { observer } from 'mobx-react';
import React, { Component } from 'react';

import { Text } from '@fluentui/react/lib/Text';

import { ItemsDetailsList } from '../../../components/items-details-list';
import { Queue } from '../../../models';
import { QueuesScreenStore } from '../../../view-services';
import { QueueStore } from '../../../view-services/queues';
import { QueueHeader } from './queue-header';

export interface QueueDetailsProps {
    className: string;
    queueStore: QueueStore;
    queueScreenStore: QueuesScreenStore;
    handleEditQueueClick: (queue: Queue) => void;
    handleAssignAnalystClick: (queue: Queue) => void;
    handleRefreshListClick: (queue: Queue) => void;
    handleLoadMoreRowsClick: (queue: Queue) => void;
    handleAutoRefreshToggleClick: (isEnabled: boolean) => void;
}

const CN = 'queue-details';

@observer
export class QueueDetails extends Component<QueueDetailsProps, never> {
    renderNoSelectedQueue(): JSX.Element {
        return (
            <div className={`${CN}__center-aligned`}>
                <Text>Please select a queue</Text>
            </div>
        );
    }

    render() {
        const {
            queueStore,
            queueScreenStore,
            className,
            handleEditQueueClick,
            handleAssignAnalystClick,
            handleRefreshListClick,
            handleAutoRefreshToggleClick,
            handleLoadMoreRowsClick,
        } = this.props;
        const {
            selectedQueue,
            selectedQueueId,
            refreshingQueueIds,
            sorting,
            updateSorting
        } = queueStore;
        const {
            isAutoRefreshEnabled,
            selectedQueueUpdated,
            canUserEditQueue,
            canUserAssignAnalyst
        } = queueScreenStore;

        let toRender: JSX.Element;

        if (selectedQueueId && !selectedQueue) {
            toRender = this.renderNoSelectedQueue();
        } else {
            toRender = (
                <ItemsDetailsList
                    queueStore={queueStore}
                    storeForItemsLoading={queueStore}
                    handleLoadMoreRowsClick={() => handleLoadMoreRowsClick(selectedQueue!)}
                    selectedQueue={selectedQueue}
                    loadingMessage="Loading orders..."
                    noItemsMessage="No orders in the queue. Please wait for a while."
                    sortingObject={sorting}
                    handleSortingUpdate={updateSorting}
                />
            );
        }

        const isQueueRefreshing = selectedQueue ? refreshingQueueIds.includes(selectedQueue?.viewId) : false;

        return (
            <div className={className}>
                <div className={`${CN}__common-wrapper`}>
                    <QueueHeader
                        queue={selectedQueue}
                        queueScreenStore={queueScreenStore}
                        handleEditQueueClick={handleEditQueueClick}
                        handleAssignAnalystClick={handleAssignAnalystClick}
                        handleRefreshClick={handleRefreshListClick}
                        handleAutoRefreshToggleClick={handleAutoRefreshToggleClick}
                        isQueueRefreshing={isQueueRefreshing}
                        isAutoRefreshEnabled={isAutoRefreshEnabled}
                        canEditQueue={canUserEditQueue}
                        canAssignAnalyst={canUserAssignAnalyst}
                        queueLastUpdated={selectedQueueUpdated}
                        sorted={sorting}
                    />
                    { toRender }
                </div>
            </div>
        );
    }
}
