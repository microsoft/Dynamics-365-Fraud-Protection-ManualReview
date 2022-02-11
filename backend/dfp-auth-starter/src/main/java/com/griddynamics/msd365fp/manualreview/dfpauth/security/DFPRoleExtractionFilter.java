// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.dfpauth.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.griddynamics.msd365fp.manualreview.azuregraph.client.AnalystClient;
import com.griddynamics.msd365fp.manualreview.dfpauth.config.properties.DFPRoleExtractorProperties;
import com.griddynamics.msd365fp.manualreview.dfpauth.util.UserPrincipalUtility;
import com.griddynamics.msd365fp.manualreview.model.Analyst;
import com.griddynamics.msd365fp.manualreview.model.exception.IncorrectConfigurationException;
import com.azure.spring.autoconfigure.aad.UserPrincipal;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_PRINCIPAL_ID_CLAIM;

@Slf4j
public class DFPRoleExtractionFilter extends OncePerRequestFilter {

    private static final String VIRTUAL_USER = "Virtual-User";
    private static final String PERF_TEST_PROFILE = "perftest";

    private final AnalystClient analystClient;
    private final Cache<String, Set<String>> roleCache;
    private final List<String> activeProfiles;

    public DFPRoleExtractionFilter(final AnalystClient analystClient,
                                   final DFPRoleExtractorProperties properties,
                                   final List<String> activeProfiles) throws IncorrectConfigurationException {
        if (properties.getTokenCacheSize() == null ||
                properties.getTokenCacheSize() < 1 ||
                properties.getTokenCacheRetention() == null) {
            throw new IncorrectConfigurationException("Token cache settings for DFPAuth should be defined properly");
        }
        this.analystClient = analystClient;
        this.roleCache = CacheBuilder.newBuilder()
                .expireAfterWrite(properties.getTokenCacheRetention())
                .maximumSize(properties.getTokenCacheSize())
                .build();
        this.activeProfiles = activeProfiles;
    }

    @SneakyThrows
    @Override
    protected void doFilterInternal(@NonNull final HttpServletRequest request,
                                    @NonNull final HttpServletResponse response,
                                    @NonNull final FilterChain filterChain) {
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        final String userId = UserPrincipalUtility.extractUserId(auth);
        final String virtualUserName = request.getHeader(VIRTUAL_USER);

        if (authHeader != null && auth != null && userId != null) {
            log.debug("The request will be authorized by DFP roles");
            Set<String> roles = roleCache.get(authHeader, () -> {
                Analyst analyst = analystClient.getAnalystById(userId);
                Set<String> analystRoles = Objects.requireNonNullElse(analyst.getRoles(), Set.of())
                        .stream()
                        .map(Object::toString)
                        .collect(Collectors.toSet());
                log.info("User [{}] imported from DFP RBAC policies with [{}] roles.", userId, analystRoles);
                return analystRoles;
            });
            List<GrantedAuthority> updatedAuthorities = new LinkedList<>();
            roles.forEach(role -> updatedAuthorities.add(new SimpleGrantedAuthority(role)));
            Object newPrincipal = null;
            if (virtualUserName != null && activeProfiles.contains(PERF_TEST_PROFILE)) {
                newPrincipal = addVirtualUserClaim(auth, virtualUserName, userId);
            }
            Authentication newAuth = new PreAuthenticatedAuthenticationToken(
                    newPrincipal == null ? auth.getPrincipal() : newPrincipal, auth.getCredentials(), updatedAuthorities);
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        } else {
            log.debug("No conditions have been met for DFP roles retrieving");
        }
        filterChain.doFilter(request, response);
    }

    private Object addVirtualUserClaim(Authentication auth, String virtualUserName, String userId) {
        try {
            JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder();
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            for (Map.Entry<String, Object> entry : principal.getClaims().entrySet()) {
                claimsSetBuilder.claim(entry.getKey(), entry.getValue());
            }
            claimsSetBuilder.claim(AUTH_TOKEN_PRINCIPAL_ID_CLAIM, virtualUserName);
            UserPrincipal newPrincipal = new UserPrincipal(null, null, claimsSetBuilder.build());
            log.debug("Virtual user with ID [{}] will be used as authentication principal", virtualUserName);
            return newPrincipal;
        } catch (Exception e) {
            log.warn("User [{}] tried to authenticate with virtual user [{}].", userId, virtualUserName, e);
        }
        return null;
    }
}
