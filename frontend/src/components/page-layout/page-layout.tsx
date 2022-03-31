// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './page-layout.scss';

import autobind from 'autobind-decorator';
import cn from 'classnames';
import { resolve } from 'inversify-react';
import { observer } from 'mobx-react';
import React, { Component } from 'react';

import { ActionButton } from '@fluentui/react/lib/Button';
import { Modal } from '@fluentui/react/lib/Modal';
import { Panel } from '@fluentui/react/lib/Panel';
import { Persona, PersonaSize } from '@fluentui/react/lib/Persona';
import { IRawStyle } from '@fluentui/react/lib/Styling';
import { Text } from '@fluentui/react/lib/Text';

import { QUEUE_MUTATION_TYPES } from '../../constants';
import { CreateEditQueueModal } from '../../screens/queues/create-edit-queue';
import { TYPES } from '../../types';
import { AppStore, CurrentUserStore, QueuesScreenStore } from '../../view-services';
import { Header } from './header';
import { LeftNavigation } from './left-navigation';
import {
    emailTextStyles,
    myAccountPanelStyles,
    signOutButtonStyles,
    userNameTextStyles
} from './page-layout.styles';
import { ToastNotification } from './toast-notification';

export interface PageLayoutProps {
    styles?: IRawStyle;
    children: React.ReactNode;
    title?: JSX.Element | string;
    withHeader?: boolean;
    showLockedOrders?: boolean;
}

const CN = 'page-layout';

@observer
export class PageLayout extends Component<PageLayoutProps, never> {
    static defaultProps = {
        styles: null,
        title: null,
        withHeader: false,
        showLockedOrders: false
    };

    @resolve(TYPES.CURRENT_USER_STORE)
    private userStore!: CurrentUserStore;

    @resolve(TYPES.APP_STORE)
    private appStore!: AppStore;

    @resolve(TYPES.QUEUES_SCREEN_STORE)
    private queueScreenStore!: QueuesScreenStore;

    @autobind
    onToggleNavigation(isExpanded: boolean) {
        this.appStore.toggleNavigationExpanded(isExpanded);
    }

    @autobind
    handleSignOut() {
        this.userStore.toggleUserPanel(false);
        this.userStore.signOut();
    }

    @autobind
    dismissUserPanel() {
        this.userStore.toggleUserPanel(false);
    }

    @autobind
    closeModifyQueueModal() {
        this.appStore.toggleOpenedModalType(null);
    }

    @autobind
    renderMyAccountPanel() {
        const { showUserPanel, user } = this.userStore;

        return (
            <Panel
                isLightDismiss
                headerText="My account"
                isOpen={showUserPanel}
                onDismiss={this.dismissUserPanel}
                closeButtonAriaLabel="Close"
                styles={myAccountPanelStyles}
            >
                {user && (
                    <div className={`${CN}__user-card`}>
                        <div className={`${CN}__user-card-image`}>
                            <Persona
                                imageUrl={user.imageUrl}
                                text={user.name}
                                hidePersonaDetails
                                size={PersonaSize.size72}
                            />
                        </div>
                        <div className={`${CN}__user-card-info`}>
                            <Text
                                variant="mediumPlus"
                                styles={userNameTextStyles}
                            >
                                {user.name}
                            </Text>
                            <Text styles={emailTextStyles}>{user.email}</Text>
                            <ActionButton
                                styles={signOutButtonStyles}
                                onClick={this.handleSignOut}
                            >
                                Sign Out
                            </ActionButton>
                        </div>
                    </div>
                )}
            </Panel>
        );
    }

    @autobind
    private renderCreateQueueModal() {
        const {
            isModalOpened,
            openedQueueMutationModalType,
            openedQueueMutationModalInitialTab
        } = this.appStore;
        const { queueStore } = this.queueScreenStore;
        const { selectedQueue } = queueStore;

        return (
            <Modal
                isOpen={isModalOpened}
                onDismiss={this.closeModifyQueueModal}
                isBlocking={false}
                containerClassName={`${CN}__create-queue-modal-container`}
                scrollableContentClassName={`${CN}__create-queue-modal-content`}
            >
                {openedQueueMutationModalType && (
                    <CreateEditQueueModal
                        closeCreateEditQueueModal={this.closeModifyQueueModal}
                        mutationType={openedQueueMutationModalType}
                        queue={openedQueueMutationModalType === QUEUE_MUTATION_TYPES.UPDATE ? selectedQueue : null}
                        initialTab={openedQueueMutationModalInitialTab}
                    />
                )}
            </Modal>
        );
    }

    render() {
        const {
            children,
            title,
            withHeader = true,
            showLockedOrders = false
        } = this.props;
        const { isNavigationExpanded } = this.appStore;

        return (
            <div className={CN}>
                { withHeader ? <Header title={title} showLockedOrders={showLockedOrders} /> : null }
                <div className={cn(`${CN}__bottom-part`, { [`${CN}__bottom-part--expanded`]: isNavigationExpanded })}>
                    <LeftNavigation
                        isExpanded={isNavigationExpanded}
                        onToggleExpanded={this.onToggleNavigation}
                        userStore={this.userStore}
                    />
                    <div className={`${CN}__content-area`}>{children}</div>
                </div>
                { this.renderMyAccountPanel() }
                { this.renderCreateQueueModal() }
                <ToastNotification />
            </div>
        );
    }
}
