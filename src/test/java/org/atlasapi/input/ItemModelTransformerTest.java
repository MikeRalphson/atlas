package org.atlasapi.input;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.Date;

import com.google.common.collect.ImmutableSet;
import org.atlasapi.media.channel.ChannelResolver;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.entity.simple.*;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.atlasapi.persistence.topic.TopicStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Lists;
import com.metabroadcast.common.ids.NumberToShortStringCodec;
import com.metabroadcast.common.time.Clock;

@RunWith(MockitoJUnitRunner.class)
public class ItemModelTransformerTest {

    private ItemModelTransformer transformer;

    private @Mock LookupEntryStore lookupEntryStore;
    private @Mock TopicStore topicStore;
    private @Mock ChannelResolver channelResolver;
    private @Mock NumberToShortStringCodec idCodec;
    private @Mock ClipModelTransformer clipModelTransformer;
    private @Mock Clock clock;
    private @Mock SegmentModelTransformer segmentModelTransformer;

    private Item simpleItem;
    private Broadcast simpleBroadcast;
    private Restriction simpleRestriction;
    private Location simpleLocation;

    @Before
    public void setUp() throws Exception {
        transformer = new ItemModelTransformer(
                lookupEntryStore,
                topicStore,
                channelResolver,
                idCodec,
                clipModelTransformer,
                clock,
                segmentModelTransformer
        );

        simpleRestriction = getSimpleRestriction();
        simpleBroadcast = getSimpleBroadcast();
        simpleLocation = getSimpleLocation();
        simpleItem = getSimpleItem();
    }

    @Test
    public void testTransformItemWithBroadcastVersionsTransformsAllVersionFields()
            throws Exception {
        simpleItem.setShortDescription("H");
        simpleItem.setMediumDescription("Hello");
        simpleItem.setLongDescription("Hello World");
        org.atlasapi.media.entity.Item complex = transformer.transform(simpleItem);

        assertThat(complex.getVersions().size(), is(1));
        assertThat(simpleItem.getShortDescription(), is(complex.getShortDescription()));
        assertThat(simpleItem.getMediumDescription(), is(complex.getMediumDescription()));
        assertThat(simpleItem.getLongDescription(), is(complex.getLongDescription()));

        Version version = complex.getVersions().iterator().next();
        checkRestriction(version.getRestriction());
    }

    @Test
    public void testTransformDuration()
            throws Exception {
        org.atlasapi.media.entity.Item complex = transformer.transform(simpleItem);

        assertThat(complex.getVersions().size(), is(1));
        Version version = complex.getVersions().iterator().next();
        assertThat(version.getDuration(), is (2));
    }

    public void testTransformItemWithEventRefs() {
        when(idCodec.decode("12345")).thenReturn(BigInteger.valueOf(12345));
        when(idCodec.decode("1234")).thenReturn(BigInteger.valueOf(1234));
        org.atlasapi.media.entity.Item complex = transformer.transform(getItemWithEventRefs());
        assertEquals(1, complex.events().size());
        assertThat(complex.events().get(0).id(), is(1234L));
    }

    private void checkRestriction(org.atlasapi.media.entity.Restriction restriction) {
        assertThat(restriction.isRestricted(), is(simpleRestriction.isRestricted()));
        assertThat(restriction.getMinimumAge(), is(simpleRestriction.getMinimumAge()));
        assertThat(restriction.getRating(), is(simpleRestriction.getRating()));
        assertThat(restriction.getAuthority(), is(simpleRestriction.getAuthority()));
        assertThat(restriction.getMessage(), is(simpleRestriction.getMessage()));
    }

    private Item getSimpleItem() {
        Item item = new Item();
        item.setUri("uri");
        item.setPublisher(new PublisherDetails(Publisher.BBC.key()));
        item.setBroadcasts(Lists.newArrayList(simpleBroadcast));
        item.setLocations(Lists.<Location>newArrayList(simpleLocation));
        return item;
    }

    private Item getItemWithEventRefs(){
        Item item = new Item();
        item.setId("12345");
        item.setUri("uri");
        item.setPublisher(new PublisherDetails(Publisher.BBC.key()));

        EventRef eventRef = new EventRef();
        eventRef.setId("1234");
        eventRef.setPublisher(new PublisherDetails(Publisher.BBC.key()));
        item.setEventRefs(ImmutableSet.of(eventRef));
        return item;

    }

    private Broadcast getSimpleBroadcast() {
        Broadcast broadcast = new Broadcast();

        broadcast.setTransmissionTime(new Date());
        broadcast.setBroadcastDuration(10);
        broadcast.setBroadcastOn("broadcastOn");

        broadcast.setRestriction(simpleRestriction);

        return broadcast;
    }

    private Restriction getSimpleRestriction() {
        Restriction restriction = new Restriction();

        restriction.setMinimumAge(12);
        restriction.setRestricted(true);
        restriction.setRating("rating");
        restriction.setAuthority("authority");
        restriction.setMessage("message");

        return restriction;
    }

    private Location getSimpleLocation() {
        Location location = new Location();
        location.setDuration(2000);
        return location;
    }
}