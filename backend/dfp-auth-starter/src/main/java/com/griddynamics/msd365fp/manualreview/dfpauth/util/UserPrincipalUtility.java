// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.dfpauth.util;

import com.griddynamics.msd365fp.manualreview.model.Analyst;
import com.azure.spring.autoconfigure.aad.UserPrincipal;
import lombok.EqualsAndHashCode;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_PRINCIPAL_ID_CLAIM;
import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_SUB_CLAIM;

@UtilityClass
public class UserPrincipalUtility {

    public Authentication getAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public String getUserId() {
        return extractUserId(getAuth());
    }

    public List<String> getUserRoles() {
        return extractUserRoles(getAuth());
    }

    public String extractUserId(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            return extractUserId((UserPrincipal) auth.getPrincipal());
        } else if (auth instanceof EmulatedAuth) {
            return auth.getName();
        }
        return null;
    }

    public String extractUserId(UserPrincipal principal) {
        if (principal != null) {
            Object subClaim = principal.getClaim(AUTH_TOKEN_SUB_CLAIM);
            Object idClaim = principal.getClaim(AUTH_TOKEN_PRINCIPAL_ID_CLAIM);
            if (idClaim != null && subClaim != null && !StringUtils.equals(subClaim.toString(), idClaim.toString())) {
                return idClaim.toString();
            }
        }
        return null;
    }

    public List<String> extractUserRoles(Authentication auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList());
    }

    public void setEmulatedAuth(Analyst analyst) {
        if (analyst != null && analyst.getRoles() != null && analyst.getId() != null) {
            List<GrantedAuthority> authorities = analyst.getRoles().stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
            SecurityContextHolder.setContext(new SecurityContextImpl(new EmulatedAuth(authorities, analyst.getId())));
        } else {
            SecurityContextHolder.setContext(new SecurityContextImpl());
        }
    }

    public void clearEmulatedAuth() {
        SecurityContextHolder.setContext(new SecurityContextImpl());
    }

    @EqualsAndHashCode(callSuper = true)
    public static class EmulatedAuth extends AbstractAuthenticationToken {
        String userId;

        public EmulatedAuth(final Collection<? extends GrantedAuthority> authorities, final String userId) {
            super(authorities);
            this.userId = userId;
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return userId;
        }

        @Override
        public boolean isAuthenticated() {
            return true;
        }
    }


}
