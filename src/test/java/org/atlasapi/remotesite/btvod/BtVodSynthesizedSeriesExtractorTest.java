package org.atlasapi.remotesite.btvod;


import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Topic;
import org.atlasapi.persistence.topic.TopicCreatingTopicResolver;
import org.atlasapi.persistence.topic.TopicWriter;
import org.atlasapi.remotesite.btvod.contentgroups.BtVodContentMatchingPredicates;
import org.atlasapi.remotesite.btvod.model.BtVodEntry;
import org.atlasapi.remotesite.btvod.model.BtVodPlproduct$productTag;
import org.atlasapi.remotesite.btvod.model.BtVodProductScope;

import com.google.api.client.util.Sets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BtVodSynthesizedSeriesExtractorTest {

    private static final String BRAND_TITLE = "Brand Title";
    private static final String PRODUCT_ID = "1234";
    private static final String REAL_EPISODE_TITLE = "Real Title";
    private static final String FULL_EPISODE_TITLE = BRAND_TITLE + ": S1 S1-E9 " + REAL_EPISODE_TITLE;
    private static final Publisher PUBLISHER = Publisher.BT_VOD;
    private static final String SERIES_GUID = "series_guid";
    private static final Topic NEW_TOPIC = new Topic(123L);
    private static final String BT_VOD_GUID_NAMESPACE = "guid namespace";
    private static final String BT_VOD_ID_NAMESPACE = "id namespace";
    private static final String BT_VOD_SYNTHESISED_FROM_GUID_ALIAS_NAMESPACE = "synth guid namespace";
    private static final String BT_VOD_SYNTHESISED_FROM_ID_ALIAS_NAMESPACE = "synth id namespace";
    private static final String BT_VOD_CONTENT_PROVIDER_NAMESPACE = "content provider namespace";
    private static final String BT_VOD_GENRE_NAMESPACE = "genre namespace";
    private static final String BT_VOD_KEYWORD_NAMESPACE = "keyword namespace";

    private final BtVodBrandProvider brandProvider = mock(BtVodBrandProvider.class);
    private final BtVodContentListener contentListener = mock(BtVodContentListener.class);
    private final TopicCreatingTopicResolver topicResolver = mock(TopicCreatingTopicResolver.class);
    private final TopicWriter topicWriter = mock(TopicWriter.class);
    private final BtVodContentMatchingPredicate newTopicContentMatchingPredicate = mock(BtVodContentMatchingPredicate.class);
    private final BtVodSeriesUriExtractor seriesUriExtractor = mock(BtVodSeriesUriExtractor.class);
    private final BtVodTagMap btVodTagMap = mock(BtVodTagMap.class);

    private final BtVodDescribedFieldsExtractor describedFieldsExtractor = new BtVodDescribedFieldsExtractor(
            topicResolver,
            topicWriter,
            Publisher.BT_VOD,
            newTopicContentMatchingPredicate,
            BtVodContentMatchingPredicates.schedulerChannelPredicate("Kids"),
            BtVodContentMatchingPredicates.schedulerChannelAndOfferingTypePredicate(
                    "TV", ImmutableSet.of("Season", "Season-EST")
            ),
            BtVodContentMatchingPredicates.schedulerChannelPredicate("TV Replay"),
            NEW_TOPIC,
            new Topic(234L),
            new Topic(345L),
            new Topic(456L),
            BT_VOD_GUID_NAMESPACE,
            BT_VOD_SYNTHESISED_FROM_GUID_ALIAS_NAMESPACE,
            BT_VOD_ID_NAMESPACE,
            BT_VOD_SYNTHESISED_FROM_ID_ALIAS_NAMESPACE,
            BT_VOD_CONTENT_PROVIDER_NAMESPACE,
            BT_VOD_GENRE_NAMESPACE,
            BT_VOD_KEYWORD_NAMESPACE
    );

    private final BtVodSynthesizedSeriesExtractor seriesExtractor = new BtVodSynthesizedSeriesExtractor(
            brandProvider,
            PUBLISHER,
            contentListener,
            describedFieldsExtractor, 
            Sets.<String>newHashSet(),
            seriesUriExtractor,
            ImmutableSet.of(SERIES_GUID),
            btVodTagMap
    );

    @Test
    public void testExtractsSeriesFromEpisode() {
        BtVodEntry entry = row();

        String brandUri = "http://brand-uri.com";
        ParentRef brandRef = mock(ParentRef.class);

        when(brandProvider.brandRefFor(entry)).thenReturn(Optional.of(brandRef));
        when(seriesUriExtractor.extractSeriesNumber(entry)).thenReturn(Optional.of(1));
        when(seriesUriExtractor.seriesUriFor(entry)).thenReturn(Optional.of(brandUri + "/series/1"));

        seriesExtractor.process(entry);

        Series series = Iterables.getOnlyElement(seriesExtractor.getSynthesizedSeries().values());
        assertThat(series.getCanonicalUri(), is(brandUri + "/series/1"));
        assertThat(series.getSeriesNumber(), is(1));
        assertThat(series.getParent(), is(brandRef));
        assertThat(seriesExtractor.getSeriesFor(entry).get().getCanonicalUri(), is(brandUri + "/series/1"));
    }

    @Test
    public void testDoesntExtractsSeriesFromEpisodeWhichAlreadyHasExplicitSeriesExtracted() {
        BtVodEntry entry = row();
        entry.setParentGuid(SERIES_GUID);
        when(seriesUriExtractor.extractSeriesNumber(entry)).thenReturn(Optional.of(1));
        when(seriesUriExtractor.seriesUriFor(entry)).thenReturn(Optional.of("URI"));

        seriesExtractor.process(entry);

        assertThat(seriesExtractor.getSynthesizedSeries().isEmpty(), is(true));
    }

    @Test
    public void testDoesntExtractSeriesFromNonEpisode() {
        BtVodEntry entry = row();
        entry.setProductType("film");

        ParentRef brandRef = mock(ParentRef.class);

        when(brandProvider.brandRefFor(entry)).thenReturn(Optional.of(brandRef));

        seriesExtractor.process(entry);
        assertThat(seriesExtractor.getSynthesizedSeries().isEmpty(), is(true));
    }

    @Test
    public void testUpdatesBrandFromSeries() {
        BtVodEntry entry = row();

        String brandUri = "http://brand-uri.com";
        ParentRef brandRef = mock(ParentRef.class);

        when(brandProvider.brandRefFor(entry)).thenReturn(Optional.of(brandRef));
        when(seriesUriExtractor.extractSeriesNumber(entry)).thenReturn(Optional.of(1));
        when(seriesUriExtractor.seriesUriFor(entry)).thenReturn(Optional.of(brandUri + "/series/1"));

        seriesExtractor.process(entry);

        Series series = Iterables.getOnlyElement(seriesExtractor.getSynthesizedSeries().values());

        verify(brandProvider).updateBrandFromSeries(entry, series);
    }

    @Test
    public void testSetSynthesisedAliasesFromBtEntry() throws Exception {
        BtVodEntry entry = row();

        String brandUri = "http://brand-uri.com";
        ParentRef brandRef = mock(ParentRef.class);

        when(brandProvider.brandRefFor(entry)).thenReturn(Optional.of(brandRef));
        when(seriesUriExtractor.extractSeriesNumber(entry)).thenReturn(Optional.of(1));
        when(seriesUriExtractor.seriesUriFor(entry)).thenReturn(Optional.of(brandUri + "/series/1"));

        seriesExtractor.process(entry);

        Series series = Iterables.getOnlyElement(seriesExtractor.getSynthesizedSeries().values());

        assertThat(series.getAliases().size(), is(2));
        assertThat(series.getAliases().containsAll(Lists.newArrayList(
                new Alias(BT_VOD_SYNTHESISED_FROM_GUID_ALIAS_NAMESPACE, entry.getGuid()),
                new Alias(BT_VOD_SYNTHESISED_FROM_ID_ALIAS_NAMESPACE, entry.getId())
        )),
                is(true));
    }

    private BtVodEntry row() {
        BtVodEntry entry = new BtVodEntry();
        entry.setGuid(PRODUCT_ID);
        entry.setId("12345");
        entry.setTitle(FULL_EPISODE_TITLE);
        entry.setProductOfferStartDate(1364774400000L); //"Apr  1 2013 12:00AM"
        entry.setProductOfferEndDate(1398816000000L);// "Apr 30 2014 12:00AM"
        entry.setProductType("episode");// "Apr 30 2014 12:00AM"
        entry.setProductTags(ImmutableList.<BtVodPlproduct$productTag>of());
        entry.setProductScopes(ImmutableList.<BtVodProductScope>of());
        return entry;
    }
}
