package org.atlasapi.tracking;

import org.atlasapi.media.entity.Description;
import org.atlasapi.persistence.system.Fetcher;
import org.atlasapi.persistence.tracking.MongoDBBackedContentMentionStore;
import org.atlasapi.tracking.twitter.LinkExtractingStatusProcessor;
import org.atlasapi.tracking.twitter.QueueingTweetProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.collect.ImmutableSet;
import com.metabroadcast.purple.core.buzz.twitter.TweetProcessor;
import com.metabroadcast.purple.core.buzz.twitter.stream.TwitterFilteredPipe;

@Configuration
public class TrackingModule {

	private static final ImmutableSet<String> KEYWORDS = ImmutableSet.of(
		  "clip",
          "bbc",
          "channel",
          "channel4",
          "blip",
          "hulu",
          "youtube",
          "watch",
          "watching",
          "watched",
          "liked",
          "loved",
          "loving",
          "series",
          "episode",
          "TV",
          "iplayer",
          "show",
          "program",
          "programme",
          "catching",
          "season",
          "tonight");
	
	private @Value("${twitter.tracker.username}") String twitterUsername;
	private @Value("${twitter.tracker.password}") String twitterPassword;
	
	private @Autowired MongoDBBackedContentMentionStore mentionListener;
	private @Autowired @Qualifier("contentResolver") Fetcher<Description> contentResolver;
	

	public @Bean TwitterFilteredPipe trackingTwitterPipe() {
        QueueingTweetProcessor processor = new QueueingTweetProcessor(tweetProcessor());
		final TwitterFilteredPipe pipe = new TwitterFilteredPipe(processor, twitterUsername, twitterPassword);
        pipe.setKeywords(KEYWORDS);
        pipe.start();
        return pipe;
    }

	@Bean TweetProcessor tweetProcessor() {
		return new LinkExtractingStatusProcessor(new ContentResolvingUriMentionListener(contentResolver, mentionListener));
	}
}
