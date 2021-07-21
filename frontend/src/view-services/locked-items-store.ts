// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { injectable, inject } from 'inversify';
import { action, observable, computed } from 'mobx';
import { TYPES } from '../types';
import { ItemService } from '../data-services';
import { Logger } from '../utility-services';
import { Item, Queue } from '../models';
import { QueueStore } from './queues';

export interface ItemLock {
    item: Item;
    queue: Queue | null;
}

@injectable()
export class LockedItemsStore {
    @observable
    lockedItems: Item[] | undefined | null;

    @observable
    loading: boolean = false;

    @computed
    get itemLocks(): ItemLock[] | null {
        const { allQueues } = this.queueStore;
        if (!this.lockedItems) {
            return null;
        }

        return this.lockedItems
            .reduce((acc: ItemLock[], item: Item): ItemLock[] => {
                const seekedQueue = allQueues?.find(queue => queue.viewId === item.lockedOnQueueViewId);
                return seekedQueue
                    ? [...acc, { item, queue: seekedQueue }]
                    : acc;
            }, []);
    }

    constructor(
        @inject(TYPES.LOGGER) protected readonly logger: Logger,
        @inject(TYPES.ITEM_SERVICE) private itemService: ItemService,
        @inject(TYPES.QUEUE_STORE) private queueStore: QueueStore
    ) {}

    @action
    async getLockedItems() {
        this.loading = true;
        const transformedResponse = await this.itemService.getLockedItems();
        const { allQueues, loadingQueues } = this.queueStore;
        if (!allQueues && !loadingQueues) {
            this.queueStore.loadQueues();
        }
        this.lockedItems = transformedResponse;
        this.loading = false;
    }

    @action
    async unassignItem(itemId: string, queueId: string | null) {
        if (queueId) {
            await this.itemService.finishReview(itemId, queueId);
        } else {
            await this.itemService.finishReview(itemId);
        }

        // refresh queue if it is the one selected on the screen
        if (queueId && this.queueStore.selectedQueueId === queueId) {
            this.queueStore.refreshQueue(queueId);
            this.queueStore.loadQueueItems(queueId);
        }
    }
}
