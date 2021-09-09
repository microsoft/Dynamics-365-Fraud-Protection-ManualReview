// Copyright (c) Microsoft Corporation.
// Licensed under the MIT license.

package com.griddynamics.msd365fp.manualreview.analytics.service;

import com.azure.cosmos.implementation.PreconditionFailedException;
import com.griddynamics.msd365fp.manualreview.analytics.config.properties.ApplicationProperties;
import com.griddynamics.msd365fp.manualreview.analytics.model.persistence.Task;
import com.griddynamics.msd365fp.manualreview.analytics.repository.TaskRepository;
import com.griddynamics.msd365fp.manualreview.analytics.service.dashboard.CollectedInfoService;
import com.griddynamics.msd365fp.manualreview.model.TaskStatus;
import com.griddynamics.msd365fp.manualreview.model.exception.IncorrectConfigurationException;
import com.azure.spring.data.cosmos.exception.CosmosAccessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.ELDEST_APPLICATION_DATE;
import static com.griddynamics.msd365fp.manualreview.analytics.config.Constants.INCORRECT_CONFIG_STATUS;
import static com.griddynamics.msd365fp.manualreview.analytics.config.ScheduledJobsConfig.*;
import static com.griddynamics.msd365fp.manualreview.model.TaskStatus.READY;
import static com.griddynamics.msd365fp.manualreview.model.TaskStatus.RUNNING;

/**
 * Service for working with {@link Task}s.
 * It creates default {@link Task} documents in the database.
 * It initializes scheduled tasks on application startup.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final StreamService streamService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TaskRepository taskRepository;
    private final ResolutionService resolutionService;
    private final AlertService alertService;
    private final CollectedInfoService collectedInfoService;
    private final ApplicationProperties applicationProperties;

    private Map<String, TaskExecution<Object, Exception>> taskExecutions;


    @PostConstruct
    private void initializeTasks() {
        this.taskExecutions = Map.of(
                ALERT_TEMPLATE_RECONCILIATION_TASK_NAME, task -> alertService.reconcileAlertTemplates(),
                RESOLUTION_RETRY_TASK_NAME, task -> resolutionService.sendResolutionsToDFP(OffsetDateTime.now()),
                COLLECT_ANALYST_INFO_TASK_NAME, task -> collectedInfoService.collectAnalystInfo(),
                SEND_ALERTS_TASK_NAME, task -> alertService.sendAlerts(),
                PRIM_HEALTH_ANALYSIS_TASK_NAME, this::healthAnalysis,
                SEC_HEALTH_ANALYSIS_TASK_NAME, this::healthAnalysis
        );

        Optional<Map.Entry<String, ApplicationProperties.TaskProperties>> incorrectTimingTask = applicationProperties.getTasks().entrySet().stream()
                .filter(entry -> entry.getValue().getDelay() == null ||
                        (entry.getValue().getTimeout() != null &&
                                entry.getValue().getTimeout().compareTo(entry.getValue().getDelay()) < 0))
                .findFirst();
        if (incorrectTimingTask.isPresent()) {
            log.error("Task [{}] has incorrect timing configuration: {}",
                    incorrectTimingTask.get().getKey(),
                    incorrectTimingTask.get().getValue());
            System.exit(INCORRECT_CONFIG_STATUS);
        }
        if (applicationProperties.getTaskWarningTimeoutMultiplier() < 1 ||
                applicationProperties.getTaskWarningTimeoutMultiplier() > applicationProperties.getTaskResetTimeoutMultiplier()) {
            log.error("Incorrect timeout multiplier configuration: [{},{}]",
                    applicationProperties.getTaskWarningTimeoutMultiplier(),
                    applicationProperties.getTaskResetTimeoutMultiplier());
            System.exit(INCORRECT_CONFIG_STATUS);
        }
    }


    public List<Task> getAllTasks() {
        List<Task> result = new ArrayList<>();
        taskRepository.findAll().forEach(result::add);
        return result;
    }

    public void setAllTasksReady() {
        List<Task> allTasks = getAllTasks();
        allTasks.forEach(task -> {
            if (!READY.equals(task.getStatus())) {
                task.setStatus(READY);
                task.setLastFailedRunMessage("Restored manually");
                taskRepository.save(task);
            }
        });
    }

    public boolean forceTaskRunByName(String taskName) throws IncorrectConfigurationException {
        ApplicationProperties.TaskProperties taskProperties = applicationProperties.getTasks().get(taskName);
        if (taskProperties == null) {
            throw new IncorrectConfigurationException("Task isn't configured");
        }
        Task task = taskRepository.findById(taskName).orElseThrow(IncorrectConfigurationException::new);

        return executeTask(task);
    }

    /**
     * Task runner.
     * The common scheduled method that is ran very frequently
     * and checks for {@link Task}s that are available for execution.
     * If there are such then they are executed.
     * If there are no distributed lock in the DB for particular task
     * then it will be created but task will be executed only in the next iteration.
     * If task is stuck then it'll be reported and later restored after particular
     * amount of iterations.
     */
    @Scheduled(fixedRate = TASK_RUNNER_RATE_MS)
    protected void runReadyTasks() {
        Map<String, Task> storedTasks = StreamSupport.stream(taskRepository.findAll().spliterator(), false)
                .collect(Collectors.toMap(Task::getId, task -> task));

        applicationProperties.getTasks().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .parallel()
                .forEach(taskPropertiesEntry -> {
                    String taskName = taskPropertiesEntry.getKey();
                    ApplicationProperties.TaskProperties taskProperties = taskPropertiesEntry.getValue();
                    Task task = storedTasks.get(taskName);
                    boolean taskLaunched = false;

                    // Execute task
                    if (task != null && isTaskReadyForExecutionNow(task, taskProperties)) {
                        taskLaunched = executeTask(task);
                    }

                    // Create task if absent
                    if (task == null) {
                        createStoredTask(taskName, taskPropertiesEntry.getValue());
                    }

                    // Restore task if it's stuck
                    if (task != null && !taskLaunched) {
                        processTaskFreezes(task, taskProperties);
                    }
                });
    }

    private boolean isTaskReadyForExecutionNow(Task task, ApplicationProperties.TaskProperties taskProperties) {
        return READY.equals(task.getStatus()) &&
                (task.getPreviousRun() == null ||
                        task.getPreviousRun().plus(taskProperties.getDelay()).isBefore(OffsetDateTime.now()));
    }

    @SuppressWarnings("java:S1854")
    private void processTaskFreezes(Task task, ApplicationProperties.TaskProperties taskProperties) {
        Duration timeout = Objects.requireNonNullElse(taskProperties.getTimeout(), taskProperties.getDelay());
        OffsetDateTime previousSuccessfulRun = Objects.requireNonNullElse(task.getPreviousSuccessfulRun(), ELDEST_APPLICATION_DATE);
        OffsetDateTime previousRun = Objects.requireNonNullElse(task.getPreviousRun(), ELDEST_APPLICATION_DATE);
        OffsetDateTime currentRun = Objects.requireNonNullElse(task.getCurrentRun(), ELDEST_APPLICATION_DATE);
        OffsetDateTime now = OffsetDateTime.now();

        Duration runWithoutSuccess = Duration.between(previousSuccessfulRun, now);
        Duration acceptableDelayWithoutSuccessfulRuns = Duration.ofSeconds(
                (long) (timeout.toSeconds() * applicationProperties.getTaskSuccessfulRunsTimeoutMultiplier()));
        if (previousSuccessfulRun.isBefore(previousRun) &&
                runWithoutSuccess.compareTo(acceptableDelayWithoutSuccessfulRuns) > 0) {
            log.warn("Background task [{}] issue. No successful executions during [{}] minutes. Last Fail reason: [{}].",
                    task.getId(), runWithoutSuccess.toMinutes(), task.getLastFailedRunMessage());
        }
        if (!READY.equals(task.getStatus())) {
            Duration currentRunDuration = Duration.between(currentRun, now);
            Duration acceptableDelayBeforeWarning = Duration.ofSeconds(
                    (long) (timeout.toSeconds() * applicationProperties.getTaskWarningTimeoutMultiplier()));
            Duration acceptableDelayBeforeReset = Duration.ofSeconds(
                    (long) (timeout.toSeconds() * applicationProperties.getTaskResetTimeoutMultiplier()));
            if (currentRunDuration.compareTo(acceptableDelayBeforeWarning) > 0) {
                log.warn("Background task [{}] issue. Idle for too long. Last execution was [{}] minutes ago with status message: [{}].",
                        task.getId(), currentRunDuration.toMinutes(), task.getLastFailedRunMessage());
            }
            if (currentRunDuration.compareTo(acceptableDelayBeforeReset) > 0) {
                try {
                    log.info("Start [{}] task restore", task.getId());
                    task.setStatus(READY);
                    task.setLastFailedRunMessage("Restored after long downtime");
                    taskRepository.save(task);
                    log.info("Task [{}] has been restored", task.getId());
                } catch (CosmosAccessException e) {
                    log.warn("Task [{}] recovering ended with a conflict: {}", task.getId(), e.getMessage());
                }
            }
        }
    }

    private void createStoredTask(final String taskName, final ApplicationProperties.TaskProperties properties) {
        try {
            log.info("Trying to create task [{}]", taskName);
            taskRepository.save(Task.builder()
                    .id(taskName)
                    ._etag(taskName)
                    .previousRun(ELDEST_APPLICATION_DATE)
                    .status(READY)
                    .build());
            log.info("Task [{}] has been initialized successfully.", taskName);
        } catch (CosmosAccessException e) {
            log.warn("Task [{}] creation ended with a conflict: {}", taskName, e.getMessage());
        }
    }

    /**
     * Scale-safe task executor.
     * <p>
     * Wraps task execution with the common logic of condition checking,
     * acquiring task lock, releasing it, and handling exceptional path.
     * </p>
     * Before task execution the conditions are checked:
     * - task should be in a {@link TaskStatus#READY} status
     * - if there is a precondition for task then it should return {@code true}
     * </p>
     * Scheduled task uses provided {@link Task} that should be retrieved
     * from the database. The executor sets {@link Task#getStatus()} into
     * {@link TaskStatus#RUNNING} state, otherwise execution ends.
     * </p>
     * Afterwards {@link Function} task execution starts in non-blocking way.
     * </p>
     * If service's method worked as expected {@link Task#getStatus()} is set
     * to {@link TaskStatus#READY} status after execution. Finally {@link Task}
     * have to be saved to the database with updated {@link Task#getStatus()}.
     * </p>
     * In case task execution failed with an exception,
     * {@link Task#getLastFailedRunMessage()} is set to
     * {@link Exception#getMessage()} and new state saved into the database.
     *
     * @param task which should represent a lock object
     *             from shared database
     * @return true if task has been sent to execution on the current instance
     */
    @SuppressWarnings("java:S2326")
    private <T, E extends Exception> boolean executeTask(Task task) {
        // check possibility to execute
        if (!READY.equals(task.getStatus())) {
            return false;
        }

        // acquire a lock
        OffsetDateTime startTime = OffsetDateTime.now();
        task.setStatus(RUNNING);
        task.setInstanceId(applicationProperties.getInstanceId());
        task.setCurrentRun(startTime);
        if (task.getPreviousRun() == null) {
            task.setPreviousRun(ELDEST_APPLICATION_DATE);
        }
        if (task.getPreviousSuccessfulRun() == null) {
            task.setPreviousSuccessfulRun(ELDEST_APPLICATION_DATE);
        }
        Task runningTask;
        try {
            runningTask = taskRepository.save(task);
        } catch (CosmosAccessException e) {
            if (e.getCause() instanceof PreconditionFailedException) {
                log.debug("Could not acquire lock because task [{}] was modified by another source.", task.getId());
            } else {
                log.warn("Could not acquire lock for the task [{}].", task.getId(), e);
            }
            return false;
        }

        // get task object for usage in async execution
        log.info("Task [{}] started its execution.", runningTask.getId());

        // launch execution
        ApplicationProperties.TaskProperties taskProperties =
                applicationProperties.getTasks().get(task.getId());
        TaskExecution<Object, Exception> taskExecution = taskExecutions.get(task.getId());
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return Optional.ofNullable(taskExecution.apply(runningTask));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, taskExecutor)
                .orTimeout(
                        Objects.requireNonNullElse(taskProperties.getTimeout(), taskProperties.getDelay()).toMillis(),
                        TimeUnit.MILLISECONDS)
                .whenComplete((result, exception) -> {
                    Duration duration = Duration.between(startTime, OffsetDateTime.now());
                    runningTask.setStatus(READY);
                    runningTask.setPreviousRun(startTime);
                    if (exception != null) {
                        log.warn("Task [{}] finished its execution with an exception in [{}].",
                                runningTask.getId(), duration.toString());
                        log.warn("Task [{}] exception", runningTask.getId(), exception);
                        runningTask.setLastFailedRunMessage(exception.getMessage());
                        taskRepository.save(runningTask);
                    } else {
                        runningTask.setPreviousSuccessfulRun(startTime);
                        runningTask.setPreviousSuccessfulExecutionTime(duration);

                        if (result.isEmpty()) {
                            log.info("Task [{}] finished its execution with empty result in [{}].",
                                    runningTask.getId(), duration);
                        } else {
                            log.info("Task [{}] finished its execution successfully in [{}]. Result: [{}]",
                                    runningTask.getId(), duration, result.get());
                        }
                    }
                    taskRepository.save(runningTask);
                });
        return true;
    }


    private boolean healthAnalysis(Task task) {
        boolean resourcesAreHealthy;
        try {
            resourcesAreHealthy = streamService.checkStreamingHealth();
        } catch (Exception e) {
            log.error("Exception during health-check.", e);
            resourcesAreHealthy = false;
        }
        if (!resourcesAreHealthy) {
            log.warn("One of health checks has failed.");
        }
        return resourcesAreHealthy;
    }


    @FunctionalInterface
    public interface TaskExecution<T, E extends Exception> {
        T apply(Task task) throws E;
    }
}
