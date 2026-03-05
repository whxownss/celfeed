package com.xowns.celfeed.config.batch;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Configuration
@RequiredArgsConstructor
public class JobScheduleConfig {

    private final JobOperator jobOperator;
    private final Job job;

    @Scheduled(cron = "0 0 4 3 * *", zone = "Asia/Seoul")
    public void runJob() throws Exception {
        LocalDate firstDayOfLastMonth = LocalDate.now()
                .minusMonths(1)
                .withDayOfMonth(1);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String date = firstDayOfLastMonth.format(formatter);

        JobParameters param = new JobParametersBuilder()
                .addString("date", date)
                .toJobParameters();

        jobOperator.start(job, param);
    }
}
