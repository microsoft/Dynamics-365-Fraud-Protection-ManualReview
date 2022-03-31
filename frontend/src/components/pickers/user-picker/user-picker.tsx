// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import { observer } from 'mobx-react';
import autobind from 'autobind-decorator';
import cn from 'classnames';

import { NormalPeoplePicker } from '@fluentui/react/lib/Pickers';
import { IPersonaProps, Persona, PersonaSize } from '@fluentui/react/lib/Persona';
import { Label } from '@fluentui/react/lib/Label';

import './user-picker.scss';

const CN = 'user-picker';

interface UserPickerProps {
    onSelectionChange(users: IPersonaProps[]): void;
    suggestions: IPersonaProps[];
    selectedUserIds?: string[];
    label?: string;
    className?: string;
    placeholder?: string;
    itemLimit?: number;
}

@observer
export class UserPicker extends Component<UserPickerProps, never> {
    static defaultProps = {
        label: null,
        placeholder: null,
        itemLimit: 1,
        className: ''
    };

    @autobind
    handleSelectionChange(users?: IPersonaProps[]) {
        const { onSelectionChange } = this.props;

        if (users) {
            onSelectionChange(users);
        }
    }

    @autobind
    resolveSuggestions(filterText: string, selectedItems?: IPersonaProps[]): IPersonaProps[] {
        const { suggestions } = this.props;

        return (suggestions || [])
            .filter(suggestion => {
                const isUserAlreadySelected = selectedItems?.find(user => user.id === suggestion.id);
                const isUserNameContainsFilterText = filterText
                    ? suggestion.text?.toLowerCase().indexOf(filterText.toLowerCase()) !== -1
                    : true;

                return !isUserAlreadySelected && isUserNameContainsFilterText;
            });
    }

    renderSuggestionItem(user: IPersonaProps) {
        const props: IPersonaProps = {
            ...user,
            size: PersonaSize.size28,
            className: `${CN}__suggestion-item`
        };
        return (
            /* eslint-disable-next-line react/jsx-props-no-spreading */
            <Persona {...props} />
        );
    }

    render() {
        const {
            selectedUserIds,
            suggestions,
            className,
            label,
            placeholder,
            itemLimit,
        } = this.props;

        const selectedItems: IPersonaProps[] = [];
        (selectedUserIds || []).forEach(id => {
            const userFromSuggestions = suggestions.find(user => user.id === id);
            if (userFromSuggestions) {
                selectedItems.push(userFromSuggestions);
            }
        });

        return (
            <div className={cn(CN, className)}>
                <Label className={`${CN}__label`}>{label || ''}</Label>
                <NormalPeoplePicker
                    className={`${CN}__picker`}
                    pickerSuggestionsProps={{
                        suggestionsHeaderText: 'Suggested analysts',
                        noResultsFoundText: 'No analysts found',
                        className: `${CN}__suggestion`
                    }}
                    inputProps={{ placeholder }}
                    selectedItems={selectedItems}
                    itemLimit={itemLimit}
                    onResolveSuggestions={this.resolveSuggestions}
                    onEmptyResolveSuggestions={selectedUsers => this.resolveSuggestions('', selectedUsers)}
                    onChange={this.handleSelectionChange}
                    onRenderSuggestionsItem={this.renderSuggestionItem}
                />
            </div>
        );
    }
}
