package de.zalando.zmon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.zalando.zmon.service.impl.NoOpEventLog;

/**
 * TODO, we have to replace EventLog.
 * 
 * @author jbellmann
 *
 */
@Configuration
public class EventLogConfiguration {
	
	@Bean
	public NoOpEventLog noOpEventLog(){
		return new NoOpEventLog();
	}

}
