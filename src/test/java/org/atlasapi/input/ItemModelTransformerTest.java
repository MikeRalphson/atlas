package org.atlasapi.input;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Date;

import com.google.common.collect.ImmutableSet;
import junit.framework.Assert;
import org.atlasapi.media.channel.ChannelResolver;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.entity.simple.*;
import org.atlasapi.persistence.lookup.entry.LookupEntryStore;
import org.atlasapi.persistence.topic.TopicStore;
import org.junit.Before;
import org.junit.Ignore;
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
        simpleItem = getSimpleItem();
    }

    @Test
    public void testTransformItemWithBroadcastVersionsTransformsAllVersionFields()
            throws Exception {
        org.atlasapi.media.entity.Item complex = transformer.transform(simpleItem);

        assertThat(complex.getVersions().size(), is(1));

        Version version = complex.getVersions().iterator().next();
        checkRestriction(version.getRestriction());
    }

    @Test
    public void testTransformItemWithEventRefs() {
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
        return item;
    }

    private Item getItemWithEventRefs(){
        Item item = new Item();
        item.setUri("uri");
        item.setPublisher(new PublisherDetails(Publisher.BBC.key()));
        item.setId("12345");

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
}