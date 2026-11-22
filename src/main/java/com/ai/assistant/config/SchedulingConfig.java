package com.ai.assistant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled tasks, such as the nightly website crawls. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
