// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.queues.service;

import com.griddynamics.msd365fp.manualreview.model.ItemLock;
import com.griddynamics.msd365fp.manualreview.model.Label;
import com.griddynamics.msd365fp.manualreview.model.PageableCollection;
import com.griddynamics.msd365fp.manualreview.model.event.type.LockActionType;
import com.griddynamics.msd365fp.manualreview.queues.config.ModelMapperConfig;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueSortSettings;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueView;
import com.griddynamics.msd365fp.manualreview.queues.model.QueueViewType;
import com.griddynamics.msd365fp.manualreview.queues.model.dto.LabelDTO;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Item;
import com.griddynamics.msd365fp.manualreview.queues.model.persistence.Queue;
import com.griddynamics.msd365fp.manualreview.queues.util.QueueViewUtility;
import com.azure.spring.autoconfigure.aad.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_PRINCIPAL_ID_CLAIM;
import static com.griddynamics.msd365fp.manualreview.dfpauth.config.Constants.AUTH_TOKEN_SUB_CLAIM;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicItemServiceTest {

    private static final int DEFAULT_TTL = 5184000;
    private static final OffsetDateTime ITEM_IMPORT_DATE_TIME = OffsetDateTime.parse("2007-12-03T10:15:30+01:00");
    private static final OffsetDateTime ITEM_ENRICH_DATE_TIME = ITEM_IMPORT_DATE_TIME.plusMinutes(5);
    private static final OffsetDateTime ITEM_LOCK_DATE_TIME = ITEM_ENRICH_DATE_TIME.plusMinutes(5);
    private static final OffsetDateTime QUEUE_CREATE_DATE_TIME = OffsetDateTime.parse("2007-12-03T10:15:30+01:00");
    private static final String TEST_ITEM_ID = "00000000-0000-0000-0000-000000000000";
    private static final String TEST_QUEUE_ID = "11111111-1111-1111-1111-111111111111";
    private static final String TEST_QUEUE_VIEW_ID =
            "11111111-1111-1111-1111-111111111111-" + QueueViewType.REGULAR.name();
    private static final String TEST_ESC_QUEUE_VIEW_ID =
            "11111111-1111-1111-1111-111111111111-" + QueueViewType.ESCALATION.name();
    private static final String TEST_REVIEWER_ID = "22222222-2222-2222-2222-222222222222";

    private PublicItemService itemService;

    @Mock
    private StreamService streamService;
    @Mock
    private PublicItemClient publicItemClient;
    @Mock
    private PublicQueueClient publicQueueClient;
    @Mock
    private DFPExplorerService dfpExplorerService;
    @Mock
    private UserPrincipal userPrincipal;

    @Captor
    private ArgumentCaptor<LockActionType> lockActionTypeCaptor;
    @Captor
    private ArgumentCaptor<Item> itemCaptor;
    @Captor
    private ArgumentCaptor<ItemLock> itemLockCaptor;
    @Captor
    private ArgumentCaptor<Queue> queueCaptor;
    @Captor
    private ArgumentCaptor<Set<String>> stringSetCaptor;

    @BeforeEach
    public void setUp() {
        ModelMapper modelMapper = new ModelMapperConfig().modelMapper();
        itemService = new PublicItemService(streamService, publicItemClient, publicQueueClient, modelMapper, dfpExplorerService);
        itemService.setDefaultTtl(Duration.ofSeconds(DEFAULT_TTL));
    }


    /**
     * {@link PublicItemService#lockQueueItem} should send event when the lock doesn't exist in the database and new one is
     * created.
     */
    @Test
    void lockTopQueueItemSendsEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .build();
        Queue queue = Queue.builder()
                .active(true)
                .id(TEST_QUEUE_ID)
                .created(QUEUE_CREATE_DATE_TIME)
                .name("Common Queue")
                .residual(false)
                .sorting(new QueueSortSettings())
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .build();
        QueueViewUtility.addViewToQueue(queue, QueueViewType.REGULAR);
        QueueView queueView = QueueViewUtility.extractQueueView(queue, QueueViewType.REGULAR);

        when(publicQueueClient.getActiveQueueView(eq(TEST_QUEUE_VIEW_ID)))
                .thenReturn(queueView);
        when(publicItemClient.getLockedItemPageableList(eq(TEST_REVIEWER_ID), eq(queueView), anyInt(), isNull()))
                .thenReturn(new PageableCollection<>(Collections.emptyList(), null));
        when(publicItemClient.getFirstFreeActiveItem(eq(queueView)))
                .thenReturn(item);
        doAnswer(i -> {
            item.getLock().lock(TEST_QUEUE_ID, TEST_QUEUE_VIEW_ID, TEST_REVIEWER_ID);
            return null;
        }).when(publicItemClient).lockItem(eq(queueView), eq(item));
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_PRINCIPAL_ID_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID);
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_SUB_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID + "-assignment");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(userPrincipal, null));

        itemService.lockFirstFreeQueueItem(TEST_QUEUE_VIEW_ID);

        verify(streamService, times(1))
                .sendItemLockEvent(itemCaptor.capture(), itemLockCaptor.capture(), lockActionTypeCaptor.capture());

        assertTrue(ITEM_ENRICH_DATE_TIME.isBefore(itemCaptor.getValue().getLock().getLocked()));
        assertSame(LockActionType.SETUP, lockActionTypeCaptor.getValue());
    }

    /**
     * {@link PublicItemService#lockQueueItem} should not send event when the lock already exists in the database.
     */
    @Test
    void lockTopQueueItemDoesNotSendEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .build();
        Queue queue = Queue.builder()
                .active(true)
                .id(TEST_QUEUE_ID)
                .created(QUEUE_CREATE_DATE_TIME)
                .name("Common Queue")
                .residual(false)
                .sorting(new QueueSortSettings())
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .build();
        QueueViewUtility.addViewToQueue(queue, QueueViewType.REGULAR);
        QueueView queueView = QueueViewUtility.extractQueueView(queue, QueueViewType.REGULAR);

        when(publicQueueClient.getActiveQueueView(eq(TEST_QUEUE_VIEW_ID)))
                .thenReturn(queueView);
        when(publicItemClient.getLockedItemPageableList(eq(TEST_REVIEWER_ID), eq(queueView), anyInt(), isNull()))
                .thenReturn(new PageableCollection<>(List.of(item), null));
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_PRINCIPAL_ID_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID);
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_SUB_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID + "-assignment");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(userPrincipal, null));

        itemService.lockFirstFreeQueueItem(TEST_QUEUE_VIEW_ID);

        verifyNoInteractions(streamService);
    }

    /**
     * {@link PublicItemService#unlockItem(String, String)} should create an event with {@link LockActionType#MANUAL_RELEASE}.
     */
    @Test
    void unlockItemManuallySendsEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .lock(ItemLock.builder()
                        .locked(ITEM_LOCK_DATE_TIME)
                        .ownerId(TEST_REVIEWER_ID)
                        .queueViewId(TEST_QUEUE_VIEW_ID)
                        .queueId(TEST_QUEUE_ID)
                        .build())
                .build();
        when(publicItemClient.getItem(eq(TEST_ITEM_ID), isNull(), eq(true)))
                .thenReturn(item);

        itemService.unlockItem(TEST_ITEM_ID, null);

        verify(streamService, times(1))
                .sendItemLockEvent(itemCaptor.capture(), itemLockCaptor.capture(), lockActionTypeCaptor.capture());

        assertEquals(new ItemLock(), itemCaptor.getValue().getLock(), "Lock should have all fields nullified.");
        assertEquals(ItemLock.builder()
                .locked(ITEM_LOCK_DATE_TIME)
                .ownerId(TEST_REVIEWER_ID)
                .queueViewId(TEST_QUEUE_VIEW_ID)
                .queueId(TEST_QUEUE_ID)
                .build(), itemLockCaptor.getValue());
        assertEquals(LockActionType.MANUAL_RELEASE, lockActionTypeCaptor.getValue());
    }

    /**
     * {@link PublicItemService#labelItem(String, String, LabelDTO)} should create an event with
     * {@link LockActionType#LABEL_APPLIED_RELEASE}.
     */
    @Test
    @Disabled
    void unlockItemByLabelingSendsEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .lock(ItemLock.builder()
                        .locked(ITEM_LOCK_DATE_TIME)
                        .ownerId(TEST_REVIEWER_ID)
                        .queueViewId(TEST_QUEUE_VIEW_ID)
                        .queueId(TEST_QUEUE_ID)
                        .build())
                .build();
        LabelDTO labelDto = new LabelDTO(Label.GOOD);
        Queue queue = Queue.builder()
                .active(true)
                .id(TEST_QUEUE_ID)
                .created(QUEUE_CREATE_DATE_TIME)
                .allowedLabels(Collections.singleton(Label.GOOD))
                .name("Common Queue")
                .residual(false)
                .sorting(new QueueSortSettings())
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .build();
        QueueViewUtility.addViewToQueue(queue, QueueViewType.REGULAR);
        QueueView queueView = QueueViewUtility.extractQueueView(queue, QueueViewType.REGULAR);

        when(publicQueueClient.getActiveQueueView(eq(TEST_QUEUE_VIEW_ID)))
                .thenReturn(queueView);
        when(publicItemClient.getItem(eq(TEST_ITEM_ID), isNull(), true))
                .thenReturn(item);

        itemService.labelItem(TEST_ITEM_ID, null, labelDto);

        verify(streamService, times(1))
                .sendItemLockEvent(itemCaptor.capture(), itemLockCaptor.capture(), lockActionTypeCaptor.capture());

        assertEquals(new ItemLock(), itemCaptor.getValue().getLock(), "Lock should have all fields nullified.");
        assertEquals(ItemLock.builder()
                .locked(ITEM_LOCK_DATE_TIME)
                .ownerId(TEST_REVIEWER_ID)
                .queueViewId(TEST_QUEUE_VIEW_ID)
                .queueId(TEST_QUEUE_ID)
                .build(), itemLockCaptor.getValue());
        assertEquals(LockActionType.LABEL_APPLIED_RELEASE, lockActionTypeCaptor.getValue());
        assertNotNull(item.getLabel());
        assertEquals(labelDto.getLabel(), item.getLabel().getValue());
    }

    /**
     * {@link PublicItemService#labelItem(String, String, LabelDTO)} should create an event via
     * {@link StreamService#sendItemAssignmentEvent(Item, Set)} method.
     */
    @Test
    @Disabled
    void labelingItemWithResolutionLabelSendsEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .lock(ItemLock.builder()
                        .locked(ITEM_LOCK_DATE_TIME)
                        .ownerId(TEST_REVIEWER_ID)
                        .queueViewId(TEST_QUEUE_VIEW_ID)
                        .queueId(TEST_QUEUE_ID)
                        .build())
                .build();
        LabelDTO labelDto = new LabelDTO(Label.GOOD);
        Queue queue = Queue.builder()
                .active(true)
                .id(TEST_QUEUE_ID)
                .created(QUEUE_CREATE_DATE_TIME)
                .allowedLabels(Collections.singleton(Label.GOOD))
                .name("Common Queue")
                .residual(false)
                .sorting(new QueueSortSettings())
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .build();
        QueueViewUtility.addViewToQueue(queue, QueueViewType.REGULAR);
        QueueView queueView = QueueViewUtility.extractQueueView(queue, QueueViewType.REGULAR);

        when(publicQueueClient.getActiveQueueView(eq(TEST_QUEUE_VIEW_ID)))
                .thenReturn(queueView);
        when(publicItemClient.getItem(eq(TEST_ITEM_ID), isNull(), true))
                .thenReturn(item);
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_PRINCIPAL_ID_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID);
        when(userPrincipal.getClaim(eq(AUTH_TOKEN_SUB_CLAIM)))
                .thenReturn(TEST_REVIEWER_ID + "-assignment");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(userPrincipal, null));

        itemService.labelItem(TEST_ITEM_ID, null, labelDto);

        ItemLock expectedLock = ItemLock.builder()
                .locked(ITEM_LOCK_DATE_TIME)
                .ownerId(TEST_REVIEWER_ID)
                .queueViewId(TEST_QUEUE_VIEW_ID)
                .queueId(TEST_QUEUE_ID)
                .build();
        verify(streamService, times(1))
                .sendItemLockEvent(any(Item.class), eq(expectedLock), eq(LockActionType.LABEL_APPLIED_RELEASE));
        verify(streamService, times(1))
                .sendItemAssignmentEvent(itemCaptor.capture(), stringSetCaptor.capture());

        assertNotNull(itemCaptor.getValue());
        assertNotNull(queueCaptor.getAllValues().get(0));
        assertNull(queueCaptor.getAllValues().get(1));
        assertTrue(itemCaptor.getValue().getLabel().getValue().isFormsResolution());
    }

    /**
     * {@link PublicItemService#labelItem(String, String, LabelDTO)} should create an event via
     * {@link StreamService#sendItemAssignmentEvent(Item, Set)} method.
     */
    //TODO: enable after escalation flow rework
    @Disabled("until escalation flow total rework")
    @Test
    void labelingItemWithEscalateLabelSendsEvent() throws Exception {
        Item item = Item.builder()
                .active(true)
                .id(TEST_ITEM_ID)
                .queueIds(Collections.singleton(TEST_QUEUE_ID))
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .imported(ITEM_IMPORT_DATE_TIME)
                .enriched(ITEM_ENRICH_DATE_TIME)
                .updated(ITEM_ENRICH_DATE_TIME)
                .lock(ItemLock.builder()
                        .locked(ITEM_LOCK_DATE_TIME)
                        .ownerId(TEST_REVIEWER_ID)
                        .queueViewId(TEST_QUEUE_VIEW_ID)
                        .queueId(TEST_QUEUE_ID)
                        .build())
                .build();
        LabelDTO labelDto = new LabelDTO(Label.ESCALATE);
        Queue queue = Queue.builder()
                .active(true)
                .id(TEST_QUEUE_ID)
                .created(QUEUE_CREATE_DATE_TIME)
                .allowedLabels(Collections.singleton(Label.ESCALATE))
                .name("Common Queue")
                .residual(false)
                .sorting(new QueueSortSettings())
                .reviewers(new HashSet<>(Collections.singleton(TEST_REVIEWER_ID)))
                .build();
        QueueViewUtility.addViewToQueue(queue, QueueViewType.REGULAR);
        QueueView queueView = QueueViewUtility.extractQueueView(queue, QueueViewType.REGULAR);
        QueueViewUtility.addViewToQueue(queue, QueueViewType.ESCALATION);
        QueueView escalationQueueView = QueueViewUtility.extractQueueView(queue, QueueViewType.ESCALATION);

        when(publicQueueClient.getActiveQueueView(eq(TEST_QUEUE_VIEW_ID)))
                .thenReturn(queueView);
        when(publicQueueClient.getActiveQueueView(eq(TEST_ESC_QUEUE_VIEW_ID)))
                .thenReturn(escalationQueueView);
        when(publicItemClient.getItem(eq(TEST_ITEM_ID), isNull(), true))
                .thenReturn(item);

        itemService.labelItem(TEST_ITEM_ID, null, labelDto);

        ItemLock expectedLock = ItemLock.builder()
                .locked(ITEM_LOCK_DATE_TIME)
                .ownerId(TEST_REVIEWER_ID)
                .queueViewId(TEST_QUEUE_VIEW_ID)
                .queueId(TEST_QUEUE_ID)
                .build();
        verify(streamService, times(1))
                .sendItemLockEvent(itemCaptor.capture(), eq(expectedLock), eq(LockActionType.LABEL_APPLIED_RELEASE));
//        verify(streamService, times(1))
//                .sendItemAssignmentEvent(itemCaptor.capture(), queueCaptor.capture(), queueCaptor.capture());

        assertNotNull(itemCaptor.getValue());
//        assertNotNull(queueCaptor.getAllValues().get(0));
//        assertNotNull(queueCaptor.getAllValues().get(1));
        assertFalse(itemCaptor.getValue().getLabel().getValue().isFormsResolution());
//        assertEquals(firstQueueId, queueCaptor.getAllValues().get(0).getId());
//        assertEquals(secondQueueId, queueCaptor.getAllValues().get(1).getId());
    }


}
