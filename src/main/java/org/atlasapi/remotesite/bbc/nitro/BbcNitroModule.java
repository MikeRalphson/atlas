package org.atlasapi.remotesite.bbc.nitro;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import javax.annotation.PostConstruct;

import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.channel.ChannelResolver;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.ScheduleEntry.ItemRefAndBroadcast;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ScheduleResolver;
import org.atlasapi.persistence.content.people.QueuingPersonWriter;
import org.atlasapi.persistence.content.schedule.mongo.ScheduleWriter;
import org.atlasapi.remotesite.bbc.ion.BbcIonServices;
import org.atlasapi.remotesite.channel4.pmlsd.epg.ScheduleResolverBroadcastTrimmer;
import org.atlasapi.util.GroupLock;

import com.metabroadcast.atlas.glycerin.Glycerin;
import com.metabroadcast.atlas.glycerin.XmlGlycerin;
import com.metabroadcast.atlas.glycerin.XmlGlycerin.Builder;
import com.metabroadcast.common.scheduling.RepetitionRules;
import com.metabroadcast.common.scheduling.ScheduledTask;
import com.metabroadcast.common.scheduling.SimpleScheduler;
import com.metabroadcast.common.time.SystemClock;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.joda.time.Duration;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BbcNitroModule {

    private @Value("${updaters.bbcnitro.enabled}") Boolean tasksEnabled;
    private @Value("${updaters.bbcnitro.offschedule.enabled}") Boolean offScheduleIngestEnabled;
    private @Value("${bbc.nitro.root}") String nitroRoot;
    private @Value("${bbc.nitro.apiKey}") String nitroApiKey;
    private @Value("${bbc.nitro.requestsPerSecond.today}") Integer nitroTodayRateLimit;
    private @Value("${bbc.nitro.requestsPerSecond.fortnight}") Integer nitroFortnightRateLimit;
    private @Value("${bbc.nitro.requestsPerSecond.threeweek}") Integer nitroThreeWeekRateLimit;
    private @Value("${bbc.nitro.requestsPerSecond.aroundtoday}") Integer nitroAroundTodayRateLimit;
    private @Value("${bbc.nitro.threadCount.today}") Integer nitroTodayThreadCount;
    private @Value("${bbc.nitro.threadCount.fortnight}") Integer nitroFortnightThreadCount;
    private @Value("${bbc.nitro.threadCount.threeweek}") Integer nitroThreeWeekThreadCount;
    private @Value("${bbc.nitro.threadCount.aroundtoday}") Integer nitroAroundTodayThreadCount;
    private @Value("${bbc.nitro.requestPageSize}") Integer nitroRequestPageSize;
    private @Value("${bbc.nitro.jobFailureThresholdPercent}") Integer jobFailureThresholdPercent;
    
    private @Autowired SimpleScheduler scheduler;
    private @Autowired ContentWriter contentWriter;
    private @Autowired ContentResolver contentResolver;
    private @Autowired ScheduleResolver scheduleResolver;
    private @Autowired ScheduleWriter scheduleWriter;
    private @Autowired ChannelResolver channelResolver;
    private @Autowired QueuingPersonWriter peopleWriter;
    
    private final ThreadFactory nitroThreadFactory
        = new ThreadFactoryBuilder().setNameFormat("nitro %s").build();
    private final GroupLock<String> pidLock = GroupLock.<String>natural();
    
    @PostConstruct
    public void configure() {
        if (tasksEnabled) {
            scheduler.schedule(nitroScheduleUpdateTask(7, 7, nitroFortnightThreadCount, nitroFortnightRateLimit, Optional.<Predicate<Item>>absent())
                .withName("Nitro 15 day updater"), RepetitionRules.every(Duration.standardHours(2)));
            scheduler.schedule(nitroScheduleUpdateTask(0, 0, nitroTodayThreadCount, nitroTodayRateLimit, Optional.<Predicate<Item>>absent())
                .withName("Nitro today updater"), RepetitionRules.every(Duration.standardMinutes(30)));
            scheduler.schedule(nitroScheduleUpdateTask(0, 0, nitroTodayThreadCount, nitroTodayRateLimit, Optional.of(Predicates.<Item>alwaysTrue()))
                    .withName("Nitro full fetch today updater"), RepetitionRules.NEVER);
            scheduler.schedule(nitroScheduleUpdateTask(30, -8, nitroThreeWeekThreadCount, nitroThreeWeekRateLimit, Optional.of(Predicates.<Item>alwaysTrue()))
                    .withName("Nitro full fetch -8 to -30 day updater"), RepetitionRules.every(Duration.standardHours(12)));
            scheduler.schedule(nitroScheduleUpdateTask(7, 3, nitroAroundTodayThreadCount, nitroAroundTodayRateLimit, Optional.of(Predicates.<Item>alwaysTrue()))
                    .withName("Nitro full fetch -7 to +3 day updater"), RepetitionRules.every(Duration.standardHours(2)));
        }
        if (offScheduleIngestEnabled) {
            scheduler.schedule(
                    nitroOffScheduleIngestTask().withName("Nitro off-schedule content updater"),
                    RepetitionRules.every(Duration.standardHours(3)));
        }
    }

    private ScheduledTask nitroScheduleUpdateTask(int back, int forward, Integer threadCount, Integer rateLimit, Optional<Predicate<Item>> fullFetchPermittedPredicate) {
        DayRangeChannelDaySupplier drcds = new DayRangeChannelDaySupplier(bbcChannelSupplier(), dayRangeSupplier(back, forward));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount, nitroThreadFactory);
        return new ChannelDayProcessingTask(executor, drcds, nitroChannelDayProcessor(rateLimit, fullFetchPermittedPredicate),
                null, jobFailureThresholdPercent);
    }

    private ScheduledTask nitroOffScheduleIngestTask() {
        Glycerin glycerin = glycerin(null);
        return new OffScheduleContentIngestTask(
                nitroContentAdapter(glycerin),
                nitroRequestPageSize,
                contentWriter(),
                pidLock,
                localOrRemoteNitroFetcher(
                    glycerin,
                    Optional.of(Predicates.<Item>alwaysTrue())
                )
        );
    }

    public ContentWriter contentWriter() {
        return new LastUpdatedSettingContentWriter(contentResolver, contentWriter);
    }

    @Bean
    PidUpdateController pidUpdateController() {
        Glycerin glycerin = glycerin(null);
        return new PidUpdateController(
                nitroContentAdapter(glycerin),
                contentWriter(),
                localOrRemoteNitroFetcher(
                        glycerin,
                        Optional.of(Predicates.<Item>alwaysTrue())
                )
        );
    }

    @Bean
    ScheduleDayUpdateController nitroScheduleUpdateController() {
        return new ScheduleDayUpdateController(channelResolver, 
                            nitroChannelDayProcessor(nitroTodayRateLimit, 
                            Optional.of(Predicates.<Item>alwaysTrue())));
    }

    ChannelDayProcessor nitroChannelDayProcessor(Integer rateLimit, Optional<Predicate<Item>> fullFetchPermitted) {
        ContentWriter contentWriter = contentWriter();
        ScheduleResolverBroadcastTrimmer scheduleTrimmer
            = new ScheduleResolverBroadcastTrimmer(Publisher.BBC_NITRO, scheduleResolver, contentResolver, contentWriter);
        Glycerin glycerin = glycerin(rateLimit);
        return new NitroScheduleDayUpdater(scheduleWriter, scheduleTrimmer, 
                nitroBroadcastHandler(glycerin, fullFetchPermitted, contentWriter), glycerin);
    }

    Glycerin glycerin(Integer rateLimit) {
        if (!tasksEnabled) {
            return UnconfiguredGlycerin.get();
        }

        Builder glycerin = XmlGlycerin.builder(nitroApiKey).withRootResource(nitroRoot);
        if (rateLimit != null) {
            glycerin.withLimiter(RateLimiter.create(rateLimit));
        }
        return glycerin.build();
    }

    NitroBroadcastHandler<ImmutableList<Optional<ItemRefAndBroadcast>>> nitroBroadcastHandler(Glycerin glycerin, 
            Optional<Predicate<Item>> fullFetchPermitted, ContentWriter contentWriter) {
        return new ContentUpdatingNitroBroadcastHandler(contentResolver, contentWriter,
                        localOrRemoteNitroFetcher(glycerin, fullFetchPermitted), pidLock);
    }
    
    LocalOrRemoteNitroFetcher localOrRemoteNitroFetcher(Glycerin glycerin, 
            Optional<Predicate<Item>> fullFetchPermitted) {
        if (fullFetchPermitted.isPresent()) {
            return new LocalOrRemoteNitroFetcher(contentResolver, nitroContentAdapter(glycerin), fullFetchPermitted.get());
        } else {
            return new LocalOrRemoteNitroFetcher(contentResolver, nitroContentAdapter(glycerin), new SystemClock());
        }
    }
    
    

    GlycerinNitroContentAdapter nitroContentAdapter(Glycerin glycerin) {
        SystemClock clock = new SystemClock();
        GlycerinNitroClipsAdapter clipsAdapter = new GlycerinNitroClipsAdapter(glycerin, clock, nitroRequestPageSize);
        return new GlycerinNitroContentAdapter(glycerin, clipsAdapter, peopleWriter, clock, nitroRequestPageSize);
    }

    private Supplier<Range<LocalDate>> dayRangeSupplier(int back, int forward) {
        return AroundTodayDayRangeSupplier.builder()
                .withDaysBack(back)
                .withDaysForward(forward)
                .build();
    }

    private Supplier<ImmutableSet<Channel>> bbcChannelSupplier() {
        return new Supplier<ImmutableSet<Channel>>() {
            //TODO: really need that alias for bbc services...
            @Override
            public ImmutableSet<Channel> get() {
                return ImmutableSet.copyOf(Iterables.transform(BbcIonServices.services.values(),
                    new Function<String, Channel>() {
                        @Override
                        public Channel apply(String input) {
                            return channelResolver.fromUri(input).requireValue();
                        }
                    }
                ));
            }
        };
    }
    
    
}
