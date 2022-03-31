// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autobind from 'autobind-decorator';
import cn from 'classnames';
import { observer } from 'mobx-react';
import { resolve } from 'inversify-react';
import { IconButton } from '@fluentui/react/lib/Button';
import { Callout } from '@fluentui/react/lib/Callout';
import { Text } from '@fluentui/react/lib/Text';
import { Persona, PersonaSize } from '@fluentui/react/lib/Persona';

import { Note } from '../../../models';
import { QueuesScreenStore } from '../../../view-services';
import { encodeStringForCSS, formatForNotes } from '../../../utils';
import { TYPES } from '../../../types';

import './queue-item-notes.scss';

interface QueueItemNoteProps {
    className?: string;
    notes: Note[];
    itemId: string;
}

const CN = 'queue-item-notes';

@observer
export class QueueItemNote extends Component<QueueItemNoteProps, never> {
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
        this.queuesScreenStore.setDisplayedNotesItemId(itemId);
        event.stopPropagation();
        event.preventDefault();
    }

    @autobind
    handleDismiss() {
        this.queuesScreenStore.setDisplayedNotesItemId(null);
    }

    render() {
        const { className, notes, itemId } = this.props;
        const { displayedNotesItemId } = this.queuesScreenStore;
        const iconClass = `${CN}-${encodeStringForCSS(itemId)}`;
        const isCalloutVisible = displayedNotesItemId === itemId && !!notes?.length;

        return (
            <>
                <IconButton
                    className={cn(className, iconClass)}
                    iconProps={{ iconName: 'Message' }}
                    onMouseDown={this.handleIconClick}
                />
                <Callout
                    className={CN}
                    gapSpace={0}
                    // I would love to use refs here, but Fluent UI doesn't allow to add element refs on FortIcon or IconButton
                    target={`.${iconClass}`}
                    onDismiss={this.handleDismiss}
                    hidden={!isCalloutVisible}
                >
                    <>
                        <IconButton
                            className={`${CN}__note-heading-dismiss`}
                            iconProps={{
                                iconName: 'Cancel'
                            }}
                            title="Dismiss notes"
                            ariaLabel="Dismiss notes"
                            onClick={this.handleDismiss}
                        />
                        {
                            notes.map((note: Note, i) => {
                                const { note: content, created, user } = note;
                                const formattedDate = formatForNotes(created, '');
                                const name = user ? user.name : 'Fraud Analyst';
                                return (
                                    // TODO: don't use index as key (so far we don't have unique params in dummy data)
                                    // eslint-disable-next-line react/no-array-index-key
                                    <div className={`${CN}__note`} key={`${itemId}-note-${i}`}>
                                        <div className={`${CN}__note-heading`}>
                                            <Persona
                                                imageUrl={user?.imageUrl}
                                                hidePersonaDetails
                                                size={PersonaSize.size24}
                                                className={`${CN}__note-photo`}
                                                text={user?.name}
                                            />
                                            <Text>
                                                <strong>{`${name} `}</strong>
                                                commented
                                            </Text>
                                            <Text variant="small">{formattedDate}</Text>
                                        </div>
                                        <div className={`${CN}__note-content`}>
                                            <Text>
                                                <pre>
                                                    { content }
                                                </pre>
                                            </Text>
                                        </div>
                                    </div>
                                );
                            })
                        }
                    </>
                </Callout>
            </>
        );
    }
}
