package org.atlasapi.remotesite.rte;

import javax.annotation.PostConstruct;

import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.remotesite.ContentMerger;
import org.atlasapi.remotesite.ContentMerger.MergeStrategy;
import org.atlasapi.remotesite.support.atom.AtomClient;
import org.joda.time.LocalTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.metabroadcast.common.scheduling.RepetitionRules;
import com.metabroadcast.common.scheduling.SimpleScheduler;

@Configuration
public class RteModule {

    private final static LocalTime START_TIME = new LocalTime(21, 0);
    
    @Value("${rte.feed.url}") private String feedUrl;

    @Autowired private SimpleScheduler scheduler;
    @Autowired private ContentWriter contentWriter;
    @Autowired private ContentResolver contentResolver;

    
    @Bean
    public RteFeedUpdater feedUpdater() {
        return new RteFeedUpdater(feedSupplier(), feedProcessor());
    }
    
    @Bean 
    public RteFeedSupplier feedSupplier() {
        return new RteHttpFeedSupplier(new AtomClient(), feedUrl);
    }
    
    @Bean
    public RteFeedProcessor feedProcessor() {
        return new RteFeedProcessor(contentWriter, contentResolver, new ContentMerger(
                MergeStrategy.MERGE), brandExtractor());
    }
    
    @Bean
    public RteBrandExtractor brandExtractor() {
        return new RteBrandExtractor();
    }
    
    @PostConstruct
    public void init() {
        scheduler.schedule(feedUpdater().withName("RTE AZ Feed Ingest"), RepetitionRules.daily(START_TIME));
    }
    
}
