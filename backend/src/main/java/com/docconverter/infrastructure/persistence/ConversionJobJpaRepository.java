package com.docconverter.infrastructure.persistence;

import com.docconverter.domain.model.ConversionJob;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversionJobJpaRepository extends JpaRepository<ConversionJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ConversionJob job where job.id = :id")
    Optional<ConversionJob> findByIdForUpdate(@Param("id") UUID id);

    @Query(
            value = """
                    select *
                    from conversion_jobs
                    where status = 'CREATED'
                      and next_attempt_at <= :startedAt
                      and expires_at > :startedAt
                    order by created_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<ConversionJob> findNextJobsForProcessing(
            @Param("limit") int limit,
            @Param("startedAt") Instant startedAt
    );

    @Query(
            value = """
                    select job.id
                    from conversion_jobs job
                    where job.status = 'PROCESSING'
                      and not exists (
                          select 1
                          from conversion_attempts fresh_attempt
                          where fresh_attempt.job_id = job.id
                            and fresh_attempt.status = 'STARTED'
                            and fresh_attempt.started_at > :staleBefore
                      )
                      and (
                          exists (
                              select 1
                              from conversion_attempts stale_attempt
                              where stale_attempt.job_id = job.id
                                and stale_attempt.status = 'STARTED'
                                and stale_attempt.started_at <= :staleBefore
                          )
                          or (
                              not exists (
                                  select 1
                                  from conversion_attempts active_attempt
                                  where active_attempt.job_id = job.id
                                    and active_attempt.status = 'STARTED'
                              )
                              and (
                                  job.processing_started_at is null
                                  or job.processing_started_at <= :staleBefore
                              )
                          )
                      )
                    order by job.processing_started_at asc nulls first
                    limit :limit
                    """,
            nativeQuery = true
    )
    List<UUID> findStalledProcessingIds(
            @Param("staleBefore") Instant staleBefore,
            @Param("limit") int limit
    );

    @Query(
            value = """
                    select id
                    from conversion_jobs
                    where cleanup_completed_at is null
                      and (
                          status = 'EXPIRED'
                          or (
                              status in ('CREATED', 'COMPLETED', 'FAILED')
                              and expires_at <= :expiresAt
                          )
                      )
                    order by expires_at asc
                    limit :limit
                    """,
            nativeQuery = true
    )
    List<UUID> findCleanupCandidateIds(
            @Param("expiresAt") Instant expiresAt,
            @Param("limit") int limit
    );

}
