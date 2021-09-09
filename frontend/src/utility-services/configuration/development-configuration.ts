// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

import { SEVERITY } from '../logger';
import { ConfigAuthentication, ConfigMaps, Configuration } from './configuration';

export class DevelopmentConfiguration implements Configuration {
    readonly apiBaseUrl = '/api';

    readonly appId = '';

    readonly logLevel: SEVERITY;

    readonly authentication: ConfigAuthentication;

    readonly maps: ConfigMaps;

    constructor(
        logLevel?: SEVERITY,
        baseAuthUrl?: string,
        clientId?: string,
        tenant?: string,
        TOKEN_PERSIST_KEY?: string,
        NONCE_PERSIST_KEY?: string,
        mapClientId?: string
    ) {
        this.logLevel = logLevel || SEVERITY.DEBUG;
        this.authentication = {
            baseAuthUrl: baseAuthUrl || 'https://login.microsoftonline.com',
            clientId: clientId || '27193f56-cb90-4e67-8cf2-13eabbbabca5',
            tenant: tenant || 'ce2526f8-5105-481b-8e16-b2eb098b32bb',
            TOKEN_PERSIST_KEY: TOKEN_PERSIST_KEY || 'MR_AUTH_TOKEN',
            NONCE_PERSIST_KEY: NONCE_PERSIST_KEY || 'MR_AUTH_TOKEN_NONCE',
        };
        this.maps = {
            clientId: '13c1aae5-c721-459a-ac8b-0e1a973b99a6' || mapClientId || '34b48a29-3933-49df-9b58-0be37fea18a7'
        };
    }
}
