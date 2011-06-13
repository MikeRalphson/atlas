package org.atlasapi.remotesite.channel4.epg;

import static com.google.common.collect.Iterables.getLast;
import static com.google.common.collect.Iterables.getOnlyElement;
import static org.atlasapi.media.entity.Channel.CHANNEL_FOUR;
import static org.atlasapi.media.entity.Publisher.C4;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import java.util.List;

import junit.framework.TestCase;

import org.atlasapi.StubContentResolver;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.SeriesRef;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.SystemOutAdapterLog;
import org.atlasapi.remotesite.channel4.RecordingContentWriter;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.joda.time.DateTime;
import org.joda.time.Duration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.intl.Countries;
import com.metabroadcast.common.time.DateTimeZones;

public class C4EpgEntryProcessorTest extends TestCase {
    
    private final AdapterLog log = new SystemOutAdapterLog();
    
    private final Mockery context = new Mockery();
    
    private final ContentResolver resolver = context.mock(ContentResolver.class);

    //Item, series and brand don't exist so all are made.
    public void testProcessNewItemSeriesBrand() {
        
    	ContentResolver resolver = StubContentResolver.RESOLVES_NOTHING;

    	RecordingContentWriter writer = new RecordingContentWriter();
    	
    	C4EpgEntryProcessor processor = new C4EpgEntryProcessor(writer, resolver, log);
        processor.process(buildEntry(), CHANNEL_FOUR);
        
        Brand brand = Iterables.getOnlyElement(writer.updatedBrands);
        
        assertThat(brand.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs")));
        assertThat(brand.getCurie(), is(equalTo("c4:the-hoobs")));
        
        
        Series series = Iterables.getOnlyElement(writer.updatedSeries);
        assertThat(series.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1")));
        assertThat(series.getParent().getUri(), is(brand.getCanonicalUri()));
        
        List<Episode> contents = ImmutableList.copyOf(Iterables.filter(writer.updatedItems, Episode.class));
        assertThat(contents.size(), is(equalTo(1)));
        
        Episode episode = getOnlyElement(contents);
        assertThat(episode.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-59")));
        assertThat(episode.getTitle(), is(equalTo("Dancing")));
        assertThat(episode.getEpisodeNumber(), is(59));
        assertThat(episode.getSeriesNumber(), is(1));
        assertThat(episode.getSeriesRef().getUri(), is(series.getCanonicalUri()));
        
        
        Version version = getOnlyElement(episode.getVersions());
        assertThat(version.getDuration().longValue(), is(equalTo(Duration.standardMinutes(24).plus(Duration.standardSeconds(12)).getStandardSeconds())));
        
        Broadcast broadcast = getOnlyElement(version.getBroadcasts());
        assertThat(broadcast.getId(), is(equalTo("c4:337")));
        assertThat(broadcast.getAliases().size(), is(1));
        assertThat(broadcast.getAliases(), hasItem("tag:www.channel4.com,2009:slot/C4337"));
        assertThat(broadcast.getTransmissionTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z"))));
        assertThat(broadcast.getTransmissionEndTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z").plus(Duration.standardMinutes(24).plus(Duration.standardSeconds(12))))));
        
        Encoding encoding = getOnlyElement(version.getManifestedAs());
        Location location = getOnlyElement(encoding.getAvailableAt());
        assertThat(location.getUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/4od#2930251")));
        assertThat(location.getPolicy().getAvailabilityStart(), is(equalTo(new DateTime("2009-06-07T22:00:00.000Z"))));
        assertThat(location.getPolicy().getAvailabilityEnd(), is(equalTo(new DateTime("2018-12-07T00:00:00.000Z"))));
    }
    
    public void testProcessExistingItemSeriesBrand() { 
        
        final Episode episode = existingEpisode();
        final Series series = new Series("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1", "c4:the-hoobs-series-1", C4);
        final Brand brand = new Brand("http://www.channel4.com/programmes/the-hoobs", "c4:the-hoobs", C4);

        episode.setContainer(brand);
        episode.setSeries(series);
        
        final ContentWriter writer = context.mock(ContentWriter.class);
        
        context.checking(new Expectations(){{
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("episode-59")))); will(returnValue(episode)); //item
            one(resolver).findByCanonicalUris(with(hasItem(containsString("synthesized")))); will(returnValue(null)); //no synth item
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("series-1")))); will(returnValue(series)); //series
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("the-hoobs")))); will(returnValue(brand)); //brand
            one(writer).createOrUpdate(with(updatedBrandWithExistingItem()));
        }});
        
        C4EpgEntryProcessor processor = new C4EpgEntryProcessor(writer, resolver, log);
        
        processor.process(buildEntry(), CHANNEL_FOUR);
        
        context.assertIsSatisfied();
    }
    
    public Matcher<Brand> updatedBrandWithExistingItem() {
        return new TypeSafeMatcher<Brand>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("");
            }

            @Override
            public boolean matchesSafely(Brand brand) {
                assertThat(brand.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs")));
                
                assertThat(brand.getSeries().size(), is(equalTo(1)));
                assertThat(getOnlyElement(brand.getSeries()).getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1")));

                ImmutableList<Episode> contents = brand.getContents();
                assertThat(contents.size(), is(equalTo(1)));
                
                Episode episode = getOnlyElement(contents);
                assertThat(episode.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-59")));
                assertThat(episode.getTitle(), is(equalTo("Dancing")));
                assertThat(episode.getEpisodeNumber(), is(59));
                assertThat(episode.getSeriesNumber(), is(1));
                
                Version version = getOnlyElement(episode.getVersions());
                assertThat(version.getDuration().longValue(), is(equalTo(Duration.standardMinutes(24).plus(Duration.standardSeconds(12)).getStandardSeconds())));
                
                assertThat(version.getBroadcasts().size(), is(2));
                Broadcast broadcast = getLast(version.getBroadcasts()).getCurie() != null ? Iterables.get(version.getBroadcasts(), 0) : getLast(version.getBroadcasts());
                assertThat(broadcast.getId(), is(equalTo("c4:337")));
                assertThat(broadcast.getAliases().size(), is(1));
                assertThat(broadcast.getAliases(), hasItem("tag:www.channel4.com,2009:slot/C4337"));
                assertThat(broadcast.getTransmissionTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z"))));
                assertThat(broadcast.getTransmissionEndTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z").plus(Duration.standardMinutes(24).plus(Duration.standardSeconds(12))))));
                
                Encoding encoding = getOnlyElement(version.getManifestedAs());
                assertThat(encoding.getAvailableAt().size(), is(1));
                Location location = getOnlyElement(encoding.getAvailableAt());

                // EPG update doesn't modify locations created overnight
                assertThat(location.getUri(), is(equalTo("oldUri")));
                return true;
            }
        };
    }

    private Episode existingEpisode() {
        Episode episode = new Episode("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-59", "c4:the-hoobs-series-1-episode-1", C4);
        episode.setTitle("Dancing");
        episode.setSeriesNumber(1);
        episode.setEpisodeNumber(59);
        
        Version version = new Version();

        Broadcast broadcast = new Broadcast("http://www.channel4.com", new DateTime(DateTimeZones.UTC), new DateTime(DateTimeZones.UTC));
        broadcast.setCurie("old");
        broadcast.withId("c4:345");

        Encoding encoding = new Encoding();
        Location location = new Location();
        location.setUri("oldUri");
		encoding.addAvailableAt(location);
        
        version.addBroadcast(broadcast);
        version.addManifestedAs(encoding);
        episode.addVersion(version);
        
        return episode;
    }
    
    public void testProcessNewItemSeriesExistingBrand() {
        
        final Brand brand = new Brand("http://www.channel4.com/programmes/the-hoobs", "c4:the-hoobs", C4);
        Series series = new Series("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1", "c4:the-hoobs-series-1", C4);
        Episode episode = new Episode("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-58", "c4:the-hoobs-series-1-episode-58", C4);
        
        episode.setContainer(brand);
        episode.setSeries(series);
        episode.setSeriesNumber(1);
        episode.setEpisodeNumber(58);

        final ContentWriter writer = context.mock(ContentWriter.class);
        
        context.checking(new Expectations(){{
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("episode-59")))); will(returnValue(null)); //item
            one(resolver).findByCanonicalUris(with(hasItem(containsString("synthesized")))); will(returnValue(null)); //no synth item
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("series-1")))); will(returnValue(null)); //series
            one(resolver).findByCanonicalUris(with(hasItem(endsWith("the-hoobs")))); will(returnValue(brand)); //brand
            one(writer).createOrUpdate(with(updatedBrandWithNewItem()));
        }});
        
        C4EpgEntryProcessor processor = new C4EpgEntryProcessor(writer, resolver, log);
        
        processor.process(buildEntry(), CHANNEL_FOUR);
        
        context.assertIsSatisfied();
        
    }
    
    private Matcher<Brand> updatedBrandWithNewItem() {
        return new TypeSafeMatcher<Brand>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("updated brand");
            }

            @Override
            public boolean matchesSafely(Brand brand) {
                assertThat(brand.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs")));
                
                assertThat(brand.getSeries().size(), is(equalTo(1)));
                assertThat(getOnlyElement(brand.getSeries()).getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1")));

                ImmutableList<Episode> contents = brand.getContents();
                assertThat(contents.size(), is(equalTo(2)));
                
                Episode episode = Iterables.getLast(contents);
                episode = episode.getCanonicalUri().endsWith("episode-59") ? episode : Iterables.getFirst(contents, episode);
                assertThat(episode.getCanonicalUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-59")));
                assertThat(episode.getTitle(), is(equalTo("Dancing")));
                assertThat(episode.getEpisodeNumber(), is(59));
                assertThat(episode.getSeriesNumber(), is(1));
                
                
                Version version = getOnlyElement(episode.getVersions());
                assertThat(version.getDuration().longValue(), is(equalTo(Duration.standardMinutes(24).plus(Duration.standardSeconds(12)).getStandardSeconds())));
                
                Broadcast broadcast = getOnlyElement(version.getBroadcasts());
                assertThat(broadcast.getId(), is(equalTo("c4:337")));
                assertThat(broadcast.getAliases().size(), is(1));
                assertThat(broadcast.getAliases(), hasItem("tag:www.channel4.com,2009:slot/C4337"));
                assertThat(broadcast.getTransmissionTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z"))));
                assertThat(broadcast.getTransmissionEndTime(), is(equalTo(new DateTime("2011-01-07T06:35:00.000Z").plus(Duration.standardMinutes(24).plus(Duration.standardSeconds(12))))));
                
                Encoding encoding = getOnlyElement(version.getManifestedAs());
                Location location = getOnlyElement(encoding.getAvailableAt());
                assertThat(location.getUri(), is(equalTo("http://www.channel4.com/programmes/the-hoobs/4od#2930251")));
                assertThat(location.getPolicy().getAvailabilityStart(), is(equalTo(new DateTime("2009-06-07T22:00:00.000Z"))));
                assertThat(location.getPolicy().getAvailabilityEnd(), is(equalTo(new DateTime("2018-12-07T00:00:00.000Z"))));
                return true;
            }
        };
    }

    private C4EpgEntry buildEntry() {
        return new C4EpgEntry("tag:www.channel4.com,2009:slot/337")
            .withTitle("Dancing")
            .withUpdated(new DateTime("2010-11-03T05:57:50.175Z"))
            .withSummary("Hoobs have been dancing the Hoobyjiggle since Hooby time began. But is there a Peep dance that fits to the Hoobyjiggle music?")
            .withLinks(ImmutableList.of(
                    "http://www.channel4.com/programmes/the-hoobs/4od#2930251", 
                    "http://www.channel4.com/programmes/the-hoobs/episode-guide/series-1/episode-59.atom"
            )).withMedia(
                    new C4EpgMedia()
                        .withPlayer("http://www.channel4.com/programmes/the-hoobs/4od#2930251")
                        .withThumbnail("http://www.channel4.com/assets/programmes/images/the-hoobs/series-1/the-hoobs-s1-20090623112301_200x113.jpg")
                        .withRating("nonadult")
                        .withRestriction(ImmutableSet.of(Countries.GB, Countries.IE)))
            .withBrandTitle("The Hoobs")
            .withAvailable("start=2009-06-07T22:00:00.000Z; end=2018-12-07T00:00:00.000Z; scheme=W3C-DTF")
            .withSeriesNumber(1)
            .withEpisodeNumber(59)
            .withAgeRating(0)
            .withTxDate(new DateTime("2011-01-07T06:35:00.000Z"))
            .withTxChannel("C4")
            .withSubtitles(true)
            .withAudioDescription(false)
            .withDuration(Duration.standardMinutes(24).plus(Duration.standardSeconds(12)));
    }

}
