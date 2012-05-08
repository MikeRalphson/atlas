package org.atlasapi.remotesite.music.musicbrainz;

import com.metabroadcast.common.properties.Configurer;
import com.metabroadcast.common.scheduling.RepetitionRules;
import javax.annotation.PostConstruct;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.AdapterLogEntry;
import org.atlasapi.persistence.logging.AdapterLogEntry.Severity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.metabroadcast.common.scheduling.SimpleScheduler;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.people.ItemsPeopleWriter;

@Configuration
public class MusicBrainzModule {

    private @Autowired SimpleScheduler scheduler;
    private @Autowired ContentWriter contentWriter;
    private @Autowired ItemsPeopleWriter peopleWriter;
    private @Autowired AdapterLog log;

    @PostConstruct
    public void startBackgroundTasks() {
        scheduler.schedule(updater().withName("Music Brainz Updater"), RepetitionRules.NEVER);
        log.record(new AdapterLogEntry(Severity.INFO).withSource(getClass()).withDescription("Installed Music Brainz updater"));
    }

    @Bean(name="bbcproductsupdater")
    public MusicBrainzUpdater updater() {
        return new MusicBrainzUpdater(contentWriter, peopleWriter, log, Configurer.get("musicbrainz.dataDir").get());
    }
}
