package org.atlasapi.remotesite.health;

import org.atlasapi.media.entity.Channel;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ScheduleResolver;
import org.atlasapi.persistence.system.AToZUriSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.collect.ImmutableList;
import com.metabroadcast.common.health.HealthProbe;
import com.metabroadcast.common.properties.Configurer;
import com.metabroadcast.common.time.Clock;
import com.metabroadcast.common.time.SystemClock;
import com.metabroadcast.common.webapp.health.HealthController;

@Configuration
public class RemoteSiteHealthModule {
    
    private @Autowired ContentResolver store;

    private @Autowired ScheduleResolver scheduleResolver;
    
    private @Autowired HealthController health;
    
    private final Clock clock = new SystemClock();

    public @Bean HealthProbe bbcProbe() {
        return new BroadcasterProbe(Publisher.BBC, ImmutableList.of(
                "http://www.bbc.co.uk/programmes/b006m86d", // Eastenders
                "http://www.bbc.co.uk/programmes/b006mf4b", // Spooks
                "http://www.bbc.co.uk/programmes/b006t1q9", // Question Time
                "http://www.bbc.co.uk/programmes/b006qj9z", // Today
                "http://www.bbc.co.uk/programmes/b006md2v", // Blue Peter
                "http://www.bbc.co.uk/programmes/b0071b63", // The apprentice
                "http://www.bbc.co.uk/programmes/b007t9yb", // Match of the Day 2
                "http://www.bbc.co.uk/programmes/b0087g39", // Helicopter Heroes
                "http://www.bbc.co.uk/programmes/b006mk1s", // Mastermind
                "http://www.bbc.co.uk/programmes/b006wknd" // Rob da Bank
        ), store);
    }
    
    public @Bean HealthProbe c4Probe() {
        return new BroadcasterProbe(Publisher.C4, new AToZUriSource("http://www.channel4.com/programmes/atoz/", "", true), store);
    }
    
    public @Bean HealthProbe c4ScheduleProbe() {
        return new ScheduleProbe(Publisher.C4, Channel.CHANNEL_FOUR, scheduleResolver, clock);
    }
    
    public @Bean HealthProbe bbcScheduleProbe() {
        return new ScheduleProbe(Publisher.BBC, Channel.BBC_ONE, scheduleResolver, clock);
    }
    
    public @Bean HealthProbe scheduleLivenessHealthProbe() {
    	return new ScheduleLivenessHealthProbe(scheduleResolver, ImmutableList.of(
    			Channel.BBC_ONE,
    			Channel.BBC_TWO,
    			Channel.ITV1_LONDON,
    			Channel.CHANNEL_FOUR,
    			Channel.FIVE,
    			Channel.SKY1,
    			Channel.SKY_ATLANTIC
    	), Publisher.PA);
    }
    
    @Bean
    public ScheduleLivenessHealthController scheduleLivenessHealthController() {
    	return new ScheduleLivenessHealthController(health, Configurer.get("pa.schedule.health.username", "").get(), Configurer.get("pa.schedule.health.password", "").get());
    } 
}
