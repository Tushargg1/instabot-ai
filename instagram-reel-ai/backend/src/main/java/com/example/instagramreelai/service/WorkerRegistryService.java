package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.WorkerInstanceResponse;
import com.example.instagramreelai.entity.WorkerInstance;
import com.example.instagramreelai.repository.WorkerInstanceRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WorkerRegistryService {
    private static final String APPLICATION_NAME = "instagram-reel-ai";
    private final WorkerInstanceRepository repository;
    private final WorkerRegistryService self;
    private final String instanceId;
    private final long staleAfterMillis;

    public WorkerRegistryService(
            WorkerInstanceRepository repository,
            @Lazy WorkerRegistryService self,
            @Value("${app.worker.instance-id:}") String configuredInstanceId,
            @Value("${app.worker.stale-after-ms:30000}") long staleAfterMillis) {
        this.repository = repository;
        this.self = self;
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? "worker-" + UUID.randomUUID() : configuredInstanceId.trim();
        this.staleAfterMillis = Math.max(staleAfterMillis, 1L);
    }

    public String getInstanceId() {
        return instanceId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerAtStartup() {
        ensureRegistered();
    }

    @Scheduled(fixedDelayString = "${app.worker.heartbeat-interval-ms:10000}")
    public void heartbeat() {
        ensureRegistered();
    }

    /**
     * Publishes this worker exactly once, then keeps it fresh. Startup registration and the
     * scheduled heartbeat both land here and can run concurrently, so the unique instance_id
     * constraint is treated as "another writer already published us" rather than a failure.
     * The retry sits outside the failed transaction because a constraint violation dooms it.
     */
    private void ensureRegistered() {
        if (self.touchHeartbeat() > 0) {
            return;
        }
        try {
            self.register();
        } catch (DataIntegrityViolationException concurrentRegistration) {
            log.debug("Worker {} was registered concurrently; recording a heartbeat instead",
                    instanceId);
            self.touchHeartbeat();
        }
    }

    @Transactional
    public int touchHeartbeat() {
        return repository.heartbeat(instanceId, Instant.now());
    }

    @Transactional
    public void register() {
        Instant now = Instant.now();
        WorkerInstance worker = repository.findByInstanceId(instanceId)
                .orElseGet(WorkerInstance::new);
        worker.setInstanceId(instanceId);
        worker.setHostName(resolveHostName());
        worker.setProcessId(ProcessHandle.current().pid());
        worker.setApplicationName(APPLICATION_NAME);
        worker.setStartedAt(runtimeStart());
        worker.setRegisteredAt(now);
        worker.setLastHeartbeatAt(now);
        worker.setStoppedAt(null);
        repository.saveAndFlush(worker);
    }

    @Transactional(readOnly = true)
    public List<WorkerInstanceResponse> listActive() {
        return repository.findActive(cutoff()).stream()
                .map(worker -> response(worker, "ACTIVE"))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkerInstanceResponse> listStale() {
        return repository.findStale(cutoff()).stream()
                .map(worker -> response(worker, worker.getStoppedAt() == null ? "STALE" : "STOPPED"))
                .toList();
    }

    @PreDestroy
    public void markStopped() {
        repository.findByInstanceId(instanceId).ifPresent(worker -> {
            worker.setStoppedAt(Instant.now());
            repository.save(worker);
        });
    }

    private Instant cutoff() {
        return Instant.now().minusMillis(staleAfterMillis);
    }

    private WorkerInstanceResponse response(WorkerInstance worker, String state) {
        return new WorkerInstanceResponse(worker.getId(), worker.getInstanceId(),
                worker.getHostName(), worker.getProcessId(), worker.getApplicationName(), state,
                worker.getStartedAt(), worker.getRegisteredAt(), worker.getLastHeartbeatAt(),
                worker.getStoppedAt());
    }

    private Instant runtimeStart() {
        return Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
    }

    private String resolveHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
            return "unknown";
        }
    }
}
