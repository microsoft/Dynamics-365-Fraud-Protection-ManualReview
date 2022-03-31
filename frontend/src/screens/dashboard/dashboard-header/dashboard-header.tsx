// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './dashboard-header.scss';

import autoBind from 'autobind-decorator';
import cx from 'classnames';
import { History } from 'history';
import { resolve } from 'inversify-react';
import { observer } from 'mobx-react';
import React, { Component } from 'react';

import { ActionButton } from '@fluentui/react/lib/Button';
import { DatePicker } from '@fluentui/react/lib/DatePicker';
import { Dropdown, IDropdownOption } from '@fluentui/react/lib/Dropdown';
import { IPersonaProps } from '@fluentui/react/lib/Persona';

import {
    DATE_RANGE,
    DATE_RANGE_DAYS,
    DATE_RANGE_DISPLAY,
    ROLE
} from '../../../constants';
import { Queue, User } from '../../../models';
import { TYPES } from '../../../types';
import { getEndOfDate, getPastDate } from '../../../utils/date';
import { DashboardScreenStore } from '../../../view-services';
import { DashboardSearch } from './dashboard-search';

const CN = 'dashboard-header';

interface DateRange extends IDropdownOption {
    key: DATE_RANGE;
}

interface DashboardHeaderProps {
    /**
     * displayBackButton - shows or hide go back button depending
     * on the current visited page
     */
    displayBackButton?: boolean;
    dashboardScreenStore: DashboardScreenStore;
    /**
     * isSearchBarDisplayed - show/hide dashboard header search filed based on the user role
     */
    isSearchBarDisplayed?: boolean;

    onQueueSearchChange?(queue: Queue): void;

    onAnalystSearchChange?(analyst: IPersonaProps): void;

    /**
     * onGoBackClick - callback, on go back button click
     */
    onGoBackClick?(): void;

    /**
     * renderSearchBar - render or not the search bar
     */
    renderSearchBar?: boolean

    user: User | null;
}

@observer
export class DashboardHeader extends Component<DashboardHeaderProps, never> {
    static defaultProps = {
        isSearchBarDisplayed: false,
        onQueueSearchChange: undefined,
        onAnalystSearchChange: undefined,
        onGoBackClick: undefined,
        renderSearchBar: false
    };

    private maxDate: Date = new Date();

    @resolve(TYPES.HISTORY)
    private history!: History;

    private dropdownOptions: DateRange[] = [
        { key: DATE_RANGE.SIX_WEEKS, text: DATE_RANGE_DISPLAY[DATE_RANGE.SIX_WEEKS] },
        { key: DATE_RANGE.FOUR_WEEKS, text: DATE_RANGE_DISPLAY[DATE_RANGE.FOUR_WEEKS] },
        { key: DATE_RANGE.TWO_WEEKS, text: DATE_RANGE_DISPLAY[DATE_RANGE.TWO_WEEKS] },
        { key: DATE_RANGE.CUSTOM, text: DATE_RANGE_DISPLAY[DATE_RANGE.CUSTOM] },
    ];

    @autoBind
    getDatepickerValue(start?: boolean) {
        const { dashboardScreenStore } = this.props;
        const { dateRange } = dashboardScreenStore;
        const nowDate = dateRange !== DATE_RANGE.CUSTOM
            ? getEndOfDate(new Date())
            : undefined;

        if (start) {
            return getPastDate(DATE_RANGE_DAYS[dateRange]);
        }

        return nowDate;
    }

    @autoBind
    handleDropdownChange(option: IDropdownOption | undefined) {
        const { dashboardScreenStore } = this.props;

        if (option) {
            dashboardScreenStore.setDateRange(option.key as DATE_RANGE);

            const pastDate = getPastDate(DATE_RANGE_DAYS[option.key as DATE_RANGE]);
            const nowDate = getEndOfDate(new Date());

            dashboardScreenStore.clearDates();

            if (pastDate) {
                dashboardScreenStore.setFromDate(pastDate);
                dashboardScreenStore.setToDate(nowDate);
            }
        }
    }

    @autoBind
    handleStartDateSelection(date: Date | null | undefined) {
        const { dashboardScreenStore } = this.props;

        if (date) {
            dashboardScreenStore.setFromDate(date);
        }
    }

    @autoBind
    handleEndDateSelection(date: Date | null | undefined) {
        const { dashboardScreenStore } = this.props;

        if (date) {
            const fullHoursDate = getEndOfDate(date);

            dashboardScreenStore.setToDate(fullHoursDate);
        }
    }

    /**
     * Disable date picker only when custom range selection is issued
     */
    isDatePickerDisabled() {
        const { dashboardScreenStore } = this.props;
        const { dateRange } = dashboardScreenStore;

        return dateRange !== DATE_RANGE.CUSTOM;
    }

    @autoBind
    renderGoBackButton() {
        const {
            onGoBackClick, isSearchBarDisplayed, displayBackButton, user
        } = this.props;

        const isAdmin = user?.roles.includes(ROLE.ADMIN_MANAGER);

        let buttonVisibility;

        if (isAdmin) {
            buttonVisibility = true;
        } else {
            buttonVisibility = isSearchBarDisplayed && displayBackButton;
        }

        return (
            <ActionButton
                className={`${CN}__go-back-btn`}
                iconProps={{ iconName: 'ChevronLeftSmall', className: `${CN}__back-to-queues-btn-icon` }}
                onClick={onGoBackClick}
                /* eslint-disable-next-line no-nested-ternary */
                style={{ visibility: buttonVisibility ? 'initial' : 'hidden' }}
            >
                Go Back
            </ActionButton>
        );
    }

    render() {
        const {
            dashboardScreenStore, onQueueSearchChange, onAnalystSearchChange, displayBackButton, isSearchBarDisplayed, renderSearchBar
        } = this.props;
        const { getFromDate, getToDate, dateRange } = dashboardScreenStore;
        const fromDate = getFromDate || this.getDatepickerValue(true);
        const toDate = getToDate || this.getDatepickerValue();

        return (

            <div className={cx(CN, { [`${CN}--aligned`]: renderSearchBar })}>
                {
                    renderSearchBar && (
                        <div className={cx(`${CN}__search`, { [`${CN}__search--hide`]: isSearchBarDisplayed })}>
                            {displayBackButton && this.renderGoBackButton()}
                            <DashboardSearch
                                onAnalystSearchChange={onAnalystSearchChange!}
                                onQueueSearchChange={onQueueSearchChange!}
                                dashboardScreenStore={dashboardScreenStore}
                            />
                        </div>
                    )
                }
                <div className={`${CN}__date-selection`}>
                    <Dropdown
                        onChange={(ev, option) => this.handleDropdownChange(option)}
                        className={`${CN}__dropdown`}
                        selectedKey={dateRange}
                        options={this.dropdownOptions}
                    />
                    <DatePicker
                        value={fromDate || this.getDatepickerValue(true)}
                        disabled={this.isDatePickerDisabled()}
                        className={`${CN}__date-picker`}
                        maxDate={toDate || this.maxDate}
                        placeholder="Select start date..."
                        ariaLabel="Select start date"
                        onSelectDate={this.handleStartDateSelection}
                    />
                    <DatePicker
                        value={toDate || this.getDatepickerValue()}
                        disabled={this.isDatePickerDisabled()}
                        className={`${CN}__date-picker`}
                        minDate={fromDate}
                        maxDate={this.maxDate}
                        placeholder="Select end date..."
                        ariaLabel="Select end date"
                        onSelectDate={this.handleEndDateSelection}
                    />
                </div>
            </div>
        );
    }
}
