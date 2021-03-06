package org.atlasapi.remotesite.knowledgemotion;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.media.entity.TopicRef.Relationship;
import org.atlasapi.media.entity.Version;
import org.atlasapi.remotesite.knowledgemotion.topics.TopicGuesser;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class KnowledgeMotionDataRowContentExtractorTest {

    private static final ImmutableList<KnowledgeMotionSourceConfig> SOURCES = ImmutableList.of(
        KnowledgeMotionSourceConfig.from("GlobalImageworks", Publisher.KM_GLOBALIMAGEWORKS, "globalImageWorks:%s", "http://globalimageworks.com/%s"),
        KnowledgeMotionSourceConfig.from("BBC Worldwide", Publisher.KM_BBC_WORLDWIDE, "km-bbcWorldwide:%s", "http://bbc.knowledgemotion.com/%s"),
        KnowledgeMotionSourceConfig.from("British Movietone", Publisher.KM_MOVIETONE, "km-movietone:%s", "http://movietone.knowledgemotion.com/%s"),
        KnowledgeMotionSourceConfig.from("Bloomberg", Publisher.KM_BLOOMBERG, "bloomberg:%s", "http://bloomberg.com/%s")
    );

    private final TopicGuesser topicGuesser = Mockito.mock(TopicGuesser.class);
    private final KnowledgeMotionDataRowContentExtractor extractor = new KnowledgeMotionDataRowContentExtractor(SOURCES, topicGuesser);

    @Test
    public void testExtractItem() {

        ImmutableSet<TopicRef> topicRefs = ImmutableSet.of(new TopicRef(9000l, 1.0f, false, Relationship.ABOUT));
        Mockito.when(topicGuesser.guessTopics(Matchers.anyCollection())).thenReturn(topicRefs);

        KnowledgeMotionDataRow row = new KnowledgeMotionDataRow(
                "GlobalImageworks",
                "id",
                "title",
                "description",
                "2014-01-01",
                "0:01:01;10",
                ImmutableList.of("key"),
                ImmutableList.of("stock","news"),
                null,
                "Terms of Use"
        );

        KnowledgeMotionDataRow badRow = new KnowledgeMotionDataRow(
                "GlobalInageworks",
                "id",
                "title",
                "description",
                "2014-01-01",
                "0:01:01;10",
                ImmutableList.of("key"),
                ImmutableList.<String>of(),
                null,
                null
        );

        Optional<? extends Content> content = extractor.extract(row);
        Item item = (Item) content.get();
        assertThat(item.getCanonicalUri(), endsWith("id"));
        assertThat(item.getPublisher(), is(Publisher.KM_GLOBALIMAGEWORKS));
        assertThat(item.getDescription(), is("description"));
        assertThat(item.getTitle(), is("title"));
        assertThat(item.getMediaType(), is(MediaType.VIDEO));

        ReleaseDate releaseDate = Iterables.getOnlyElement(item.getReleaseDates());
        assertThat(releaseDate.date().toString(), is("2014-01-01"));

        Version version = Iterables.getOnlyElement(item.getVersions());
        assertThat(version.getDuration(), is(Integer.valueOf(61)));
        assertThat(Iterables.getOnlyElement(item.getKeyPhrases()), is(new KeyPhrase("key", Publisher.KM_GLOBALIMAGEWORKS)));
        assertThat(ImmutableSet.copyOf(item.getTopicRefs()), is(topicRefs));

        Encoding encoding = Iterables.getOnlyElement(version.getManifestedAs());
        Location location = Iterables.getOnlyElement(encoding.getAvailableAt());

        assertThat(location.getPolicy().getTermsOfUse(), is("Terms of Use"));

        assertThat(item.getGenres(), hasItems("http://knowledgemotion.com/stock", "http://knowledgemotion.com/news"));
        assertFalse(extractor.extract(badRow).isPresent());  // Because it has an incorrect source!
    }


}
