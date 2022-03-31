// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { IconButton } from '@fluentui/react/lib/Button';
import {
    ShimmeredDetailsList,
} from '@fluentui/react/lib/ShimmeredDetailsList';
import {
    ColumnActionsMode,
    DetailsListLayoutMode,
    SelectionMode
} from '@fluentui/react/lib/DetailsList';
import { Text } from '@fluentui/react/lib/Text';
import { Toggle } from '@fluentui/react/lib/Toggle';
import autobind from 'autobind-decorator';
import classNames from 'classnames';
import { History } from 'history';
import { resolve } from 'inversify-react';
import { observer } from 'mobx-react';
import React, { Component } from 'react';
import { ROUTES } from '../../../constants';
import { Alert } from '../../../models';
import './alerts-list.scss';
import { TYPES } from '../../../types';

export const CN = 'alerts-list';

interface AlertsListProps {
    className?: string;
    alerts: Alert[];
    loadingAlerts: boolean;
    alertsInMutation: string[];
    onToggleActive: (alertId: string, checked: boolean) => void;
    onDelete: (alertId: string) => void;
}

@observer
export class AlertsList extends Component<AlertsListProps, never> {
    static defaultProps = {
        className: ''
    };

    @resolve(TYPES.HISTORY)
    private history!: History;

    private columns = [
        {
            key: 'name',
            name: 'Alert name',
            minWidth: 50,
            maxWidth: 1500,
            isPadded: true,
            className: `${CN}__vertically-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (alert: Alert) => (
                <span
                    className={`${CN}__name-cell`}
                    role="link"
                    data-alert-id={alert.id}
                    tabIndex={0}
                    onClick={this.getEditAlertHref}
                    onKeyDown={this.getEditAlertHref}
                >
                    {alert.name}
                </span>
            )
        },
        {
            key: 'created',
            name: 'Created',
            minWidth: 50,
            maxWidth: 120,
            className: `${CN}__vertically-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: () => (
                <Text variant="medium" className={`${CN}__date-cell`} />
            )
        },
        {
            key: 'active',
            name: 'Active',
            minWidth: 100,
            maxWidth: 120,
            className: `${CN}__vertically-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (alert: Alert) => (
                <Toggle
                    onText="Active"
                    offText="Inactive"
                    data-alert-id={alert.id}
                    className={`${CN}__active-cell`}
                    checked={alert.active}
                    disabled={this.isAlertMutating(alert)}
                    onChange={this.onToggleActiveClick}
                />
            )
        },
        {
            key: 'delete',
            name: 'Delete',
            minWidth: 50,
            maxWidth: 120,
            className: `${CN}__vertically-aligned-cell`,
            columnActionsMode: ColumnActionsMode.disabled,
            onRender: (alert: Alert) => (
                <IconButton
                    className={`${CN}__delete-cell`}
                    data-alert-id={alert.id}
                    disabled={this.isAlertMutating(alert)}
                    iconProps={{
                        iconName: 'Delete'
                    }}
                    onClick={this.onDeleteClick}
                />
            )
        },
    ];

    @autobind
    onDeleteClick(event: React.MouseEvent<HTMLElement> & React.KeyboardEvent<HTMLElement>) {
        const alertId = event.currentTarget.getAttribute('data-alert-id') as string;
        const { onDelete } = this.props;
        onDelete(alertId);
    }

    @autobind
    onToggleActiveClick(event: React.MouseEvent<HTMLElement>, checked: boolean | undefined) {
        const alertId = event.currentTarget.getAttribute('data-alert-id') as string;
        const { onToggleActive } = this.props;
        onToggleActive(alertId, checked || false);
    }

    @autobind
    getEditAlertHref(event: React.MouseEvent<HTMLElement> & React.KeyboardEvent<HTMLElement>) {
        const alertId = event.currentTarget.getAttribute('data-alert-id') as string;
        this.history.push({
            pathname: ROUTES.build.editAlert(alertId)
        });
    }

    @autobind
    isAlertMutating(alert: Alert) {
        const { alertsInMutation } = this.props;
        return alertsInMutation.includes(alert.id);
    }

    @autobind
    renderList() {
        const { alerts, loadingAlerts } = this.props;

        return (
            <ShimmeredDetailsList
                // onActiveItemChanged={this.setItemToItemStore}
                items={alerts}
                columns={this.columns}
                layoutMode={DetailsListLayoutMode.justified}
                selectionMode={SelectionMode.none}
                isHeaderVisible={false}
                enableShimmer={!alerts.length && loadingAlerts}
                className={`${CN}__data-list`}
                cellStyleProps={{
                    cellExtraRightPadding: 5,
                    cellLeftPadding: 10,
                    cellRightPadding: 10
                }}
            />
        );
    }

    render() {
        const { className } = this.props;

        return (
            <div className={classNames(CN, className)}>
                <div className={`${CN}__header`}>
                    <Text variant="large" className={`${CN}__header-title`}>Alert settings</Text>
                    <Text>You can manage and define alert rules by clicking at list below.</Text>
                </div>

                {this.renderList()}
            </div>
        );
    }
}
