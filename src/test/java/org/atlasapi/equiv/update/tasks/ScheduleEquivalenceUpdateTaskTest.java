package org.atlasapi.equiv.update.tasks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.atlasapi.application.v3.ApplicationConfiguration;
import org.atlasapi.equiv.update.EquivalenceUpdater;
import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Schedule;
import org.atlasapi.media.entity.Schedule.ScheduleChannel;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ScheduleResolver;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Iterables;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;


public class ScheduleEquivalenceUpdateTaskTest {

    @SuppressWarnings("unchecked") 
    private final EquivalenceUpdater<Content> updater = mock(EquivalenceUpdater.class);
    private final ContentResolver contentResolver = mock(ContentResolver.class);

    // TODO make this take multiple schedules
    private final ScheduleResolver scheduleResolver(final Schedule schedule) {
        return new ScheduleResolver() {
            
            @Override
            public Schedule schedule(DateTime from, DateTime to, Iterable<Channel> channels,
                    Iterable<Publisher> publisher, Optional<ApplicationConfiguration> mergeConfig) {
                return schedule;
            }
            
            @Override
            public Schedule schedule(DateTime from, int count, Iterable<Channel> channels,
                    Iterable<Publisher> publisher, Optional<ApplicationConfiguration> mergeConfig) {
                return schedule;
            }
            
            @Override
            public Schedule unmergedSchedule(DateTime from, DateTime to,
                    Iterable<Channel> channels, Iterable<Publisher> publisher) {
                return schedule;
            }
        };
    };
    
    @Test
    public void testUpdateScheduleEquivalences() {
        
        Channel bbcOne = new Channel(Publisher.METABROADCAST, "BBC One", "bbcone", false, MediaType.VIDEO, "bbconeuri");
        
        Item yvItemOne = new Item("yv1", "yv1c", Publisher.YOUVIEW);
        Version version = new Version();
        Broadcast broadcast = new Broadcast("bbcone", DateTime.now(), DateTime.now());
        version.addBroadcast(broadcast);
        yvItemOne.addVersion(version);

        Item yvItemTwo = new Item("yv2", "yv2c", Publisher.YOUVIEW);
        Version version1 = new Version();
        Broadcast broadcast1 = new Broadcast("bbcone", DateTime.now(), DateTime.now());
        version1.addBroadcast(broadcast1);
        yvItemTwo.addVersion(version1);

        Item yvItemSecondOne = new Item("yv1", "yv1c", Publisher.YOUVIEW);
        Version version2 = new Version();
        Broadcast broadcast2 = new Broadcast("bbcone", DateTime.now(), DateTime.now());
        version2.addBroadcast(broadcast2);
        yvItemSecondOne.addVersion(version2);
        
        DateTime now = new DateTime().withZone(DateTimeZone.UTC);
        
        ScheduleChannel schChannel1 = new ScheduleChannel(bbcOne, ImmutableList.of(yvItemOne, yvItemTwo, yvItemSecondOne));
        LocalDate today = new LocalDate();
        LocalDate tomorrow = today.plusDays(1);
        Schedule schedule1 = new Schedule(ImmutableList.of(schChannel1), new Interval(today.toDateTimeAtStartOfDay(), tomorrow.toDateTimeAtStartOfDay()));
        
        ScheduleResolver resolver = scheduleResolver(schedule1);
        
        ScheduleEquivalenceUpdateTask.builder()
            .withBack(0)
            .withForward(0)
            .withPublishers(ImmutableList.of(Publisher.YOUVIEW))
            .withChannelsSupplier(Suppliers.ofInstance((Iterable<Channel>)ImmutableList.of(bbcOne)))
            .withScheduleResolver(resolver)
            .withUpdater(updater)
            .build().run();
        

        verify(updater, times(2)).updateEquivalences(yvItemOne);
        verify(updater).updateEquivalences(yvItemTwo);
        verify(updater, times(2)).updateEquivalences(yvItemSecondOne);
        assertThat(Iterables.getOnlyElement(yvItemSecondOne.getVersions()).getBroadcasts().size(), is(2));
    }

}
