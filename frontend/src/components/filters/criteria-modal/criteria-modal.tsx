// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import './criteria-modal.scss';

import autobind from 'autobind-decorator';
import { observer } from 'mobx-react';
import React, { Component } from 'react';

import {
    CommandBarButton,
    DefaultButton,
    IconButton,
    PrimaryButton
} from '@fluentui/react/lib/Button';
import { IContextualMenuItem } from '@fluentui/react/lib/ContextualMenu';
import { Modal } from '@fluentui/react/lib/Modal';

import { Condition } from '../../../models/filter/condition';
import { FilterField } from '../../../models/filter/filter-field';
import {
    FILTER_MUTATION_STATE,
    FiltersStore
} from '../../../view-services/essence-mutation-services/filters-store';
import { CriteriaItem } from '../criteria-item';

interface CriteriaModalComponentProps {
    filter: FilterField;
    isModalOpen: boolean;
    onModalClose(): void;
    onCreateUpdateFilter(): void;
    filterStore?: FiltersStore
}

const CN = 'criteria-modal';

const ADD_BUTTON_TEXT = {
    CREATE: 'Add',
    UPDATE: 'Update'
};

const CRITERIA_MODAL_STATE = {
    ADD_BUTTON_TEXT: {
        [FILTER_MUTATION_STATE.UPDATE]: ADD_BUTTON_TEXT.UPDATE,
        [FILTER_MUTATION_STATE.CREATE]: ADD_BUTTON_TEXT.CREATE
    }
};

@observer
export class CriteriaModal extends Component<CriteriaModalComponentProps, never> {
    static defaultProps = {
        filterStore: null
    };

    getAddButtonText() {
        const { filter } = this.props;

        const filterState = filter.isFilterUsed
            ? FILTER_MUTATION_STATE.UPDATE
            : FILTER_MUTATION_STATE.CREATE;

        return CRITERIA_MODAL_STATE.ADD_BUTTON_TEXT[filterState];
    }

    @autobind
    handleConditionDropdownChange(item: IContextualMenuItem | undefined) {
        const { filter } = this.props;
        if (item) {
            const searchedCondition = filter.getFilterCondition(item.key);

            if (searchedCondition) {
                searchedCondition.setIsConditionUsed(true);

                const sortIndex = filter.highestConditionOrderSortIndex;
                searchedCondition.setSortIndex(sortIndex || 0);
            }
        }
    }

    @autobind
    handleCriteriaItemConditionDropdownChange(prevCondId: string, currCondId: string) {
        const { filter } = this.props;

        const previousSelectedCondition = filter.getFilterCondition(prevCondId);
        const seekedCondition = filter.getFilterCondition(currCondId);

        if (previousSelectedCondition) {
            previousSelectedCondition.setIsConditionUsed(false);
        }

        if (seekedCondition && previousSelectedCondition) {
            seekedCondition.setIsConditionUsed(true);
            seekedCondition.setSortIndex(previousSelectedCondition.orderSortIndex);
        }
    }

    @autobind
    handleDeleteCondition(conditionId: string) {
        const { filter } = this.props;
        const searchedCondition = filter.conditions.find(condition => condition.id === conditionId);

        if (searchedCondition) {
            searchedCondition.setIsConditionUsed(false);
            searchedCondition.setIsValid(false);
            searchedCondition.setIsDisabled(false);
            searchedCondition.setSortIndex(0);
        }
    }

    isAddUpdateButtonDisabled() {
        const { filter } = this.props;

        return !filter.isFilterUsedConditionsAreValid;
    }

    @autobind
    usedConditions(condition: Condition) {
        return condition.isConditionUsed;
    }

    renderConditionItems() {
        const { filter } = this.props;
        const {
            sortedUsedConditionsBySortIndex,
            conditionsAsDropdownOptions,
        } = filter;

        return sortedUsedConditionsBySortIndex
            .map(condition => (
                <CriteriaItem
                    key={condition.id}
                    filterId={filter.id}
                    condition={condition}
                    onConditionDropdownChange={this.handleCriteriaItemConditionDropdownChange}
                    criteriaDropdownOptions={conditionsAsDropdownOptions}
                    onDeleteCondition={this.handleDeleteCondition}
                    isDeleteButtonDisabled={false}
                />
            ));
    }

    renderConditionCommandMenuButton() {
        const { filter } = this.props;

        return (
            <CommandBarButton
                disabled={filter.isFilterDisabled}
                className={`${CN}__context-menu`}
                text="Add another condition"
                iconProps={{ iconName: 'Add' }}
                menuProps={{
                    items: filter.conditionsAsContextualMenuItems,
                    onItemClick: (_, item) => this.handleConditionDropdownChange(item)
                }}
            />
        );
    }

    render() {
        const {
            isModalOpen, onModalClose, onCreateUpdateFilter, filter
        } = this.props;

        return (
            <Modal
                titleAriaId="Add criteria modal"
                isOpen={isModalOpen}
                onDismiss={onModalClose}
                containerClassName={CN}
            >
                <div className={`${CN}__header`}>
                    <div className={`${CN}__title`}>
                        {filter.displayName}
                    </div>
                    <IconButton
                        ariaLabel="Close add criteria modal"
                        className={`${CN}__close-icon`}
                        iconProps={{
                            iconName: 'Cancel'
                        }}
                        onClick={onModalClose}
                    />
                </div>
                <div className={`${CN}__description`}>{filter.description}</div>
                <div className={`${CN}__conditions`}>
                    {this.renderConditionItems()}
                </div>
                <div className={`${CN}__action-buttons`}>
                    <div>
                        {this.renderConditionCommandMenuButton()}
                    </div>
                    <div className={`${CN}__action-buttons-wrap`}>
                        <PrimaryButton
                            disabled={this.isAddUpdateButtonDisabled()}
                            onClick={onCreateUpdateFilter}
                            text={this.getAddButtonText()}
                            className={`${CN}__action-buttons-add-criteria`}
                        />
                        <DefaultButton
                            onClick={onModalClose}
                            text="Cancel"
                            className={`${CN}__action-buttons-add-cancel`}
                        />
                    </div>
                </div>
            </Modal>
        );
    }
}
