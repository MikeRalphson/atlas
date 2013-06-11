package org.atlasapi.remotesite.pa;

import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.content.schedule.ScheduleHierarchy;
import org.atlasapi.remotesite.pa.listings.bindings.ProgData;
import org.joda.time.DateTimeZone;

import com.google.common.base.Optional;
import com.metabroadcast.common.time.Timestamp;

public interface PaProgDataProcessor {

    public Optional<ScheduleHierarchy> process(ProgData progData, Channel channel, DateTimeZone zone, Timestamp updatedAt);

}