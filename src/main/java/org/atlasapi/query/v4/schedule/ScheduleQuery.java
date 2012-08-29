package org.atlasapi.query.v4.schedule;

import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.output.Annotation;
import org.joda.time.Interval;

public final class ScheduleQuery {
    
    private final Publisher source;
    private final Channel channel;
    private final Interval interval;
    private final ApplicationConfiguration appConfig;
    private final Set<Annotation> annotations;
    
    public ScheduleQuery(Publisher source, Channel channel, Interval interval) {
        this(source, channel, interval, ApplicationConfiguration.DEFAULT_CONFIGURATION);
    }
    
    public ScheduleQuery(Publisher source, Channel channel, Interval interval, ApplicationConfiguration appConfig) {
        this(source, channel, interval, appConfig, Annotation.defaultAnnotations());
    }

    public ScheduleQuery(Publisher source, Channel channel, Interval interval, ApplicationConfiguration appConfig, Set<Annotation> annotations) {
        this.source = source;
        this.channel = channel;
        this.interval = interval;
        this.appConfig = appConfig;
        this.annotations = annotations;
    }

    public Publisher getPublisher() {
        return source;
    }

    public Channel getChannel() {
        return channel;
    }
    
    public Interval getInterval() {
        return interval;
    }
    
    public ApplicationConfiguration getApplicationConfiguration() {
        return appConfig;
    }
    
    public Set<Annotation> getAnnotations() {
        return annotations;
    }

}