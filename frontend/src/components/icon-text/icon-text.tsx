// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import React, { Component } from 'react';
import cn from 'classnames';
import { FontIcon } from '@fluentui/react/lib/Icon';
import { Text, ITextProps } from '@fluentui/react/lib/Text';
import { TooltipHost } from '@fluentui/react/lib/Tooltip';

import './icon-text.scss';

const CN = 'icon-text';

enum ICON_STYLES {
    'GOOD' = 'GOOD',
    'BAD' = 'BAD',
    'UNKNOWN' = 'UNKNOWN'
}
interface IconInfo {
    iconName: string,
    value: any;
    tooltipText?: string | JSX.Element,
}
interface Icons {
    [ICON_STYLES.GOOD]?: IconInfo;
    [ICON_STYLES.BAD]?: IconInfo;
    [ICON_STYLES.UNKNOWN]?: IconInfo;
}

interface IconTextProps {
    text?: string | JSX.Element | null,
    textVariant: ITextProps['variant'],
    placeholder: string | JSX.Element,
    iconValue: any,
    icons: Icons,
    className?: string;
    title?: string;
}

export class IconText extends Component<IconTextProps, never> {
    static defaultProps = {
        text: null,
        className: '',
        title: ''
    };

    renderIconWithTooltip(value: any, icons: Icons) {
        const tooltipId = `${Math.random()}`;

        if (icons.GOOD && icons.GOOD.tooltipText && icons.GOOD.value === value) {
            return (
                <TooltipHost
                    content={icons.GOOD.tooltipText}
                    id={tooltipId}
                    calloutProps={{ gapSpace: 0 }}
                >
                    <FontIcon
                        iconName={icons.GOOD.iconName}
                        className={cn(`${CN}__icon`, `${CN}__icon-good`)}
                        aria-describedby={tooltipId}
                    />
                </TooltipHost>
            );
        }

        if (icons.GOOD && icons.GOOD.value === value) {
            return (
                <FontIcon
                    iconName={icons.GOOD.iconName}
                    className={cn(`${CN}__icon`, `${CN}__icon-good`)}
                />
            );
        }

        if (icons.BAD && icons.BAD.tooltipText && icons.BAD.value === value) {
            return (
                <TooltipHost
                    content={icons.BAD.tooltipText}
                    id={tooltipId}
                    calloutProps={{ gapSpace: 0 }}
                >
                    <FontIcon
                        iconName={icons.BAD.iconName}
                        className={cn(`${CN}__icon`, `${CN}__icon-bad`)}
                        aria-describedby={tooltipId}
                    />
                </TooltipHost>
            );
        }
        if (icons.BAD && icons.BAD.value === value) {
            return (
                <FontIcon
                    iconName={icons.BAD.iconName}
                    className={cn(`${CN}__icon`, `${CN}__icon-bad`)}
                />
            );
        }

        if (icons.UNKNOWN && icons.UNKNOWN.tooltipText && icons.UNKNOWN.value === value) {
            return (
                <TooltipHost
                    content={icons.UNKNOWN.tooltipText}
                    id={tooltipId}
                    calloutProps={{ gapSpace: 0 }}
                >
                    <FontIcon
                        iconName={icons.UNKNOWN.iconName}
                        className={cn(`${CN}__icon`, `${CN}__icon-unknown`)}
                        aria-describedby={tooltipId}
                    />
                </TooltipHost>
            );
        }
        if (icons.UNKNOWN && icons.UNKNOWN.value === value) {
            return (
                <FontIcon
                    iconName={icons.UNKNOWN.iconName}
                    className={cn(`${CN}__icon`, `${CN}__icon-unknown`)}
                />
            );
        }

        return null;
    }

    render() {
        const {
            text,
            textVariant,
            placeholder,
            iconValue,
            icons,
            className,
            title
        } = this.props;

        if (text === undefined || text === null) {
            return placeholder;
        }

        return (
            <div className={cn(CN, className)}>
                {this.renderIconWithTooltip(iconValue, icons)}
                <Text variant={textVariant} title={title}>{text}</Text>
            </div>
        );
    }
}
