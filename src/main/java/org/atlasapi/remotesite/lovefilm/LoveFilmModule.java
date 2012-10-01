package org.atlasapi.remotesite.lovefilm;

import com.metabroadcast.common.properties.Configurer;
import javax.annotation.PostConstruct;

import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.AdapterLogEntry;
import org.atlasapi.persistence.logging.AdapterLogEntry.Severity;
import org.jets3t.service.security.AWSCredentials;
import org.joda.time.LocalTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import com.metabroadcast.common.scheduling.RepetitionRules;
import com.metabroadcast.common.scheduling.RepetitionRules.Daily;
import com.metabroadcast.common.scheduling.SimpleScheduler;
import org.atlasapi.persistence.content.ContentResolver;

@Configuration
public class LoveFilmModule {
    
    private final static Daily DAILY = RepetitionRules.daily(new LocalTime(4, 30, 0));
    
    private @Autowired SimpleScheduler scheduler;
    private @Autowired ContentResolver contentResolver;
    private @Autowired ContentWriter contentWriter;
    private @Autowired AdapterLog log;
    
    @PostConstruct
    public void startBackgroundTasks() {
//        scheduler.schedule(loveFilmUpdater().withName("LoveFilm Updater"), DAILY);
        log.record(new AdapterLogEntry(Severity.INFO).withSource(getClass()).withDescription("Installed LoveFilm updater"));
        scheduler.schedule(loveFilmCsvUpdater().withName("LoveFilm CSV Updater"), RepetitionRules.NEVER);
    }
    
    private LoveFilmCsvUpdateTask loveFilmCsvUpdater() {
        String s3access = Configurer.get("s3.access").get();
        String s3secret = Configurer.get("s3.secret").get();
        String s3bucket = Configurer.get("lovefilm.s3.bucket").get();
        String s3folder = Configurer.get("lovefilm.s3.folder").get();
        AWSCredentials credentials = new AWSCredentials(s3access, s3secret);
        RestS3ServiceSupplier serviceSupplier = new RestS3ServiceSupplier(credentials);
        LoveFilmDataSupplier dataSupplier = new S3LoveFilmDataSupplier(serviceSupplier, s3bucket, s3folder);
        LoveFilmDataRowHandler dataHandler = new DefaultLoveFilmDataRowHandler(contentResolver, contentWriter);
        return new LoveFilmCsvUpdateTask(dataSupplier, dataHandler);
    }

//    @Bean
//    public LoveFilmUpdater loveFilmUpdater() {
//        return new LoveFilmUpdater(contentResolver, contentWriter, log, Configurer.get("lovefilm.oauth.api.key").get(), Configurer.get("lovefilm.oauth.api.secret").get());
//    }
}
