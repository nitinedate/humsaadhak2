package org.humsaadhak.giving.service;

import org.humsaadhak.giving.entity.Instalment;
import org.humsaadhak.giving.repository.InstalmentRepository;
import org.humsaadhak.giving.entity.RecurringPlan;
import org.humsaadhak.giving.repository.RecurringPlanRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.humsaadhak.giving.api.InstalmentDue;
import org.humsaadhak.shared.model.Periodicity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fixes D5: runs daily for every periodicity (no hard-coded filters), once per cluster (ShedLock),
 * and reminds each instalment at most once.
 */
@Component
public class InstalmentScheduler {
    private final InstalmentRepository instalments;
    private final RecurringPlanRepository plans;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public InstalmentScheduler(InstalmentRepository instalments, RecurringPlanRepository plans, ApplicationEventPublisher events, Clock clock) {
        this.instalments = instalments; this.plans = plans; this.events = events; this.clock = clock;
    }

    @Scheduled(cron = "${humsaadhak.giving.reminder-cron:0 0 7 * * *}", zone = "Asia/Kolkata")
    @SchedulerLock(name = "instalment-reminders", lockAtMostFor = "PT30M")
    @Transactional
    public void sendReminders() {
        LocalDate today = LocalDate.now(clock);
        for (Instalment i : instalments.findByStatusAndDueDateBetween(Instalment.Status.DUE, today, today.plusDays(31))) {
            if (i.getReminderSentAt() != null) continue;
            RecurringPlan plan = plans.findById(i.getPlanId()).orElse(null);
            if (plan == null || !plan.isActive()) continue;
            Periodicity p = plan.getPeriodicity();
            if (Periodicity.inReminderWindow(today, i.getDueDate(), p.defaultReminderDays())) {
                i.setReminderSentAt(Instant.now(clock));
                events.publishEvent(new InstalmentDue(i.getId(), plan.getDonorOrgId(), plan.getNeedId(), i.getAmount(), i.getDueDate()));
            }
        }
    }

    @Configuration
    @EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
    public static class LockConfig {
        @Bean
        public LockProvider lockProvider(DataSource ds) {
            return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                    .withJdbcTemplate(new JdbcTemplate(ds)).usingDbTime().build());
        }
    }
}
