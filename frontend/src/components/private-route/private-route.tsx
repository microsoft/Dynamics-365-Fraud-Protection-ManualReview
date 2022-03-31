// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { resolve } from 'inversify-react';
import React from 'react';
import {
    Redirect,
    Route,
    RouteComponentProps,
    RouteProps
} from 'react-router-dom';
import { ERROR_SCREEN_STATES, PERMISSION, ROUTES } from '../../constants';
import { TYPES } from '../../types';
import { AuthenticationService } from '../../utility-services';
import { CurrentUserStore } from '../../view-services';

export interface PrivateRouteProps extends RouteProps{
    accessPermission: PERMISSION
}

/* eslint-disable react/jsx-props-no-spreading */
export class PrivateRoute extends React.Component<PrivateRouteProps, never> {
    @resolve(TYPES.AUTHENTICATION)
    private authService!: AuthenticationService;

    @resolve(TYPES.CURRENT_USER_STORE)
    private userStore!: CurrentUserStore;

    renderRoute(Component: React.ComponentType<RouteComponentProps<any>> | React.ComponentType<any>) {
        const { accessPermission } = this.props;

        return (props: RouteComponentProps) => {
            if (!this.authService.isAuthenticated()) {
                return (
                    <Redirect
                        to={{
                            pathname: ROUTES.LOGIN,
                            state: { from: props.location }
                        }}
                    />
                );
            }

            if (accessPermission && !this.userStore.checkUserCan(accessPermission)) {
                return (
                    <Redirect
                        to={{
                            pathname: ROUTES.build.error(ERROR_SCREEN_STATES.FORBIDDEN),
                            state: { from: props.location }
                        }}
                    />
                );
            }

            return (<Component {...props} />);
        };
    }

    render() {
        const { component: Component, ...rest } = this.props;

        return (
            <Route
                {...rest}
                render={this.renderRoute(Component!)}
            />
        );
    }
}
/* eslint-enable react/jsx-props-no-spreading */
