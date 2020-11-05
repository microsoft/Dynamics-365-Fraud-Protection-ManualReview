// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.model.dfp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

import java.util.List;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategy.UpperCamelCaseStrategy.class)
public class PreviousPurchase extends Purchase {
    private Integer riskScore;
    private String reasonCodes;
    private String policyApplied;
    private String lastMerchantStatus;
    private DeviceContext deviceContext;
    private List<Address> addressList;
    private List<PaymentInstrument> paymentInstrumentList;
    private List<BankEvent> bankEventsList;
}
