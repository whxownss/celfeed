package com.xowns.celfeed.server.batch.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobScheduleConfigTest {

    private final JobOperator jobOperator = mock(JobOperator.class);
    private final Job job = mock(Job.class);
    private final JobScheduleConfig scheduleConfig = new JobScheduleConfig(jobOperator, job);

    @Test
    @DisplayName("잡 파라미터 date 는 지난달 1일(yyyyMMdd)이다 → 그 이전에 생성된 알림이 이관 대상")
    void dateParameterIsFirstDayOfLastMonth() throws Exception {
        scheduleConfig.runJob();

        ArgumentCaptor<JobParameters> parameters = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(job), parameters.capture());

        String expected = LocalDate.now().minusMonths(1).withDayOfMonth(1)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        assertThat(parameters.getValue().getString("date")).isEqualTo(expected);
    }

    @Test
    @DisplayName("매월 3일 04시(Asia/Seoul)에 실행된다")
    void schedule() throws NoSuchMethodException {
        Scheduled scheduled = JobScheduleConfig.class.getMethod("runJob").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 4 3 * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
    }
}
