package com.sangyunpark99.tradeserver.scheduler;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "broker.resend.enabled", havingValue = "true")
public class SchedulerConfig {

}
