// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.config;

import com.griddynamics.msd365fp.manualreview.dfpauth.security.DFPRoleExtractionFilter;
import com.azure.spring.autoconfigure.aad.AADAppRoleStatelessAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.ROLES_ALLOWED_FOR_ACCESS;

@Slf4j
@EnableGlobalMethodSecurity(securedEnabled = true,
        prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final AADAppRoleStatelessAuthenticationFilter appRoleAuthFilter;
    private final DFPRoleExtractionFilter dfpRoleExtractionFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Azure Front Door health check
        http.authorizeRequests().antMatchers(HttpMethod.HEAD, "/").permitAll();
        http.authorizeRequests().antMatchers(HttpMethod.GET, "/").permitAll();
        // Swagger
        http.authorizeRequests().antMatchers("/swagger-ui/**").permitAll();
        http.authorizeRequests().antMatchers("/oauth2-redirect.html").permitAll();
        http.authorizeRequests().antMatchers("/v3/api-docs").permitAll();
        // Application
        http.authorizeRequests().antMatchers("/api/**").hasAnyAuthority(ROLES_ALLOWED_FOR_ACCESS);
        http.authorizeRequests().anyRequest().denyAll();

        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
        //TODO: rework for production
        http.csrf().disable(); //csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse());

        http.addFilterBefore(appRoleAuthFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(dfpRoleExtractionFilter, AADAppRoleStatelessAuthenticationFilter.class);
        //TODO: rework for common ErrorDTO
        http.exceptionHandling()
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    log.debug("Not enough permissions to access this resource. {}", accessDeniedException.getMessage());
                    response.sendError(HttpServletResponse.SC_FORBIDDEN);
                })
                .authenticationEntryPoint((request, response, authException) -> {
                    log.debug("Auth token is invalid or expired. {}", authException.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                });
    }

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults(""); // Remove the ROLE_ prefix
    }
}
