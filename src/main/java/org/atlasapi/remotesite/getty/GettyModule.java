package org.atlasapi.remotesite.getty;

import javax.annotation.PostConstruct;

import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.listing.ContentLister;
import org.atlasapi.remotesite.knowledgemotion.topics.TopicGuesser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.metabroadcast.common.scheduling.RepetitionRules;
import com.metabroadcast.common.scheduling.SimpleScheduler;

@Configuration
public class GettyModule {

    private @Autowired SimpleScheduler scheduler;
    private @Autowired ContentResolver contentResolver;
    private @Autowired ContentWriter contentWriter;
    private @Autowired ContentLister contentLister;
    private @Autowired TopicGuesser topicGuesser;

    @Value("${getty.client.id}") private String clientId;
    @Value("${getty.client.secret}") private String clientSecret;
    @Value("${getty.client.user}") private String clientUsername;
    @Value("${getty.client.password}") private String clientPassword;
    @Value("${getty.pagination}") private String gettyPagination;

    @PostConstruct
    public void startBackgroundTasks() {
        scheduler.schedule(gettyUpdater().withName("Getty Updater"), RepetitionRules.NEVER);
    }

    private GettyUpdateTask gettyUpdater() {
        return new GettyUpdateTask(gettyClient(), new GettyAdapter(), 
                new DefaultGettyDataHandler(contentResolver, contentWriter, new GettyContentExtractor(topicGuesser)),
                contentLister,
                Integer.valueOf(gettyPagination));
    }

    private GettyClient gettyClient() {
        return new GettyClient(
                new GettyTokenFetcher(clientId, clientSecret, clientUsername, clientPassword),
                Integer.valueOf(gettyPagination)
        );
    }

}
