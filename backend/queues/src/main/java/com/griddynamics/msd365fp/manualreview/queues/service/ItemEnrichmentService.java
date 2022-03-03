// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.cosmos.utilities.PageProcessingUtility;
import com.griddynamics.msd365fp.manualreview.model.Decision;
import com.griddynamics.msd365fp.manualreview.model.DisposabilityCheck;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.model.dfp.*;
import com.griddynamics.msd365fp.manualreview.model.dfp.raw.*;
import com.griddynamics.msd365fp.manualreview.model.exception.BusyException;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.repository.ItemRepository;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GeodeticCurve;
import org.gavaghan.geodesy.GlobalCoordinates;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.griddynamics.msd365fp.manualreview.queues.config.Constants.DEFAULT_ITEM_PAGE_SIZE;
import static java.util.stream.Collectors.groupingBy;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItemEnrichmentService {

    public static final String SHIPPING_ADDRESS_TYPE = "SHIPPING";
    public static final String BILLING_ADDRESS_TYPE = "BILLING";

    public static final String APPROVED_TRANSACTION_STATUS = "Approved";
    public static final String FAILED_TRANSACTION_STATUS = "Failed";
    public static final String REJECTED_TRANSACTION_STATUS = "Rejected";

    public static final String APPROVED_BANKEVENT_STATUS = "Approved";
    public static final String DECLINED_BANKEVENT_STATUS = "Declined";
    public static final String AUTH_BANKEVENT_TYPE = "Auth";
    public static final String AUTHCANCEL_BANKEVENT_TYPE = "AuthCancel";

    public static final String EMAIL_CONFIRMED_CUSTOM_DATA_KEY = "email_confirmed";
    public static final String EMAIL_DOMAIN_CUSTOM_DATA_KEY = "email_domain";

    private final StreamService streamService;
    private final EmailDomainService emailDomainService;
    private final ItemRepository itemRepository;
    private final DFPExplorerService dfpExplorerService;

    @Setter(onMethod = @__({@Autowired}))
    private ItemEnrichmentService thisService;
    @Setter(onMethod = @__({@Autowired, @Qualifier("dfpModelMapper")}))
    private ModelMapper modelMapper;
    @Setter(onMethod = @__({@Value("${mr.tasks.item-enrichment-task.enrichment-delay}")}))
    private Duration enrichmentDelay;
    @Setter(onMethod = @__({@Value("${mr.tasks.item-enrichment-task.max-enrichment-delay}")}))
    private Duration maxEnrichmentDelay;
    @Setter(onMethod = @__({@Value("${mr.tasks.item-enrichment-task.max-enrichment-attempts}")}))
    private Integer maxEnrichmentAttempts;
    @Setter(onMethod = @__({@Value("${mr.tasks.item-enrichment-task.history-depth}")}))
    private int historyDepth;
    @Setter(onMethod = @__({@Value("${azure.cosmosdb.default-ttl}")}))
    private Duration defaultTtl;

    private final GeodeticCalculator geoCalc = new GeodeticCalculator();


    public boolean enrichAllPoorItems(boolean forceEnrichment) throws BusyException {
        PageProcessingUtility.executeForAllPages(
                continuationToken -> {
                    PageableCollection<String> unenrichedItemIds;
                    if (forceEnrichment) {
                        unenrichedItemIds = itemRepository.findUnenrichedItemIds(DEFAULT_ITEM_PAGE_SIZE, continuationToken);
                    } else {
                        OffsetDateTime importedUpperBoundary = OffsetDateTime.now().minus(enrichmentDelay);
                        unenrichedItemIds = itemRepository.findUnenrichedItemIds(
                                importedUpperBoundary, DEFAULT_ITEM_PAGE_SIZE, continuationToken);
                    }
                    return unenrichedItemIds;
                },
                itemCollection -> {
                    log.info("Trying to enrich items with IDs: [{}]", itemCollection.getValues());
                    itemCollection.forEach(item -> thisService.enrichItem(item, forceEnrichment));
                });
        return true;
    }

    @Retry(name = "cosmosOptimisticUpdate")
    public void enrichItem(String itemId, boolean forceEnrichment) {
        log.info("Attempting to enrich item [{}].", itemId);

        // 1. Get item from database
        Optional<Item> itemFromDB = itemRepository.findById(itemId);
        if (itemFromDB.isEmpty()) {
            log.warn("Item [{}] enrichment failed. Item wasn't found in the database.", itemId);
            return;
        }
        Item item = itemFromDB.get();
        if (!forceEnrichment && item.getEnriched() != null) {
            log.warn("Item [{}] has already been enriched and " +
                    "enrichment has been ran without forcing. Enrichment is skipped.", itemId);
            return;
        }
        item.setEnrichmentAttempts(Objects.requireNonNullElse(item.getEnrichmentAttempts(), 0) + 1);

        try {
            // 2. Get main purchase data from DFP
            ExplorerEntity mainEntity = dfpExplorerService.explorePurchase(itemId);

            // 3. Map main data
            if (mainEntity.getNodes().stream().noneMatch(n -> n.getData() instanceof PurchaseNodeData)) {
                if (item.getImported().plus(maxEnrichmentDelay).isBefore(OffsetDateTime.now())) {
                    log.error("Item [{}] can't be enriched during max delay period.", itemId);
                    item.setEnrichmentFailed(true);
                    item.setTtl(defaultTtl.toSeconds());
                    item.setEnrichmentFailReason("There is no purchase information in DFP during maximum delay period");
                    itemRepository.save(item);
                } else {
                    log.info("Item [{}] has not required purchase information in DFP. Enrichment is postponed", itemId);
                }
                return;
            }
            if (mainEntity.getNodes().stream().noneMatch(n -> n.getData() instanceof UserNodeData)) {
                if (item.getImported().plus(maxEnrichmentDelay).isBefore(OffsetDateTime.now())) {
                    log.warn("Item [{}] doesn't have user info but maximum postponing is spent. Start enriching without user info.", itemId);
                } else {
                    log.info("Item [{}] doesn't have user info. Enrichment is postponed.", itemId);
                    return;
                }
            }
            mapMainEntityToItem(item, mainEntity);

            // 4. Get and map billing data for main purchase
            enrichPurchasePaymentData(item.getPurchase());

            // 5. Get user data from DFP and map to main purchase
            ExplorerEntity userEntity = getUserEntity(mainEntity);
            mapPurchaseHistoryToMainPurchase(item.getPurchase(), userEntity);

            // 6. Get and map data for detailed transaction history
            item.getPurchase().getPreviousPurchaseList().forEach(previousPurchase -> {
                mapPreviousPurchaseEntityToPreviousPurchase(
                        previousPurchase,
                        dfpExplorerService.explorePurchase(previousPurchase.getPurchaseId()));
                enrichPurchasePaymentData(previousPurchase);
            });

            // 7. Calculate derived fields
            calculateDerivedFields(item);

            //remove previous transactions that are too old.
            removeOldPreviousPurchases(item.getPurchase());

            // 8. Save item
            boolean itemWasNew = item.getEnriched() == null;
            if (itemWasNew) {
                item.setActive(true);
            }
            item.setEnrichmentFailed(false);
            item.setEnriched(OffsetDateTime.now());
            itemRepository.save(item);
            if (itemWasNew) {
                streamService.sendItemAssignmentEvent(item);
            }
            log.info("Item [{}] has been successfully enriched in the database.", item.getId());
        } catch (CosmosAccessException ignored) {
            // ignored because it will be consumed by retry mechanism
            log.info("Optimistic lock exception during enrichment of [{}].", itemId);
        } catch (Exception e) {
            // 1. mark item failed if attempt amounts are exhausted
            log.warn("Exception during enrichment of [{}]: {}", itemId, e.getMessage());
            log.warn("Exception during enrichment of [{}].", itemId, e);
            if (maxEnrichmentAttempts <= item.getEnrichmentAttempts()) {
                log.error("Item [{}] can't be enriched during max attempts and marked as failed.", itemId);
                item.setEnrichmentFailed(true);
                item.setTtl(defaultTtl.toSeconds());
                item.setEnrichmentFailReason("Can't be enriched during max attempts");
            }
            itemRepository.save(item);
            // 2. stop current enrichment process
            throw e;
        }
    }

    private void removeOldPreviousPurchases(final MainPurchase mainPurchase) {
        //We need all the purchases group by original Order Id
        Map<String, List<PreviousPurchase>> actualPurchaseHistory = mainPurchase.getPreviousPurchaseList().stream()
                .sorted(Comparator.comparing(PreviousPurchase::getMerchantLocalDate).reversed())
                .filter(pn -> pn.getMerchantLocalDate().isAfter(mainPurchase.getMerchantLocalDate().minusWeeks(1L)))
                .collect(groupingBy(PreviousPurchase::getOriginalOrderId));

        //if the groups are more than history depth - delete the ones that are at the end of the grouping map, since they are sorted by date desc
        if (actualPurchaseHistory.size()>= historyDepth) {
            Integer current = 0;
            for(Iterator<String> iterator = actualPurchaseHistory.keySet().iterator(); iterator.hasNext(); ) {
                iterator.next();
                if(current > historyDepth)
                {
                    iterator.remove();
                }
                current++;
            }
        }

        //after we are done with adjusting the number - flatten the group map back to normal list and assign it to the main purchase
        List<PreviousPurchase> result =  actualPurchaseHistory
                .entrySet().stream()
                .flatMap(m -> m.getValue().stream())
                .collect(Collectors.toList());
        mainPurchase.setPreviousPurchaseList(result);
    }

    private ExplorerEntity getUserEntity(final ExplorerEntity mainEntity) {
        return mainEntity.getNodes().stream()
                .filter(n -> n.getData() instanceof UserNodeData)
                .map(n -> dfpExplorerService.exploreUser(n.getId()))
                .findFirst().orElse(ExplorerEntity.EMPTY);
    }

    private void enrichPurchasePaymentData(final Purchase purchase) {
        if (purchase.getPaymentInstrumentList() == null || purchase.getPaymentInstrumentList().isEmpty()) {
            return;
        }
        List<ExplorerEntity> paymentInstrumentEntities = purchase.getPaymentInstrumentList().stream()
                .map(PaymentInstrument::getPaymentInstrumentId)
                .map(dfpExplorerService::explorePaymentInstrument)
                .collect(Collectors.toList());

        mapPIEntitiesToPurchase(purchase, paymentInstrumentEntities);
    }

    private void mapMainEntityToItem(final Item item, final ExplorerEntity entity) {
        DeviceContext deviceContext = new DeviceContext();
        Map<String, PaymentInstrument> paymentInstrumentMap = new HashMap<>();
        Map<String, Product> productMap = new HashMap<>();
        Map<String, BankEvent> bankEventMap = new HashMap<>();
        Address shippingAddress = new Address();
        AssesmentResult assesmentResult = new AssesmentResult();
        Map<String, PurchaseStatus> purchaseStatusMap = new HashMap<>();
        User user = new User();
        Map<String, Map<String, Object>> additionalInfo = new HashMap<>();
        Set<String> foundEntities = new HashSet<>();
        MainPurchase purchase = new MainPurchase();

        entity.getNodes().forEach(node -> {
            switch (node.getName()) {
                case AddressNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), shippingAddress);
                    break;
                case BankEventNodeData.NODE_NAME:
                    modelMapper.map(node.getData(), bankEventMap.computeIfAbsent(
                            node.getId(),
                            key -> new BankEvent()));
                    break;
                case DeviceContextNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), deviceContext);
                    break;
                case PaymentInstrumentNodeData.NODE_NAME:
                    modelMapper.map(node.getData(), paymentInstrumentMap.computeIfAbsent(
                            node.getId(),
                            key -> new PaymentInstrument()));
                    break;
                case ProductNodeData.NODE_NAME:
                    modelMapper.map(node.getData(), productMap.computeIfAbsent(
                            node.getId(),
                            key -> new Product()));
                    break;
                case PurchaseNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), purchase);
                    modelMapper.map(node.getData(), assesmentResult);
                    break;
                case UserNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), user);
                    break;
                default:
                    modelMapper.map(node.getData(), additionalInfo.computeIfAbsent(
                            node.getName() + node.getId(),
                            key -> new HashMap<>()));
            }
            foundEntities.add(node.getName());
        });


        entity.getEdges().forEach(edge -> {
            switch (edge.getName()) {
                case PurchaseAddressEdgeData.EDGE_DIRECT_NAME:
                case PurchaseAddressEdgeData.EDGE_REVERSED_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, edge.getName());
                    modelMapper.map(edge.getData(), shippingAddress);
                    if (!SHIPPING_ADDRESS_TYPE.equals(shippingAddress.getType())) {
                        log.warn("DFP gave purchase-address edge without SHIPPING type for item [{}].", item.getId());
                        shippingAddress.setType(SHIPPING_ADDRESS_TYPE);
                    }
                    break;
                case PurchaseBankEventEdgeData.EDGE_DIRECT_NAME:
                case PurchaseBankEventEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), bankEventMap.computeIfAbsent(
                            ((PurchaseBankEventEdgeData) edge.getData()).getBankEventId(),
                            key -> new BankEvent()));
                    break;
                case PurchaseDeviceContextEdgeData.EDGE_DIRECT_NAME:
                case PurchaseDeviceContextEdgeData.EDGE_REVERSED_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, edge.getName());
                    modelMapper.map(edge.getData(), deviceContext);
                    break;
                case PurchasePaymentInstrumentEdgeData.EDGE_DIRECT_NAME:
                case PurchasePaymentInstrumentEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), paymentInstrumentMap.computeIfAbsent(
                            ((PurchasePaymentInstrumentEdgeData) edge.getData()).getPaymentInstrumentId(),
                            key -> new PaymentInstrument()));
                    break;
                case PurchaseProductEdgeData.EDGE_DIRECT_NAME:
                case PurchaseProductEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), productMap.computeIfAbsent(
                            ((PurchaseProductEdgeData) edge.getData()).getProductId(),
                            key -> new Product()));
                    break;
                case PurchaseStatusEdgeData.EDGE_DIRECT_NAME:
                case PurchaseStatusEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), purchaseStatusMap.computeIfAbsent(
                            edge.getId(),
                            key -> new PurchaseStatus()));
                    break;
                case PurchaseUserEdgeData.EDGE_DIRECT_NAME:
                case PurchaseUserEdgeData.EDGE_REVERSED_NAME:
                    checkSingletonDataDuplicate(item.getId(), foundEntities, edge.getName());
                    modelMapper.map(edge.getData(), user);
                    break;
                default:
                    modelMapper.map(edge.getData(), additionalInfo.computeIfAbsent(
                            edge.getName() + edge.getId(),
                            key -> new HashMap<>()));
            }
            foundEntities.add(edge.getName());
        });

        if (SHIPPING_ADDRESS_TYPE.equalsIgnoreCase(shippingAddress.getType())) {
            purchase.setAddressList(new ArrayList<>(Set.of(shippingAddress)));
        }
        purchase.setBankEventsList(new ArrayList<>(bankEventMap.values()));
        purchase.setDeviceContext(deviceContext);
        purchase.setPaymentInstrumentList(new ArrayList<>(paymentInstrumentMap.values()));
        purchase.setProductList(new ArrayList<>(productMap.values()));
        purchase.setUser(user);
        assesmentResult.setPurchaseStatusList(new ArrayList<>(purchaseStatusMap.values()));
        item.setPurchase(purchase);
        item.setAssessmentResult(assesmentResult);
        item.setDecision(Decision.builder()
                .riskScore(assesmentResult.getRiskScore())
                .reasonCodes(assesmentResult.getReasonCodes())
                .build());
    }

    private void mapPIEntitiesToPurchase(final Purchase purchase, final List<ExplorerEntity> piEntities) {
        if (purchase.getPaymentInstrumentList() == null || purchase.getPaymentInstrumentList().isEmpty()) {
            return;
        }

        Map<String, PaymentInstrument> paymentInstrumentMap = new HashMap<>();
        Map<String, Address> billingAddressMap = new HashMap<>();

        piEntities.forEach(entity -> {
            entity.getNodes().forEach(node -> {
                switch (node.getName()) {
                    case AddressNodeData.NODE_NAME:
                        AddressNodeData nodeData = ((AddressNodeData) node.getData());
                        modelMapper.map(node.getData(), billingAddressMap.computeIfAbsent(
                                nodeData.getAddressId(),
                                key -> new Address()));
                        break;
                    case PaymentInstrumentNodeData.NODE_NAME:
                        modelMapper.map(node.getData(), paymentInstrumentMap.computeIfAbsent(
                                node.getId(),
                                key -> new PaymentInstrument()));
                        break;
                    default:
                        break;
                }
            });
            entity.getEdges().forEach(edge -> {
                switch (edge.getName()) {
                    case PaymentInstrumentAddressEdgeData.EDGE_DIRECT_NAME:
                    case PaymentInstrumentAddressEdgeData.EDGE_REVERSED_NAME:
                        PaymentInstrumentAddressEdgeData edgeData =
                                ((PaymentInstrumentAddressEdgeData) edge.getData());
                        Address billingAddress = billingAddressMap.computeIfAbsent(
                                edgeData.getAddressId(),
                                key -> new Address());
                        modelMapper.map(edge.getData(), billingAddress);
                        paymentInstrumentMap
                                .computeIfAbsent(
                                        edgeData.getPaymentInstrumentId(),
                                        key -> new PaymentInstrument())
                                .setAddressId(edgeData.getAddressId());
                        if (!BILLING_ADDRESS_TYPE.equals(billingAddress.getType())) {
                            log.warn("DFP gave paymentInstrument-address edge without BILLING type for payment instrument [{}].", edgeData.getPaymentInstrumentId());
                            billingAddress.setType(BILLING_ADDRESS_TYPE);
                        }
                        break;
                    default:
                        break;
                }
            });
        });

        purchase.getPaymentInstrumentList().stream()
                .filter(pi -> paymentInstrumentMap.containsKey(pi.getPaymentInstrumentId()))
                .forEach(pi -> modelMapper.map(paymentInstrumentMap.get(pi.getPaymentInstrumentId()), pi));
        if (purchase.getAddressList() == null) {
            purchase.setAddressList(new LinkedList<>());
        }
        if(billingAddressMap.size()>0) {
            purchase.getAddressList().add((Address) billingAddressMap.values().toArray()[billingAddressMap.size() - 1]);
        }
    }

    private void mapPurchaseHistoryToMainPurchase(final MainPurchase mainPurchase, ExplorerEntity userEntity) {
        mainPurchase.setPreviousPurchaseList(userEntity.getNodes().stream()
                .filter(n -> n.getData() instanceof PurchaseNodeData)
                .map(n -> ((PurchaseNodeData) n.getData()))
                .filter(n -> !mainPurchase.getPurchaseId().equals(n.getPurchaseId()))
                .filter(n -> n.getMerchantLocalDate() != null &&
                        n.getMerchantLocalDate().isBefore(mainPurchase.getMerchantLocalDate())
                )
                .sorted((n1, n2) -> n1.getMerchantLocalDate().isBefore( n2.getMerchantLocalDate())? 1 : -1)
                .map(n -> modelMapper.map(n, PreviousPurchase.class))
                .collect(Collectors.toList()));
    }

    private void mapPreviousPurchaseEntityToPreviousPurchase(
            PreviousPurchase purchase,
            ExplorerEntity entity) {
        boolean containsRequiredData = entity.getNodes().stream()
                .anyMatch(n -> n.getData() instanceof PurchaseNodeData);
        if (!containsRequiredData) {
            log.warn("Entity [{}] doesn't contain purchase data. The purchase is skipped",
                    entity.getRequestAttributeValue());
            return;
        }

        DeviceContext deviceContext = new DeviceContext();
        Map<String, PaymentInstrument> paymentInstrumentMap = new HashMap<>();
        Map<String, BankEvent> bankEventMap = new HashMap<>();
        Address shippingAddress = new Address();
        Map<String, PurchaseStatus> purchaseStatusMap = new HashMap<>();
        Set<String> foundEntities = new HashSet<>();

        entity.getNodes().forEach(node -> {
            switch (node.getName()) {
                case AddressNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(entity.getRequestAttributeValue(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), shippingAddress);
                    break;
                case BankEventNodeData.NODE_NAME:
                    modelMapper.map(node.getData(), bankEventMap.computeIfAbsent(
                            node.getId(),
                            key -> new BankEvent()));
                    break;
                case DeviceContextNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(entity.getRequestAttributeValue(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), deviceContext);
                    break;
                case PaymentInstrumentNodeData.NODE_NAME:
                    modelMapper.map(node.getData(), paymentInstrumentMap.computeIfAbsent(
                            node.getId(),
                            key -> new PaymentInstrument()));
                    break;
                case PurchaseNodeData.NODE_NAME:
                    checkSingletonDataDuplicate(entity.getRequestAttributeValue(), foundEntities, node.getName());
                    modelMapper.map(node.getData(), purchase);
                    break;
                default:
                    break;
            }
            foundEntities.add(node.getName());
        });

        entity.getEdges().forEach(edge -> {
            switch (edge.getName()) {
                case PurchaseAddressEdgeData.EDGE_DIRECT_NAME:
                case PurchaseAddressEdgeData.EDGE_REVERSED_NAME:
                    checkSingletonDataDuplicate(entity.getRequestAttributeValue(), foundEntities, edge.getName());
                    modelMapper.map(edge.getData(), shippingAddress);
                    break;
                case PurchaseBankEventEdgeData.EDGE_DIRECT_NAME:
                case PurchaseBankEventEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), bankEventMap.computeIfAbsent(
                            ((PurchaseBankEventEdgeData) edge.getData()).getBankEventId(),
                            key -> new BankEvent()));
                    break;
                case PurchaseDeviceContextEdgeData.EDGE_DIRECT_NAME:
                case PurchaseDeviceContextEdgeData.EDGE_REVERSED_NAME:
                    checkSingletonDataDuplicate(entity.getRequestAttributeValue(), foundEntities, edge.getName());
                    modelMapper.map(edge.getData(), deviceContext);
                    break;
                case PurchasePaymentInstrumentEdgeData.EDGE_DIRECT_NAME:
                case PurchasePaymentInstrumentEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), paymentInstrumentMap.computeIfAbsent(
                            ((PurchasePaymentInstrumentEdgeData) edge.getData()).getPaymentInstrumentId(),
                            key -> new PaymentInstrument()));
                    break;
                case PurchaseStatusEdgeData.EDGE_DIRECT_NAME:
                case PurchaseStatusEdgeData.EDGE_REVERSED_NAME:
                    modelMapper.map(edge.getData(), purchaseStatusMap.computeIfAbsent(
                            edge.getId(),
                            key -> new PurchaseStatus()));
                    break;
                default:
                    break;
            }
            foundEntities.add(edge.getName());
        });

        if (SHIPPING_ADDRESS_TYPE.equalsIgnoreCase(shippingAddress.getType())) {
            purchase.setAddressList(new ArrayList<>(Set.of(shippingAddress)));
        }
        purchase.setBankEventsList(new ArrayList<>(bankEventMap.values()));
        purchase.setDeviceContext(deviceContext);
        purchase.setPaymentInstrumentList(new ArrayList<>(paymentInstrumentMap.values()));
        Optional<PurchaseStatus> lastMerchantStatus = purchaseStatusMap.values().stream()
                .max(Comparator.comparing(PurchaseStatus::getStatusDate));
        Optional<BankEvent> lastBankEvent = bankEventMap.values().stream()
                .max(Comparator.comparing(BankEvent::getBankEventTimestamp));
        lastMerchantStatus.ifPresent(status -> {
            purchase.setLastMerchantStatus(status.getStatusType());
            purchase.setLastMerchantStatusReason(status.getReason());
            purchase.setLastBankEventDate(status.getStatusDate());
        });
        lastBankEvent.ifPresent(event -> {
            purchase.setLastBankEventResponseCode(event.getBankResponseCode());
            purchase.setLastBankEventStatus(event.getStatus());
            purchase.setLastBankEventDate(event.getBankEventTimestamp());
        });
    }

    private void checkSingletonDataDuplicate(final String purchaseId, final Set<String> foundEntities, final String name) {
        if (foundEntities.contains(name)) {
            log.warn("Purchase [{}] contains duplicated [{}]", purchaseId, name);
        }
    }

    private void calculateDerivedFields(final Item item) {
        MainPurchase purchase = item.getPurchase();
        CalculatedFields calculatedFields = new CalculatedFields();

        Optional<DeviceContext> deviceContext = Optional.ofNullable(purchase.getDeviceContext());
        Optional<PreviousPurchase> firstKnownPurchase = purchase.getPreviousPurchaseList().stream()
                .min(Comparator.comparing(Purchase::getMerchantLocalDate));
        Optional<PreviousPurchase> lastPurchase = purchase.getPreviousPurchaseList().stream()
                .max(Comparator.comparing(Purchase::getMerchantLocalDate));

        if (CollectionUtils.isNotEmpty(purchase.getAddressList())) {
            Optional<Address> shippingAddress = purchase.getAddressList().stream()
                    .filter(a -> SHIPPING_ADDRESS_TYPE.equals(a.getType()))
                    .findFirst();
            List<Address> billingAddresses = purchase.getAddressList().stream()
                    .filter(a -> BILLING_ADDRESS_TYPE.equals(a.getType()))
                    .collect(Collectors.toList());

            calculatedFields.setMatchingOfCountriesForShippingAndIP(
                    shippingAddress.isPresent() &&
                            deviceContext.isPresent() &&
                            shippingAddress.get().getCountry() != null &&
                            shippingAddress.get().getCountry().equals(deviceContext.get().getIpCountry())
            );
            calculatedFields.setMatchingOfCountriesForBillingAndShipping(
                    shippingAddress.isPresent() &&
                            shippingAddress.get().getCountry() != null &&
                            billingAddresses.stream().anyMatch(ba -> shippingAddress.get().getCountry().equals(ba.getCountry()))
            );
            calculatedFields.setMatchingOfCountriesForBillingAndIP(
                    deviceContext.isPresent() &&
                            deviceContext.get().getIpCountry() != null &&
                            billingAddresses.stream()
                                    .anyMatch(ba -> deviceContext.get().getIpCountry().equals(ba.getCountry()))
            );
            if (deviceContext.isPresent() &&
                    deviceContext.get().getIpLongitude() != null &&
                    deviceContext.get().getIpLatitude() != null &&
                    lastPurchase.isPresent() && lastPurchase.get().getDeviceContext() != null &&
                    lastPurchase.get().getDeviceContext().getIpLongitude() != null &&
                    lastPurchase.get().getDeviceContext().getIpLatitude() != null) {
                GlobalCoordinates currentCoordinates = new GlobalCoordinates(
                        deviceContext.get().getIpLatitude().doubleValue(),
                        deviceContext.get().getIpLongitude().doubleValue());
                GlobalCoordinates previousCoordinates = new GlobalCoordinates(
                        deviceContext.get().getIpLatitude().doubleValue(),
                        deviceContext.get().getIpLongitude().doubleValue());
                GeodeticCurve curve = geoCalc.calculateGeodeticCurve(
                        Ellipsoid.WGS84, currentCoordinates, previousCoordinates);
                calculatedFields.setDistanceToPreviousTransactionIP(
                        BigDecimal.valueOf(curve.getEllipsoidalDistance()));
            }

            calculatedFields.setBillingCountries(billingAddresses.stream()
                    .map(Address::getCountry)
                    .collect(Collectors.toList()));
            calculatedFields.setBillingZipCodes(billingAddresses.stream()
                    .map(Address::getZipCode)
                    .collect(Collectors.toList()));
            calculatedFields.setBillingAddresses(billingAddresses.stream()
                    .map(address -> String.join(" ",
                            Objects.requireNonNullElse(address.getStreet1(), ""),
                            Objects.requireNonNullElse(address.getStreet2(), ""),
                            Objects.requireNonNullElse(address.getStreet3(), ""),
                            Objects.requireNonNullElse(address.getDistrict(), ""),
                            Objects.requireNonNullElse(address.getCity(), ""),
                            Objects.requireNonNullElse(address.getState(), ""),
                            Objects.requireNonNullElse(address.getCountry(), ""),
                            Objects.requireNonNullElse(address.getZipCode(), "")))
                    .collect(Collectors.toList()));
        }

        if (purchase.getUser().getCreationDate() != null) {
            calculatedFields.setAccountAgeInDays(
                    Duration.between(purchase.getUser().getCreationDate(), purchase.getMerchantLocalDate()).toDays());
        }
        firstKnownPurchase.ifPresent(fkp -> {
            calculatedFields.setFirstTransactionDateTime(fkp.getMerchantLocalDate());
            calculatedFields.setActivityAgeInDays(
                    Duration.between(fkp.getMerchantLocalDate(), purchase.getMerchantLocalDate()).toDays());
        });

        calculatedFields.setAggregatedEmailConfirmed(
                (purchase.getUser() != null &&
                        purchase.getUser().getIsEmailValidated() != null &&
                        purchase.getUser().getIsEmailValidated())
                        ||
                        (purchase.getCustomData() != null &&
                                purchase.getCustomData().containsKey(EMAIL_CONFIRMED_CUSTOM_DATA_KEY) &&
                                Boolean.parseBoolean(purchase.getCustomData().get(EMAIL_CONFIRMED_CUSTOM_DATA_KEY)))
        );
        if (purchase.getCustomData() != null && purchase.getCustomData().containsKey(EMAIL_DOMAIN_CUSTOM_DATA_KEY)) {
            calculatedFields.setAggregatedEmailDomain(purchase.getCustomData().get(EMAIL_DOMAIN_CUSTOM_DATA_KEY));
        } else if (purchase.getUser() != null && purchase.getUser().getEmail() != null) {
            String email = purchase.getUser().getEmail();
            int domainBeginning = email.indexOf('@');
            if (domainBeginning > 0 && (domainBeginning + 1) < email.length()) {
                calculatedFields.setAggregatedEmailDomain(email.substring(domainBeginning + 1));
            }
        }

        DisposabilityCheck disposabilityCheck = emailDomainService.checkDisposability(calculatedFields.getAggregatedEmailDomain());
        calculatedFields.setDisposableEmailDomain(disposabilityCheck.getDisposable());
        calculatedFields.setDisposabilityChecks(disposabilityCheck.getDisposabilityResponses());

        if (purchase.getBankEventsList() != null) {
            calculatedFields.setAuthBankEventResultCodes(purchase.getBankEventsList().stream()
                    .filter(be -> AUTH_BANKEVENT_TYPE.equalsIgnoreCase(be.getType()) || AUTHCANCEL_BANKEVENT_TYPE.equalsIgnoreCase(be.getType()))
                    .map(BankEvent::getBankResponseCode)
                    .collect(Collectors.toList()));
            calculatedFields.setApproveBankEventResultCodes(purchase.getBankEventsList().stream()
                    .filter(be -> APPROVED_BANKEVENT_STATUS.equalsIgnoreCase(be.getStatus()))
                    .map(BankEvent::getBankResponseCode)
                    .collect(Collectors.toList()));
            calculatedFields.setDeclineBankEventResultCodes(purchase.getBankEventsList().stream()
                    .filter(be -> DECLINED_BANKEVENT_STATUS.equalsIgnoreCase(be.getStatus()))
                    .map(BankEvent::getBankResponseCode)
                    .collect(Collectors.toList()));
        }

        if (purchase.getPreviousPurchaseList() == null) {
            purchase.setPreviousPurchaseList(new LinkedList<>());
        }

        //We need to group by original order id get the transaction that has max MerchantLocalDate, remove the rest and flat the grouping map

        Set<PreviousPurchase> lifetimePreviousPurchases = purchase.getPreviousPurchaseList().stream()
                .collect(groupingBy(m -> m.getOriginalOrderId() == null ? m.getPurchaseId() : m.getOriginalOrderId()))
                .entrySet().stream()
                .flatMap(m -> m.getValue().stream().max(Comparator.comparing(PreviousPurchase::getMerchantLocalDate)).stream())
                .collect(Collectors.toSet());

        Set<PreviousPurchase> lastWeekPreviousPurchases = purchase.getPreviousPurchaseList().stream()
                .filter(pp -> pp.getMerchantLocalDate().isAfter(purchase.getMerchantLocalDate().minusWeeks(1)))
                .collect(groupingBy(m -> m.getOriginalOrderId() == null ? m.getPurchaseId() : m.getOriginalOrderId()))
                .entrySet().stream()
                .flatMap(m -> m.getValue().stream().max(Comparator.comparing(PreviousPurchase::getMerchantLocalDate)).stream())
                .collect(Collectors.toSet());

        Set<PreviousPurchase> lastDayPreviousPurchases = lastWeekPreviousPurchases.stream()
                .filter(pp -> pp.getMerchantLocalDate().isAfter(purchase.getMerchantLocalDate().minusDays(1)))
                .collect(groupingBy(m -> m.getOriginalOrderId() == null ? m.getPurchaseId() : m.getOriginalOrderId()))
                .entrySet().stream()
                .flatMap(m -> m.getValue().stream().max(Comparator.comparing(PreviousPurchase::getMerchantLocalDate)).stream())
                .collect(Collectors.toSet());

        Set<PreviousPurchase> lastHourPreviousPurchases = lastDayPreviousPurchases.stream()
                .filter(pp -> pp.getMerchantLocalDate().isAfter(purchase.getMerchantLocalDate().minusHours(1)))
                .collect(groupingBy(m -> m.getOriginalOrderId() == null ? m.getPurchaseId() : m.getOriginalOrderId()))
                .entrySet().stream()
                .flatMap(m -> m.getValue().stream().max(Comparator.comparing(PreviousPurchase::getMerchantLocalDate)).stream())
                .collect(Collectors.toSet());

        calculatedFields.setTransactionCount(new Velocity<>(
                (long) lastHourPreviousPurchases.size(),
                (long) lastDayPreviousPurchases.size(),
                (long) lastWeekPreviousPurchases.size(),
                (long) lifetimePreviousPurchases.size()));

        calculatedFields.setTransactionAmount(new Velocity<>(
                getPurchaseSetSumAmount(lastHourPreviousPurchases),
                getPurchaseSetSumAmount(lastDayPreviousPurchases),
                getPurchaseSetSumAmount(lastWeekPreviousPurchases),
                getPurchaseSetSumAmount(lifetimePreviousPurchases)));


        Set<PreviousPurchase> lastHourRejectedPreviousPurchases = lastHourPreviousPurchases.stream()
                .filter(pp -> REJECTED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastDayRejectedPreviousPurchases = lastDayPreviousPurchases.stream()
                .filter(pp -> REJECTED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastWeekRejectedPreviousPurchases = lastWeekPreviousPurchases.stream()
                .filter(pp -> REJECTED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lifetimeRejectedPreviousPurchases = lifetimePreviousPurchases.stream()
                .filter(pp -> REJECTED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());

        calculatedFields.setRejectedTransactionCount(new Velocity<>(
                (long) lastHourRejectedPreviousPurchases.size(),
                (long) lastDayRejectedPreviousPurchases.size(),
                (long) lastWeekRejectedPreviousPurchases.size(),
                (long) lifetimeRejectedPreviousPurchases.size()));

        calculatedFields.setRejectedTransactionAmount(new Velocity<>(
                getPurchaseSetSumAmount(lastHourRejectedPreviousPurchases),
                getPurchaseSetSumAmount(lastDayRejectedPreviousPurchases),
                getPurchaseSetSumAmount(lastWeekRejectedPreviousPurchases),
                getPurchaseSetSumAmount(lifetimeRejectedPreviousPurchases)));


        Set<PreviousPurchase> lastHourFailedPreviousPurchases = lastHourPreviousPurchases.stream()
                .filter(pp -> FAILED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastDayFailedPreviousPurchases = lastDayPreviousPurchases.stream()
                .filter(pp -> FAILED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastWeekFailedPreviousPurchases = lastWeekPreviousPurchases.stream()
                .filter(pp -> FAILED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lifetimeFailedPreviousPurchases = lifetimePreviousPurchases.stream()
                .filter(pp -> FAILED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());

        calculatedFields.setFailedTransactionCount(new Velocity<>(
                (long) lastHourFailedPreviousPurchases.size(),
                (long) lastDayFailedPreviousPurchases.size(),
                (long) lastWeekFailedPreviousPurchases.size(),
                (long) lifetimeFailedPreviousPurchases.size()));

        calculatedFields.setFailedTransactionAmount(new Velocity<>(
                getPurchaseSetSumAmount(lastHourFailedPreviousPurchases),
                getPurchaseSetSumAmount(lastDayFailedPreviousPurchases),
                getPurchaseSetSumAmount(lastWeekFailedPreviousPurchases),
                getPurchaseSetSumAmount(lifetimeFailedPreviousPurchases)));


        Set<PreviousPurchase> lastHourSuccessfulPreviousPurchases = lastHourPreviousPurchases.stream()
                .filter(pp -> APPROVED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastDaySuccessfulPreviousPurchases = lastDayPreviousPurchases.stream()
                .filter(pp -> APPROVED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lastWeekSuccessfulPreviousPurchases = lastWeekPreviousPurchases.stream()
                .filter(pp -> APPROVED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());
        Set<PreviousPurchase> lifetimeSuccessfulPreviousPurchases = lifetimePreviousPurchases.stream()
                .filter(pp -> APPROVED_TRANSACTION_STATUS.equalsIgnoreCase(pp.getLastMerchantStatus()))
                .collect(Collectors.toSet());

        calculatedFields.setSuccessfulTransactionCount(new Velocity<>(
                (long) lastHourSuccessfulPreviousPurchases.size(),
                (long) lastDaySuccessfulPreviousPurchases.size(),
                (long) lastWeekSuccessfulPreviousPurchases.size(),
                (long) lifetimeSuccessfulPreviousPurchases.size()));

        calculatedFields.setSuccessfulTransactionAmount(new Velocity<>(
                getPurchaseSetSumAmount(lastHourSuccessfulPreviousPurchases),
                getPurchaseSetSumAmount(lastDaySuccessfulPreviousPurchases),
                getPurchaseSetSumAmount(lastWeekSuccessfulPreviousPurchases),
                getPurchaseSetSumAmount(lifetimeSuccessfulPreviousPurchases)));

        calculatedFields.setUniquePaymentInstrumentCount(new Velocity<>(
                getUniquePaymentInstrumentCount(lastHourPreviousPurchases),
                getUniquePaymentInstrumentCount(lastDayPreviousPurchases),
                getUniquePaymentInstrumentCount(lastWeekPreviousPurchases),
                getUniquePaymentInstrumentCount(lifetimePreviousPurchases)));

        if (purchase.getPaymentInstrumentList() == null) {
            purchase.setPaymentInstrumentList(new LinkedList<>());
        }

        Set<String> currentPurchasePaymentInstrumentIds = purchase.getPaymentInstrumentList().stream()
                .map(PaymentInstrument::getPaymentInstrumentId)
                .collect(Collectors.toSet());

        Set<PreviousPurchase> lastHourTransactionWithCurrentPaymentInstrument =
                filterPreviousPurchasesByPIUsage(lastHourPreviousPurchases, currentPurchasePaymentInstrumentIds);
        Set<PreviousPurchase> lastDayTransactionWithCurrentPaymentInstrument =
                filterPreviousPurchasesByPIUsage(lastDayPreviousPurchases, currentPurchasePaymentInstrumentIds);
        Set<PreviousPurchase> lastWeekTransactionWithCurrentPaymentInstrument =
                filterPreviousPurchasesByPIUsage(lastWeekPreviousPurchases, currentPurchasePaymentInstrumentIds);
        Set<PreviousPurchase> lifetimeTransactionWithCurrentPaymentInstrument =
                filterPreviousPurchasesByPIUsage(lifetimePreviousPurchases, currentPurchasePaymentInstrumentIds);

        calculatedFields.setCurrentPaymentInstrumentTransactionCount(new Velocity<>(
                (long) lastHourTransactionWithCurrentPaymentInstrument.size(),
                (long) lastDayTransactionWithCurrentPaymentInstrument.size(),
                (long) lastWeekTransactionWithCurrentPaymentInstrument.size(),
                (long) lifetimeTransactionWithCurrentPaymentInstrument.size()));

        calculatedFields.setCurrentPaymentInstrumentTransactionAmount(new Velocity<>(
                getPurchaseSetSumAmount(lastHourTransactionWithCurrentPaymentInstrument),
                getPurchaseSetSumAmount(lastDayTransactionWithCurrentPaymentInstrument),
                getPurchaseSetSumAmount(lastWeekTransactionWithCurrentPaymentInstrument),
                getPurchaseSetSumAmount(lifetimeTransactionWithCurrentPaymentInstrument)));

        calculatedFields.setUniqueIPCountries(new Velocity<>(
                countUniqueIPCountriesInPreviousPurchases(lastHourPreviousPurchases),
                countUniqueIPCountriesInPreviousPurchases(lastDayPreviousPurchases),
                countUniqueIPCountriesInPreviousPurchases(lastWeekPreviousPurchases),
                countUniqueIPCountriesInPreviousPurchases(lifetimePreviousPurchases)));

        item.getPurchase().setCalculatedFields(calculatedFields);
    }

    private long getUniquePaymentInstrumentCount(final Set<PreviousPurchase> lastHourPreviousPurchases) {
        return lastHourPreviousPurchases.stream()
                .filter(pp -> pp.getPaymentInstrumentList() != null)
                .flatMap(pp -> pp.getPaymentInstrumentList().stream())
                .filter(pi -> pi.getPaymentInstrumentId() != null)
                .map(PaymentInstrument::getPaymentInstrumentId)
                .distinct()
                .count();
    }

    private BigDecimal getPurchaseSetSumAmount(Set<? extends Purchase> purchases) {
        return purchases.stream()
                .map(p -> Objects.requireNonNullElse(p.getTotalAmountInUSD(), BigDecimal.ZERO))
                .reduce(BigDecimal::add)
                .orElse(BigDecimal.ZERO);
    }

    private Set<PreviousPurchase> filterPreviousPurchasesByPIUsage(Set<PreviousPurchase> purchases, Set<String> piIds) {
        return purchases.stream()
                .filter(pp -> pp.getPaymentInstrumentList() != null)
                .filter(pp -> pp.getPaymentInstrumentList().stream()
                        .filter(pi -> pi.getPaymentInstrumentId() != null)
                        .map(PaymentInstrument::getPaymentInstrumentId)
                        .anyMatch(piIds::contains))
                .collect(Collectors.toSet());
    }

    private long countUniqueIPCountriesInPreviousPurchases(Set<PreviousPurchase> purchases) {
        return purchases.stream()
                .filter(pp -> pp.getDeviceContext() != null)
                .map(PreviousPurchase::getDeviceContext)
                .filter(dc -> dc.getIpCountry() != null)
                .map(DeviceContext::getIpCountry)
                .distinct()
                .count();
    }


}
