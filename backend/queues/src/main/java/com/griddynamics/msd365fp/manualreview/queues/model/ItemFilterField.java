// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.Serializable;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.queues.model.ItemDataFieldCondition.*;

@SuppressWarnings({"unused", "java:S1192"})
@AllArgsConstructor
public enum ItemFilterField implements Serializable {
    IMPORT_DATE("Other signals", "Import date",
            ItemDataField.IMPORT_DATE,
            Set.of(CONTAINS, BETWEEN_DATE, NOT_BETWEEN_DATE, LESS_DATE, GREATER_DATE, LESS_OR_EQUAL_DATE, GREATER_OR_EQUAL_DATE),
            null, null,
            "The moment of adding item to MR Tool."),
    TOTAL_AMOUNT("Transaction", "Total amount",
            ItemDataField.TOTAL_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Total amount charged to the customer. Provided by the merchant."),
    USER_COUNTRY("Account", "User country",
            ItemDataField.USER_COUNTRY,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The user's country or region."),
    PRODUCT_SKU("Transaction", "Product SKU",
            ItemDataField.PRODUCT_SKU,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Product SKU."),
    SCORE("Other signals", "Risk score",
            ItemDataField.SCORE,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "-1", "1000",
            "Risk score calculated by Fraud Protection risk models in the range from -1 to 1000. -1 indicates an error determining a risk score."
    ),
    AGGREGATED_EMAIL_CONFIRMED("Account", "Email confirmed",
            ItemDataField.AGGREGATED_EMAIL_CONFIRMED,
            Set.of(IS_TRUE),
            null, null,
            "An indicator of whether the user-provided email address has been verified as owned by the user. Value is aggregated from user data and 'email_confirmed' field in custom purchase data."
    ),
    EMAIL_DOMAIN_DISPOSABLE("Other signals", "Disposable email domain",
            ItemDataField.EMAIL_DOMAIN_DISPOSABLE,
            Set.of(IS_TRUE),
            null, null,
            "An indicator of whether the aggregated email domain is treated as disposable in connected verification systems. Collected near to time of purchase receiving."
    ),
    AGGREGATED_EMAIL_DOMAIN("Account", "Email domain",
            ItemDataField.AGGREGATED_EMAIL_DOMAIN,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "A user's email domain. Value is aggregated from user data and 'email_domain' field in custom purchase data."
    ),
    ACCOUNT_AGE("Account", "Account age",
            ItemDataField.ACCOUNT_AGE,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Account age in days on moment of purchase making."
    ),
    CUSTOM_ACCOUNT_AGE_BUCKET("Account", "Account age bucket",
            ItemDataField.CUSTOM_ACCOUNT_AGE_BUCKET,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "It is retrieved from 'account_age_in_days_bucket ' field in custom purchase data."
    ),
    AGE_OF_FIRST_KNOWN_TRANSACTION("Account", "Age of first known transaction",
            ItemDataField.AGE_OF_FIRST_KNOWN_TRANSACTION,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Age in days of first transaction registered in DFP. Calculated near to time of purchase receiving."
    ),
    AUTHENTICATION_PROVIDER("Account", "Authentication provider",
            ItemDataField.AUTHENTICATION_PROVIDER,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The user's authentication provider, if different from the merchant's."
    ),
    PRODUCT_CATEGORY("Transaction", "Product category",
            ItemDataField.PRODUCT_CATEGORY,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Category of product."
    ),
    PRODUCT_TYPE("Transaction", "Product type",
            ItemDataField.PRODUCT_TYPE,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Type of product sold."
    ),
    CUSTOM_PRODUCT_FAMILIES("Transaction", "Product families",
            ItemDataField.CUSTOM_PRODUCT_FAMILIES,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Product families sent by the merchant as 'product_families' field in custom purchase data."
    ),
    MERCHANT_PAYMENT_INSTRUMENT_ID("Credit card", "Merchant payment instrument ID",
            ItemDataField.MERCHANT_PAYMENT_INSTRUMENT_ID,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The identifier of the payment instrument. This information is provided by the merchant."
    ),
    CUSTOM_CC_HASH("Credit card", "Card hash",
            ItemDataField.CUSTOM_CC_HASH,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Cryptographically hashed card information. This attribute is used only for payments of the Credit/Debit Card type. It is retrieved from 'cc_hash' field in custom purchase data."
    ),
    PI_BIN("Credit card", "Card BIN",
            ItemDataField.PI_BIN,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The term bank identification number. This attribute is used only for payments of the Credit/Debit Card type."
    ),
    PI_COUNTRY("Credit card", "Billing address: Country",
            ItemDataField.PI_COUNTRY,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Country that is used in a billing address."
    ),
    PI_ZIP("Credit card", "Billing address: zip",
            ItemDataField.PI_ZIP,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Postal code that is used in a billing address."
    ),
    PI_ADDRESS("Credit card", "Billing address: Address",
            ItemDataField.PI_ADDRESS,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The whole billing address string concatenated from all address details."
    ),
    BILLING_COUNTRY_MATCHES_SHIPPING_COUNTRY("Credit card", "Matching with shipping address",
            ItemDataField.BILLING_COUNTRY_MATCHES_SHIPPING_COUNTRY,
            Set.of(IS_TRUE),
            null, null,
            "Matching with shipping address. Calculated near to time of purchase receiving."
    ),
    BILLING_COUNTRY_MATCHES_IP_COUNTRY("Credit card", "Matching with IP address",
            ItemDataField.BILLING_COUNTRY_MATCHES_IP_COUNTRY,
            Set.of(IS_TRUE),
            null, null,
            "Matching with IP address. Calculated near to time of purchase receiving."
    ),
    PAYMENT_GATEWAY("Transaction", "Payment gateway",
            ItemDataField.PAYMENT_GATEWAY,
            Set.of(IN, NOT_IN,  CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The value sent by banks as a payment processor."
    ),
    BANK_EVENT_RESULT_CODE("CC Authentication", "Bank response code",
            ItemDataField.BANK_EVENT_RESULT_CODE,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The 'BankResponseCode' field through all bank events."
    ),
    AUTH_BANK_EVENT_RESULT_CODE("CC Authentication", "Auth result code",
            ItemDataField.AUTH_BANK_EVENT_RESULT_CODE,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Result of CVV/AVS/other verifications. Collected as 'BankResponseCode' field in 'Auth' and 'AuthCancel' bank event types."
    ),
    APPROVE_BANK_EVENT_RESULT_CODE("CC Authentication", "Approve result code",
            ItemDataField.APPROVE_BANK_EVENT_RESULT_CODE,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "Results of approval bank events. Collected as 'BankResponseCode' field."
    ),
    DECLINE_BANK_EVENT_RESULT_CODE("CC Authentication", "Decline result code",
            ItemDataField.DECLINE_BANK_EVENT_RESULT_CODE,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),

            null, null,
            "Results of refusal bank events. Collected as 'BankResponseCode' field."
    ),
    DEVICE_CONTEXT_ID("Device", "Device context ID",
            ItemDataField.DEVICE_CONTEXT_ID,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The customer's fingerprinting session ID, or the event ID if the session is not available."
    ),
    EXTERNAL_DEVICE_ID("Device", "External device ID",
            ItemDataField.EXTERNAL_DEVICE_ID,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The customer's device ID, as provided and mastered by the merchant."
    ),
    DEVICE_CONTEXT_USER_AGENT("Device", "User agent",
            ItemDataField.DEVICE_CONTEXT_USER_AGENT,
            Set.of(IN, NOT_IN,  CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "User Agent collected during purchase making."
    ),
    IP_COUNTRY("Device", "IP country",
            ItemDataField.IP_COUNTRY,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "A country that is related to the purchase IP address."
    ),
    IP_COUNTRY_MATCHES_SHIPPING_COUNTRY("Device", "Matching with shipping address",
            ItemDataField.IP_COUNTRY_MATCHES_SHIPPING_COUNTRY,
            Set.of(IS_TRUE),
            null, null,
            "Matching with shipping address. Calculated near to time of purchase receiving."
    ),
    DISTANCE_TO_PREVIOUS_TRANSACTION_IP("Device", "Distance to previous transaction IP",
            ItemDataField.DISTANCE_TO_PREVIOUS_TRANSACTION_IP,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Distance between current transaction IP location and previous transaction IP location. Location is taken from DFP data. Calculated near to time of purchase receiving."
    ),
    LAST_HOUR_UNIQUE_IP_COUNTRIES("Velocity/Stats", "Transactions with unique IP Countries (1 hour)",
            ItemDataField.LAST_HOUR_UNIQUE_IP_COUNTRIES,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions with unique IP Countries made by the user last hour before current purchase. Collected near to time of purchase receiving."
    ),
    LAST_DAY_UNIQUE_IP_COUNTRIES("Velocity/Stats", "Transactions with unique IP Countries (1 day)",
            ItemDataField.LAST_DAY_UNIQUE_IP_COUNTRIES,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions with unique IP Countries made by the user last day before current purchase. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_UNIQUE_IP_COUNTRIES("Velocity/Stats", "Transactions with unique IP Countries (1 week)",
            ItemDataField.LAST_WEEK_UNIQUE_IP_COUNTRIES,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions with unique IP Countries made by the user last week before current purchase. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_UNIQUE_IP_COUNTRIES("Velocity/Stats", "Transactions with unique IP Countries (lifetime)",
            ItemDataField.LIFE_TIME_UNIQUE_IP_COUNTRIES,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of all transactions with unique IP Countries made by the user before current purchase. Collected near to time of purchase receiving."
    ),
    CUSTOM_CONNECTION_COUNT_BUCKET("Velocity/Stats", "Connection count bucket",
            ItemDataField.CUSTOM_CONNECTION_COUNT_BUCKET,
            Set.of(IN, NOT_IN, CONTAINS, EQUAL_ALPH, NOT_EQUAL_ALPH),
            null, null,
            "The parameter is retrieved from 'connection_count_bucket' field in custom purchase data."
    ),

    CUSTOM_LAST6MONTHS_INVITATIONS_SENT("Velocity/Stats", "Invitation sent count (6 months)",
            ItemDataField.CUSTOM_LAST6MONTHS_INVITATIONS_SENT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "The parameter is retrieved from 'num_invitations_sent_last_6_months' field in custom purchase data."
    ),
    CUSTOM_LAST_DAY_CARD_USAGE_COUNT("Velocity/Stats", "Transactions through the same card or member (24 hours)",
            ItemDataField.CUSTOM_LAST_DAY_CARD_USAGE_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "The parameter is retrieved from 'purchases_per_member_24_hours' field in custom purchase data."
    ),
    LAST_HOUR_TRANSACTION_COUNT("Velocity/Stats", "Transaction count (1 hour)",
            ItemDataField.LAST_HOUR_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_TRANSACTION_COUNT("Velocity/Stats", "Transaction count (1 day)",
            ItemDataField.LAST_DAY_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_TRANSACTION_COUNT("Velocity/Stats", "Transaction count (1 week)",
            ItemDataField.LAST_WEEK_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_TRANSACTION_COUNT("Velocity/Stats", "Transaction count (lifetime)",
            ItemDataField.LIFE_TIME_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of all transactions for the current user. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_SUCCESSFUL_TRANSACTION_COUNT("Velocity/Stats", "Successful transaction count (1 hour)",
            ItemDataField.LAST_HOUR_SUCCESSFUL_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of approved transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_SUCCESSFUL_TRANSACTION_COUNT("Velocity/Stats", "Successful transaction count (1 day)",
            ItemDataField.LAST_DAY_SUCCESSFUL_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of approved transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_SUCCESSFUL_TRANSACTION_COUNT("Velocity/Stats", "Successful transaction count (1 week)",
            ItemDataField.LAST_WEEK_SUCCESSFUL_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of approved transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_SUCCESSFUL_TRANSACTION_COUNT("Velocity/Stats", "Successful transaction count (lifetime)",
            ItemDataField.LIFE_TIME_SUCCESSFUL_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of all approved transactions for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_REJECTED_TRANSACTION_COUNT("Velocity/Stats", "Rejected transaction count (1 hour)",
            ItemDataField.LAST_HOUR_REJECTED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of rejected transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_REJECTED_TRANSACTION_COUNT("Velocity/Stats", "Rejected transaction count (1 day)",
            ItemDataField.LAST_DAY_REJECTED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of rejected transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_REJECTED_TRANSACTION_COUNT("Velocity/Stats", "Rejected transaction count (1 week)",
            ItemDataField.LAST_WEEK_REJECTED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of rejected transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_REJECTED_TRANSACTION_COUNT("Velocity/Stats", "Rejected transaction count (lifetime)",
            ItemDataField.LIFE_TIME_REJECTED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of all rejected transactions for that user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_FAILED_TRANSACTION_COUNT("Velocity/Stats", "Failed transaction count (1 hour)",
            ItemDataField.LAST_HOUR_FAILED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of failed transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_FAILED_TRANSACTION_COUNT("Velocity/Stats", "Failed transaction count (1 day)",
            ItemDataField.LAST_DAY_FAILED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of failed transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_FAILED_TRANSACTION_COUNT("Velocity/Stats", "Failed transaction count (1 week)",
            ItemDataField.LAST_WEEK_FAILED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of failed transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_FAILED_TRANSACTION_COUNT("Velocity/Stats", "Failed transaction count (lifetime)",
            ItemDataField.LIFE_TIME_FAILED_TRANSACTION_COUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of all failed transactions for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_TRANSACTION_AMOUNT("Velocity/Stats", "Transaction amount (1 hour)",
            ItemDataField.LAST_HOUR_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_TRANSACTION_AMOUNT("Velocity/Stats", "Transaction amount (1 day)",
            ItemDataField.LAST_DAY_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_TRANSACTION_AMOUNT("Velocity/Stats", "Transaction amount (1 week)",
            ItemDataField.LAST_WEEK_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_TRANSACTION_AMOUNT("Velocity/Stats", "Transaction amount (lifetime)",
            ItemDataField.LIFE_TIME_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Amount of all expenses for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_SUCCESSFUL_TRANSACTION_AMOUNT("Velocity/Stats", "Successful transaction amount (1 hour)",
            ItemDataField.LAST_HOUR_SUCCESSFUL_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in approved transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_SUCCESSFUL_TRANSACTION_AMOUNT("Velocity/Stats", "Successful transaction amount (1 day)",
            ItemDataField.LAST_DAY_SUCCESSFUL_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in approved transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_SUCCESSFUL_TRANSACTION_AMOUNT("Velocity/Stats", "Successful transaction amount (1 week)",
            ItemDataField.LAST_WEEK_SUCCESSFUL_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in approved transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_SUCCESSFUL_TRANSACTION_AMOUNT("Velocity/Stats", "Successful transaction amount (lifetime)",
            ItemDataField.LIFE_TIME_SUCCESSFUL_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Amount of all expenses in approved transactions for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_REJECTED_TRANSACTION_AMOUNT("Velocity/Stats", "Rejected transaction amount (1 hour)",
            ItemDataField.LAST_HOUR_REJECTED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in rejected transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_REJECTED_TRANSACTION_AMOUNT("Velocity/Stats", "Rejected transaction amount (1 day)",
            ItemDataField.LAST_DAY_REJECTED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in rejected transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_REJECTED_TRANSACTION_AMOUNT("Velocity/Stats", "Rejected transaction amount (1 week)",
            ItemDataField.LAST_WEEK_REJECTED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in rejected transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_REJECTED_TRANSACTION_AMOUNT("Velocity/Stats", "Rejected transaction amount (lifetime)",
            ItemDataField.LIFE_TIME_REJECTED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Amount of all expenses in rejected transactions for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_FAILED_TRANSACTION_AMOUNT("Velocity/Stats", "Failed transaction amount (1 hour)",
            ItemDataField.LAST_HOUR_FAILED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in failed transactions for the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_FAILED_TRANSACTION_AMOUNT("Velocity/Stats", "Failed transaction amount (1 day)",
            ItemDataField.LAST_DAY_FAILED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in failed transactions for the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_FAILED_TRANSACTION_AMOUNT("Velocity/Stats", "Failed transaction amount (1 week)",
            ItemDataField.LAST_WEEK_FAILED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses in failed transactions for the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_FAILED_TRANSACTION_AMOUNT("Velocity/Stats", "Failed transaction amount (lifetime)",
            ItemDataField.LIFE_TIME_FAILED_TRANSACTION_AMOUNT,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Amount of all expenses in failed transactions for the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_UNIQUE_PI("Velocity/Stats", "Unique used payment instruments count (1 hour)",
            ItemDataField.LAST_HOUR_UNIQUE_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of unique payment instruments used by the user last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_UNIQUE_PI("Velocity/Stats", "Unique used payment instruments count (1 day)",
            ItemDataField.LAST_DAY_UNIQUE_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of unique payment instruments used by the user last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_UNIQUE_PI("Velocity/Stats", "Unique used payment instruments count (1 week)",
            ItemDataField.LAST_WEEK_UNIQUE_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of unique payment instruments used by the user last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_UNIQUE_PI("Velocity/Stats", "Unique used payment instruments count (lifetime)",
            ItemDataField.LIFE_TIME_UNIQUE_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of unique payment instruments used by the user before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_TRANSACTION_COUNT_CURRENT_PI("Velocity/Stats", "Transaction count with current payment instrument (1 hour)",
            ItemDataField.LAST_HOUR_TRANSACTION_COUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions made with the usage of payment instruments from the current transaction during the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_TRANSACTION_COUNT_CURRENT_PI("Velocity/Stats", "Transaction count with current payment instrument (1 day)",
            ItemDataField.LAST_DAY_TRANSACTION_COUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions made with the usage of payment instruments from the current transaction during the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_TRANSACTION_COUNT_CURRENT_PI("Velocity/Stats", "Transaction count with current payment instrument (1 week)",
            ItemDataField.LAST_WEEK_TRANSACTION_COUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions made with the usage of payment instruments from the current transaction during the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_TRANSACTION_COUNT_CURRENT_PI("Velocity/Stats", "Transaction count with current payment instrument (lifetime)",
            ItemDataField.LAST_WEEK_TRANSACTION_COUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Count of transactions made with the usage of payment instruments from the current transaction before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_HOUR_TRANSACTION_AMOUNT_CURRENT_PI("Velocity/Stats", "Transaction amount with current payment instrument (1 hour)",
            ItemDataField.LAST_HOUR_TRANSACTION_AMOUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses processed with the usage of payment instruments from the current transaction during the last hour before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_DAY_TRANSACTION_AMOUNT_CURRENT_PI("Velocity/Stats", "Transaction amount with current payment instrument (1 day)",
            ItemDataField.LAST_DAY_TRANSACTION_AMOUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses processed with the usage of payment instruments from the current transaction during the last day before purchase making. Collected near to time of purchase receiving."
    ),
    LAST_WEEK_TRANSACTION_AMOUNT_CURRENT_PI("Velocity/Stats", "Transaction amount with current payment instrument (1 week)",
            ItemDataField.LAST_WEEK_TRANSACTION_AMOUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses processed with the usage of payment instruments from the current transaction during the last week before purchase making. Collected near to time of purchase receiving."
    ),
    LIFE_TIME_TRANSACTION_AMOUNT_CURRENT_PI("Velocity/Stats", "Transaction amount with current payment instrument (lifetime)",
            ItemDataField.LIFE_TIME_TRANSACTION_AMOUNT_CURRENT_PI,
            Set.of(BETWEEN, NOT_BETWEEN, EQUAL, NOT_EQUAL, LESS, GREATER, LESS_OR_EQUAL, GREATER_OR_EQUAL),
            "0", null,
            "Expenses processed with the usage of payment instruments from the current transaction before purchase making. Collected near to time of purchase receiving."
    );

    @Getter
    private final String category;
    @Getter
    private final String displayName;
    @Getter
    private final ItemDataField itemDataField;
    @Getter
    private final Set<ItemDataFieldCondition> acceptableConditions;
    @Getter
    private final String lowerBound;
    @Getter
    private final String upperBound;
    @Getter
    private final String description;
}
