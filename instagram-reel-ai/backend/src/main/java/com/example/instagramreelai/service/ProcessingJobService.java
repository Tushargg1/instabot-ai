package com.example.instagramreelai.service;

import com.example.instagramreelai.dto.ProcessingJobDto;
import com.example.instagramreelai.entity.ProcessingJob;
import com.example.instagramreelai.job.JobStatus;
import com.example.instagramreelai.repository.ProcessingJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProcessingJobService {

    private final ProcessingJobRepository jobRepository;

    public Page<ProcessingJobDto> listJobs(Pageable pageable, String status) {
        Page<ProcessingJob> jobs;
        if (status != null && !status.isBlank()) {
            jobs = jobRepository.findByStatus(status, pageable);
        } else {
            jobs = jobRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return jobs.map(this::toDto);
    }

    public Optional<ProcessingJobDto> getJobById(UUID id) {
        return jobRepository.findById(id).map(this::toDto);
    }

    @Transactional
    public Optional<ProcessingJobDto> retryJob(UUID id) {
        return jobRepository.findById(id).map(job -> {
            job.setStatus(JobStatus.QUEUED);
            job.setNextRetryAt(null);
            job.setLastErrorCode(null);
            job.setLastErrorMessage(null);
            return toDto(jobRepository.save(job));
        });
    }

    private ProcessingJobDto toDto(ProcessingJob job) {
        return ProcessingJobDto.builder()
                .id(job.getId())
                .reelId(job.getReel().getId())
                .jobType(job.getJobType())
                .status(job.getStatus())
                .stage(job.getStage())
                .attempts(job.getAttempts())
                .maxAttempts(job.getMaxAttempts())
                .lastErrorCode(job.getLastErrorCode())
                .lastErrorMessage(job.getLastErrorMessage())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .build();
    }
}
