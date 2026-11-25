package sn.arlettedebo.platform.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tâches planifiées (purges, rappels). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
