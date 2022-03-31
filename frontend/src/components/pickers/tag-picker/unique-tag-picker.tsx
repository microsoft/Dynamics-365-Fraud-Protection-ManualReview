// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import { observer } from 'mobx-react';
import autobind from 'autobind-decorator';
import cn from 'classnames';
import { ITag, TagPicker } from '@fluentui/react/lib/Pickers';
import { Label } from '@fluentui/react/lib/Label';

import './unique-tag-picker.scss';

const CN = 'unique-tag-picker';

interface UniqueTagPickerProps {
    onSelectionChange(tagItems: ITag[]): void;
    suggestions: ITag[];
    selectedItemIds?: string[];
    suggestionsHeaderText: string;
    noResultsFoundText: string;
    label?: string;
    className?: string;
    placeholder?: string;
    itemLimit?: number;
}

@observer
export class UniqueTagPicker extends Component<UniqueTagPickerProps, never> {
    static defaultProps = {
        label: null,
        placeholder: null,
        itemLimit: 1,
        className: ''
    };

    @autobind
    handleSelectionChange(tagItems?: ITag[]) {
        const { onSelectionChange } = this.props;

        if (tagItems) {
            onSelectionChange(tagItems);
        }
    }

    @autobind
    resolveSuggestions(filterText: string, selectedItems?: ITag[]): ITag[] {
        const { suggestions } = this.props;

        return (suggestions || [])
            .filter(suggestion => {
                const isItemAlreadySelected = selectedItems?.find(item => item.key === suggestion.key);
                const isItemNameContainsFilterText = filterText
                    ? suggestion.name.toLowerCase().indexOf(filterText.toLowerCase()) !== -1
                    : true;

                return !isItemAlreadySelected && isItemNameContainsFilterText;
            });
    }

    renderSuggestionItem(item: ITag) {
        return (
            <div className={`${CN}__suggestion-item`}>
                {item.name}
            </div>
        );
    }

    render() {
        const {
            selectedItemIds,
            suggestions,
            suggestionsHeaderText,
            noResultsFoundText,
            className,
            label,
            placeholder,
            itemLimit,
        } = this.props;

        const selectedItems = (selectedItemIds || []).map(id => ({
            key: id,
            name: suggestions.find(item => item.key === id)?.name || id,
        }));

        return (
            <div className={cn(CN, className)}>
                <Label className={`${CN}__label`}>{label || ''}</Label>
                <TagPicker
                    className={`${CN}__picker`}
                    pickerSuggestionsProps={{
                        suggestionsHeaderText,
                        noResultsFoundText,
                    }}
                    inputProps={{ placeholder }}
                    getTextFromItem={item => item.name}
                    selectedItems={selectedItems}
                    itemLimit={itemLimit}
                    resolveDelay={250}
                    onResolveSuggestions={this.resolveSuggestions}
                    onEmptyResolveSuggestions={selectedTagItems => this.resolveSuggestions('', selectedTagItems)}
                    onChange={this.handleSelectionChange}
                    onRenderSuggestionsItem={this.renderSuggestionItem}
                />
            </div>
        );
    }
}
