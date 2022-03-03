// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.griddynamics.msd365fp.manualreview.analytics.model.AppSettingsType;
import com.griddynamics.msd365fp.manualreview.analytics.model.MetricType;
import com.griddynamics.msd365fp.manualreview.analytics.model.ThresholdOperator;
import com.griddynamics.msd365fp.manualreview.analytics.model.dto.AlertCreationDTO;
import com.griddynamics.msd365fp.manualreview.analytics.model.dto.ItemLabelingMetricDTO;
import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.Alert;
import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.ConfigurableAppSetting;
import com.griddynamics.msd365fp.manualreview.analytics.repository.AlertRepository;
import com.griddynamics.msd365fp.manualreview.analytics.repository.ConfigurableAppSettingRepository;
import com.griddynamics.msd365fp.manualreview.analytics.service.dashboard.PublicItemLabelingMetricService;
import com.griddynamics.msd365fp.manualreview.azuregraph.client.AnalystClient;
import com.griddynamics.msd365fp.manualreview.model.Analyst;
import com.griddynamics.msd365fp.manualreview.model.exception.NotFoundException;
import com.azure.spring.autoconfigure.aad.UserPrincipal;
import org.assertj.core.util.Maps;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_PRINCIPAL_ID_CLAIM;
import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_SUB_CLAIM;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    private final ObjectMapper jsonMapper = new Jackson2ObjectMapperBuilder().build()
            .setTimeZone(TimeZone.getTimeZone("UTC"))
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private AlertService alertService;

    @Mock
    private PublicItemLabelingMetricService metricService;
    @Mock
    private AnalystClient analystClient;
    @Mock
    private AlertRepository alertRepository;
    @Mock
    private ConfigurableAppSettingRepository appSettingRepository;
    @Mock
    private MailService mailService;

    @BeforeEach
    public void init() {
        alertService = new AlertService(
                metricService,
                analystClient,
                alertRepository,
                appSettingRepository,
                new ModelMapper(),
                mailService,
                jsonMapper);
    }

    @Test
    void createAlertSuccessful() throws NotFoundException {
        // Given
        String userId = "userId";
        AlertCreationDTO dto = createAlertDTO();
        when(alertRepository.save(any())).thenReturn(createAlert(userId));
        mockSecurityContext();

        // When
        alertService.create(dto);

        // Then
        verify(alertRepository).save(any(Alert.class));
    }

    @Test
    void sendAlertFailedAlreadySentTest() {
        // Given
        Alert alert = new Alert();
        alert.getLastNotification().setSent(OffsetDateTime.now());
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));

        // When
        alertService.sendAlerts();

        // Then
        verify(alertRepository).findByActiveTrue();
        verifyNoInteractions(appSettingRepository);
        verifyNoInteractions(metricService);
        verifyNoInteractions(mailService);
    }

    @Test
    void sendAlertFailedAlertNotActiveTest() {
        // Given
        Alert alert = new Alert();
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));

        // When
        alertService.sendAlerts();

        // Then
        verify(alertRepository).findByActiveTrue();
        verifyNoInteractions(appSettingRepository);
        verifyNoInteractions(metricService);
        verifyNoInteractions(mailService);
    }

    @Test
    void sendAlertFailedMetadataAbsentTest() {
        // Given
        Alert alert = createAlert("userId");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(appSettingRepository.findByType(eq(AppSettingsType.MAIL_TEMPLATES)))
                .thenReturn(Collections.emptyList());

        // When
        alertService.sendAlerts();

        // Then
        verify(alertRepository).findByActiveTrue();
        verifyNoInteractions(metricService);
        verifyNoInteractions(mailService);
    }

    @Test
    void sendAlertFailNoMailTest() throws NotFoundException {
        // Given
        String userId = "userId";
        Alert alert = createAlert(userId);
        ConfigurableAppSetting appSettings = new ConfigurableAppSetting();
        Map<String, String> map = Maps.newHashMap("subject", "test");
        map.put("template", "test");
        appSettings.setValue(map);
        ItemLabelingMetricDTO totalMetric = new ItemLabelingMetricDTO();
        Analyst analyst = new Analyst();
        analyst.setDisplayName("User Name");
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(appSettingRepository.findByType(eq(AppSettingsType.MAIL_TEMPLATES)))
                .thenReturn(List.of(appSettings));
        when(metricService.getItemLabelingTotalMetrics(any(), any(), any(), any()))
                .thenReturn(totalMetric);
        when(analystClient.getAnalystById(eq(userId))).thenReturn(analyst);

        // When
        alertService.sendAlerts();

        // Then
        verify(alertRepository).findByActiveTrue();
        verify(alertRepository).save(any(Alert.class));
        verify(appSettingRepository).findByType(AppSettingsType.MAIL_TEMPLATES);
        verify(metricService).getItemLabelingTotalMetrics(any(), any(), any(), any());
        verifyNoInteractions(mailService);
    }

    @Test
    @Disabled
    void sendAlertFailAlertConditionNotMetTest() {
        // Given
        String userId = "userId";
        Alert alert = createAlert(userId);
        ConfigurableAppSetting appSettings = new ConfigurableAppSetting();
        Map<String, String> map = Maps.newHashMap("subject", "test");
        map.put("template", "test");
        appSettings.setValue(map);
        ItemLabelingMetricDTO totalMetric = new ItemLabelingMetricDTO();
        totalMetric.setGood(100);
        totalMetric.setWatched(0);
        totalMetric.setReviewed(100);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(appSettingRepository.findByType(eq(AppSettingsType.MAIL_TEMPLATES)))
                .thenReturn(List.of(appSettings));
        when(metricService.getItemLabelingTotalMetrics(any(), any(), any(), any()))
                .thenReturn(totalMetric);

        // When
        alertService.sendAlerts();

        // Then
        verify(alertRepository).findByActiveTrue();
        verify(alertRepository).save(any(Alert.class));
        verify(appSettingRepository).findByType(AppSettingsType.MAIL_TEMPLATES);
        verify(metricService).getItemLabelingTotalMetrics(any(), any(), any(), any());
        verifyNoInteractions(mailService);
    }

    @Test
    @Disabled
    void sendAlertSuccessfulTest() throws NotFoundException {
        // Given
        String userId = "userId";
        String email = "mail@mail.com";
        Alert alert = createAlert(userId);
        ConfigurableAppSetting appSettings = new ConfigurableAppSetting();
        Map<String, String> map = Maps.newHashMap("subject", "test");
        map.put("template", "test");
        appSettings.setValue(map);
        ItemLabelingMetricDTO totalMetric = new ItemLabelingMetricDTO();
        Analyst analyst = new Analyst();
        analyst.setDisplayName("User Name");
        analyst.setMail(email);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alert));
        when(appSettingRepository.findByType(eq(AppSettingsType.MAIL_TEMPLATES)))
                .thenReturn(List.of(appSettings));
        when(metricService.getItemLabelingTotalMetrics(any(), any(), any(), any()))
                .thenReturn(totalMetric);
        when(analystClient.getAnalystById(eq(userId))).thenReturn(analyst);

        // When
        boolean result = alertService.sendAlerts();

        // Then
        assertTrue(result);
        verify(alertRepository).findByActiveTrue();
        verify(alertRepository).save(any(Alert.class));
        verify(appSettingRepository).findByType(AppSettingsType.MAIL_TEMPLATES);
        verify(metricService).getItemLabelingTotalMetrics(any(), any(), any(), any());
        verify(mailService).send(any(), any(), any());
    }

    @Test
    void sendAlertsFalseTest() throws NotFoundException {
        // Given
        String userId = "userId";
        String email = "mail@mail.com";
        Alert alertTrue = createAlert(userId);
        Alert alertFalse = createAlert(userId);
        alertFalse.getLastNotification().setSent(OffsetDateTime.now());
        ConfigurableAppSetting appSettings = new ConfigurableAppSetting();
        Map<String, String> map = Maps.newHashMap("subject", "test");
        map.put("template", "test");
        appSettings.setValue(map);
        ItemLabelingMetricDTO totalMetric = new ItemLabelingMetricDTO();
        Analyst analyst = new Analyst();
        analyst.setDisplayName("User Name");
        analyst.setMail(email);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(alertTrue, alertFalse));
        when(appSettingRepository.findByType(eq(AppSettingsType.MAIL_TEMPLATES)))
                .thenReturn(List.of(appSettings));
        when(metricService.getItemLabelingTotalMetrics(any(), any(), any(), any()))
                .thenReturn(totalMetric);
        when(analystClient.getAnalystById(eq(userId))).thenReturn(analyst);

        // When
        boolean result = alertService.sendAlerts();

        // Then
        assertFalse(result);
    }

    private void mockSecurityContext() {
        UserPrincipal principal = mock(UserPrincipal.class);
        Authentication authentication = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
        when(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).thenReturn(principal);
        when(principal.getClaim(eq(AUTH_TOKEN_PRINCIPAL_ID_CLAIM))).thenReturn("userId");
        when(principal.getClaim(eq(AUTH_TOKEN_SUB_CLAIM))).thenReturn("userAssignmentId");
    }

    private AlertCreationDTO createAlertDTO() {
        AlertCreationDTO alertDTO = new AlertCreationDTO();
        alertDTO.setName("Test DTO");
        alertDTO.setMetricType(MetricType.GOOD_DECISION_RATE);
        alertDTO.setThresholdOperator(ThresholdOperator.LESS_THAN);
        alertDTO.setThresholdValue(80.0);
        return alertDTO;
    }

    private Alert createAlert(String userId) {
        Alert alert = new Alert();
        alert.setId("1");
        alert.setOwnerId(userId);
        alert.setName("Test Alert");
        alert.setPeriod(Duration.ofDays(10));
        alert.setMetricType(MetricType.GOOD_DECISION_RATE);
        alert.setThresholdOperator(ThresholdOperator.LESS_THAN);
        alert.setThresholdValue(80.0);
        alert.setActive(true);
        return alert;
    }
}
