// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import autobind from 'autobind-decorator';
import { resolve } from 'inversify-react';
import { observer } from 'mobx-react';
import { Stack } from '@fluentui/react/lib/Stack';
import { IconButton } from '@fluentui/react/lib/Button';
import { Text } from '@fluentui/react/lib/Text';
import { Persona, PersonaSize } from '@fluentui/react/lib/Persona';
import { TYPES } from '../../../types';
import { CurrentUserStore } from '../../../view-services';
import {
    headerStackStyles,
    regularButtonIconStyles,
    regularButtonStyles,
    headingStyles,
    personaIconStyles, userNameTextStyles
} from './header.styles';
import { UnfinishedOrders } from '../../unfinished-orders/unfinished-orders';

export interface HeaderProps {
    title?: JSX.Element | string;
    showLockedOrders: boolean;
}

@observer
export class Header extends Component<HeaderProps, never> {
    static defaultProps = {
        title: null,
    };

    @resolve(TYPES.CURRENT_USER_STORE)
    private userStore!: CurrentUserStore;

    @autobind
    showUserPanel() {
        this.userStore.toggleUserPanel();
    }

    render() {
        const { title, showLockedOrders } = this.props;
        const { user, isAuthenticated } = this.userStore;

        return (
            <Stack
                as="header"
                horizontal
                horizontalAlign="space-between"
                styles={headerStackStyles}
            >
                <Stack.Item verticalFill>
                    <Stack
                        horizontal
                        verticalFill
                        verticalAlign="center"
                    >
                        <IconButton
                            iconProps={{
                                iconName: 'waffle',
                                styles: regularButtonIconStyles
                            }}
                            styles={regularButtonStyles}
                        />
                        <Text
                            as="h1"
                            variant="mediumPlus"
                            styles={headingStyles}
                        >
                            {title || 'Dynamics 365 Fraud Protection Manual Review'}
                        </Text>
                    </Stack>
                </Stack.Item>
                <Stack.Item verticalFill grow>
                    <Stack
                        horizontal
                        verticalFill
                        grow
                        horizontalAlign="center"
                        verticalAlign="center"
                    >
                        { showLockedOrders ? <UnfinishedOrders /> : null }
                    </Stack>
                </Stack.Item>
                <Stack.Item verticalFill>
                    <Stack
                        horizontal
                        verticalFill
                        verticalAlign="center"
                    >
                        {(isAuthenticated && user) && (
                            <>
                                <Text
                                    onClick={this.showUserPanel}
                                    styles={userNameTextStyles}
                                >
                                    {user?.name}
                                </Text>
                                <Persona
                                    imageUrl={user?.imageUrl}
                                    onClick={this.showUserPanel}
                                    text={user?.name}
                                    hidePersonaDetails
                                    size={PersonaSize.size28}
                                    styles={personaIconStyles}
                                />
                            </>
                        )}
                    </Stack>
                </Stack.Item>
            </Stack>
        );
    }
}
