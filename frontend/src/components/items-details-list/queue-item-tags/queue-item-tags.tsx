// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autobind from 'autobind-decorator';
import cn from 'classnames';
import { observer } from 'mobx-react';
import { resolve } from 'inversify-react';
import { IconButton } from '@fluentui/react/lib/Button';
import { Callout } from '@fluentui/react/lib/Callout';
import { QueuesScreenStore } from '../../../view-services';
import { encodeStringForCSS } from '../../../utils';
import { TYPES } from '../../../types';

import './queue-item-tags.scss';

interface QueueItemTagProps {
    className?: string;
    tags: string[];
    tagsString: string;
    itemId: string;
}

const CN = 'queue-item-tags';

@observer
export class QueueItemTags extends Component<QueueItemTagProps, never> {
    static defaultProps = {
        className: ''
    };

    @resolve(TYPES.QUEUES_SCREEN_STORE)
    private queuesScreenStore!: QueuesScreenStore;

    componentWillUnmount() {
        this.queuesScreenStore.setDisplayedNotesItemId(null);
    }

    @autobind
    handleIconClick(event: React.MouseEvent<any>) {
        const { itemId } = this.props;
        this.queuesScreenStore.setDisplayedTagsItemId(itemId);
        event.stopPropagation();
        event.preventDefault();
    }

    @autobind
    handleDismiss() {
        this.queuesScreenStore.setDisplayedTagsItemId(null);
    }

    render() {
        const {
            className,
            tags,
            tagsString,
            itemId
        } = this.props;
        const { displayTagsItemId } = this.queuesScreenStore;
        const iconClass = `${CN}-${encodeStringForCSS(itemId)}`;
        const isCalloutVisible = displayTagsItemId === itemId && !!tags?.length;

        return (
            <>
                <IconButton
                    className={cn(className, iconClass)}
                    iconProps={{ iconName: 'Tag' }}
                    onMouseDown={this.handleIconClick}
                    title={tagsString}
                />
                <Callout
                    className={CN}
                    gapSpace={0}
                    target={`.${iconClass}`}
                    onDismiss={this.handleDismiss}
                    hidden={!isCalloutVisible}
                >
                    <>
                        {
                            tags.map((tag: string) => (
                                <span className={`${CN}__tag`}>
                                    {tag}
                                </span>
                            ))
                        }
                        <IconButton
                            className={`${CN}__tags-heading-dismiss`}
                            iconProps={{
                                iconName: 'Cancel'
                            }}
                            title="Dismiss notes"
                            ariaLabel="Dismiss notes"
                            onClick={this.handleDismiss}
                        />
                    </>
                </Callout>
            </>
        );
    }
}
