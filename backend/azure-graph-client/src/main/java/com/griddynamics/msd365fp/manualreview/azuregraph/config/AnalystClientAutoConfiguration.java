// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.azuregraph.config;

import com.griddynamics.msd365fp.manualreview.azuregraph.client.AnalystClient;
import com.griddynamics.msd365fp.manualreview.azuregraph.config.properties.AnalystClientProperties;
import com.griddynamics.msd365fp.manualreview.model.exception.IncorrectConfigurationException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnClass(AnalystClient.class)
@EnableConfigurationProperties(AnalystClientProperties.class)
public class AnalystClientAutoConfiguration {
    public static final String CLIENT_REGISTRATION_AZURE_GRAPH_API = "azure";

    @Bean
    @ConditionalOnBean(OAuth2AuthorizedClientManager.class)
    WebClient azureGraphAPIWebClient(OAuth2AuthorizedClientManager authorizedClientManager,
                                     ClientRegistrationRepository clientRegistrationRepository) throws IncorrectConfigurationException {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(CLIENT_REGISTRATION_AZURE_GRAPH_API);
        if (registration == null) {
            throw new IncorrectConfigurationException(
                    "The " + CLIENT_REGISTRATION_AZURE_GRAPH_API + " client registration should be defined");
        }
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId(CLIENT_REGISTRATION_AZURE_GRAPH_API);
        return WebClient.builder()
                .apply(oauth2Client.oauth2Configuration())
                .build();


//        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2 = new ServletOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
//        return WebClient.builder()
//                .apply(oauth2.oauth2Configuration())
//                .build();
    }

    @Bean
    public ModelMapper azureClientModelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setSkipNullEnabled(true)
                .setCollectionsMergeEnabled(false);
        return modelMapper;
    }

    @Bean
    public AnalystClient analystClient(@Qualifier("azureGraphAPIWebClient") WebClient azureGraphAPIWebClient,
                                       @Qualifier("azureClientModelMapper") ModelMapper azureClientModelMapper,
                                       AnalystClientProperties properties) {
        return new AnalystClient(azureGraphAPIWebClient, azureClientModelMapper, properties);
    }
}

