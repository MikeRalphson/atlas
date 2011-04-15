package org.atlasapi.remotesite.pa;

import java.util.List;
import java.util.Set;

import org.atlasapi.genres.GenreMap;
import org.atlasapi.media.entity.Actor;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Channel;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Specialization;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.AdapterLogEntry;
import org.atlasapi.persistence.logging.AdapterLogEntry.Severity;
import org.atlasapi.remotesite.ContentWriters;
import org.atlasapi.remotesite.pa.bindings.Billing;
import org.atlasapi.remotesite.pa.bindings.CastMember;
import org.atlasapi.remotesite.pa.bindings.Category;
import org.atlasapi.remotesite.pa.bindings.PictureUsage;
import org.atlasapi.remotesite.pa.bindings.ProgData;
import org.atlasapi.remotesite.pa.bindings.StaffMember;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.internal.Sets;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.time.DateTimeZones;

public class PaProgrammeProcessor implements PaProgDataProcessor {
    
    private static final String PA_BASE_IMAGE_URL = "http://images.atlasapi.org/pa/";
    private static final String BROADCAST_ID_PREFIX = "pa:";
    private static final String YES = "yes";
    private static final String CLOSED_BRAND = "http://pressassociation.com/brands/8267";
    private static final String CLOSED_EPISODE = "http://pressassociation.com/episodes/closed";
    private static final String CLOSED_CURIE = "pa:closed";
    private static final List<String> IGNORED_BRANDS = ImmutableList.of("70214");
    
    private final ContentWriters contentWriter;
    private final ContentResolver contentResolver;
    private final AdapterLog log;
    private final PaChannelMap channelMap = new PaChannelMap();
    
    private final GenreMap genreMap = new PaGenreMap();
    
    private final Splitter personSplitter = Splitter.on(", ");
    private final PaPersonWriter personWriter;

    public PaProgrammeProcessor(ContentWriters contentWriter, ContentResolver contentResolver, AdapterLog log) {
        this.contentWriter = contentWriter;
        this.contentResolver = contentResolver;
        this.log = log;
        this.personWriter = new PaPersonWriter(contentWriter, contentResolver, log);
    }

    public void process(ProgData progData, Channel channel, DateTimeZone zone) {
        try {
            if (! Strings.isNullOrEmpty(progData.getSeriesId()) && IGNORED_BRANDS.contains(progData.getSeriesId())) {
                return;
            }
            
            Maybe<Brand> brand = getBrand(progData, channel);
            Maybe<Series> series = getSeries(progData, channel, brand.hasValue());
            Maybe<Episode> episode = isClosedBrand(brand) ? getClosedEpisode(brand.requireValue(), progData, channel, zone) : getEpisode(progData, channel, zone);

            if (episode.hasValue()) {
                if (series.hasValue()) {
                    series.requireValue().addContents(episode.requireValue());
                }
                if (brand.hasValue()) {
                    brand.requireValue().addOrReplace(episode.requireValue());
                }
                contentWriter.createOrUpdate(episode.requireValue());
                
                personWriter.createOrUpdatePeople(episode.requireValue());
            }
        } catch (Exception e) {
            log.record(new AdapterLogEntry(Severity.ERROR).withCause(e).withSource(PaProgrammeProcessor.class).withDescription(e.getMessage()));
        }
    }
    
    private boolean isClosedBrand(Maybe<Brand> brand) {
        return brand.hasValue() && CLOSED_BRAND.equals(brand.requireValue().getCanonicalUri());
    }
    
    private Maybe<Episode> getClosedEpisode(Brand brand, ProgData progData, Channel channel, DateTimeZone zone) {
        Identified resolvedContent = contentResolver.findByCanonicalUri(CLOSED_EPISODE);

        Episode episode;
        if (resolvedContent instanceof Episode) {
            episode = (Episode) resolvedContent;
        } else {
            episode = getBasicEpisode(progData, Specialization.TV);
        }
        episode.setCanonicalUri(CLOSED_EPISODE);
        episode.setCurie(CLOSED_CURIE);
        episode.setTitle(progData.getTitle());
        
        Version version = findBestVersion(episode.getVersions());

        Broadcast broadcast = broadcast(progData, channel, zone);
        addBroadcast(version, broadcast);

        return Maybe.just(episode);
    }
    
    private Maybe<Brand> getBrand(ProgData progData, Channel channel) {
        String brandId = progData.getSeriesId();
        if (Strings.isNullOrEmpty(brandId) || Strings.isNullOrEmpty(brandId.trim())) {
            return Maybe.nothing();
        }

        String brandUri = PaHelper.getBrandUri(brandId);
        Brand brand = new Brand(brandUri, "pa:b-" + brandId, Publisher.PA);
        
        brand.setTitle(progData.getTitle());
        brand.setSpecialization(specialization(progData, channel));
        brand.setGenres(genreMap.map(ImmutableSet.copyOf(Iterables.transform(progData.getCategory(), Category.TO_GENRE_URIS))));

        if (progData.getPictures() != null) {
            for (PictureUsage picture : progData.getPictures().getPictureUsage()) {
                if (picture.getType().equals("season") && brand.getImage() == null){
                    brand.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                }
                if (picture.getType().equals("series")){
                    brand.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                    break;
                }
            }
        }

        return Maybe.just(brand);
    }

    private Maybe<Series> getSeries(ProgData progData, Channel channel, boolean hasBrand) {
        if (Strings.isNullOrEmpty(progData.getSeriesNumber()) || Strings.isNullOrEmpty(progData.getSeriesId())) {
            return Maybe.nothing();
        }
        
        String seriesUri = PaHelper.getSeriesUri(progData.getSeriesId(), progData.getSeriesNumber());
        
        Series series = null;
        if (! hasBrand) {
            Identified resolvedContent = contentResolver.findByCanonicalUri(seriesUri);
            if (resolvedContent instanceof Series) {
                series = (Series) resolvedContent;
            } 
        }
        if (series == null) {
            series = new Series(seriesUri, "pa:s-" + progData.getSeriesId() + "-" + progData.getSeriesNumber(), Publisher.PA);
        }
        
        series.setPublisher(Publisher.PA);
        series.setSpecialization(specialization(progData, channel));
        series.setGenres(genreMap.map(ImmutableSet.copyOf(Iterables.transform(progData.getCategory(), Category.TO_GENRE_URIS))));
        
        if (progData.getPictures() != null) {
            for (PictureUsage picture : progData.getPictures().getPictureUsage()) {
                if (picture.getType().equals("series") && series.getImage() == null){
                    series.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                }
                if (picture.getType().equals("season")){
                    series.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                    break;
                }
            }
        }

        return Maybe.just(series);
    }

    private Maybe<Episode> getEpisode(ProgData progData, Channel channel, DateTimeZone zone) {
        Specialization specialization = specialization(progData, channel);
        
        String episodeUri = specialization == Specialization.FILM
                ? PaHelper.getFilmUri(programmeId(progData))
                : PaHelper.getEpisodeUri(programmeId(progData));
        Identified resolvedContent = contentResolver.findByCanonicalUri(episodeUri);

        Episode episode;
        if (resolvedContent instanceof Episode) {
            episode = (Episode) resolvedContent;
        } else {
            episode = getBasicEpisode(progData, specialization);
        }
        
        if (progData.getEpisodeTitle() != null) {
            episode.setTitle(progData.getEpisodeTitle());
        } else {
            episode.setTitle(progData.getTitle());
        }

        try {
            if (progData.getEpisodeNumber() != null) {
                episode.setEpisodeNumber(Integer.valueOf(progData.getEpisodeNumber()));
            }
            if (progData.getSeriesNumber() != null) {
                episode.setSeriesNumber(Integer.valueOf(progData.getSeriesNumber()));
            }
        } catch (NumberFormatException e) {
            // sometimes we don't get valid numbers
        }

        if (progData.getBillings() != null) {
            for (Billing billing : progData.getBillings().getBilling()) {
                if (billing.getType().equals("synopsis")) {
                    episode.setDescription(billing.getvalue());
                    break;
                }
            }
        }
        
        episode.setMediaType(channelMap.isRadioChannel(channel) ? MediaType.AUDIO : MediaType.VIDEO);
        
        episode.setSpecialization(specialization);
        episode.setGenres(genreMap.map(ImmutableSet.copyOf(Iterables.transform(progData.getCategory(), Category.TO_GENRE_URIS))));

        if (progData.getPictures() != null) {
            for (PictureUsage picture : progData.getPictures().getPictureUsage()) {
                if (picture.getType().equals("series") && episode.getImage() == null){
                    episode.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                }
                if (picture.getType().equals("season") && episode.getImage() == null){
                    episode.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                }
                if (picture.getType().equals("episode")){
                    episode.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
                    break;
                }
            }
        }
        
        episode.setPeople(people(progData));

        Version version = findBestVersion(episode.getVersions());

        Broadcast broadcast = broadcast(progData, channel, zone);
        addBroadcast(version, broadcast);

        return Maybe.just(episode);
    }

    private Broadcast broadcast(ProgData progData, Channel channel, DateTimeZone zone) {
        Duration duration = Duration.standardMinutes(Long.valueOf(progData.getDuration()));

        DateTime transmissionTime = getTransmissionTime(progData.getDate(), progData.getTime(), zone);
        
        Broadcast broadcast = new Broadcast(channel.uri(), transmissionTime, duration).withId(BROADCAST_ID_PREFIX+progData.getShowingId());
        broadcast.setLastUpdated(new DateTime(DateTimeZones.UTC));
        
        if (progData.getAttr() != null) {
            broadcast.setRepeat(getBooleanValue(progData.getAttr().getRepeat()));
        }
        
        return broadcast;
    }
    
    private void addBroadcast(Version version, Broadcast broadcast) {
        if (! Strings.isNullOrEmpty(broadcast.getId())) {
            Set<Broadcast> broadcasts = Sets.newHashSet();
            Maybe<Interval> broadcastInterval = broadcast.transmissionInterval();
            
            for (Broadcast currentBroadcast: version.getBroadcasts()) {
                // I know this is ugly, but it's easier to read.
                if (Strings.isNullOrEmpty(currentBroadcast.getId())) {
                    continue;
                }
                if (broadcast.getId().equals(currentBroadcast.getId())) {
                    continue;
                }
                if (currentBroadcast.transmissionInterval().hasValue() && broadcastInterval.hasValue()) {
                    Interval currentInterval = currentBroadcast.transmissionInterval().requireValue();
                    if (currentBroadcast.getBroadcastOn().equals(broadcast.getBroadcastOn()) && currentInterval.overlaps(broadcastInterval.requireValue())) {
                        continue;
                    }
                }
                broadcasts.add(currentBroadcast);
            }
            broadcasts.add(broadcast);
            
            version.setBroadcasts(broadcasts);
        }
    }
    
    private List<CrewMember> people(ProgData progData) {
        List<CrewMember> people = Lists.newArrayList();
        
        for (CastMember cast: progData.getCastMember()) {
            Actor actor = Actor.actor(cast.getActor(), cast.getCharacter(), Publisher.PA);
            if (! people.contains(actor)) {
                people.add(actor);
            }
        }
        
        for (StaffMember staff: progData.getStaffMember()) {
            String roleKey = staff.getRole().toLowerCase().replace(' ', '_');
            for (String name: personSplitter.split(staff.getPerson())) {
                CrewMember crewMember = CrewMember.crewMember(name, roleKey, Publisher.PA);
                if (! people.contains(crewMember)) {
                    people.add(crewMember);
                }
            }
        }
        
        return people;
    }

    private Version findBestVersion(Iterable<Version> versions) {
        for (Version version : versions) {
            if (version.getProvider() == Publisher.PA) {
                return version;
            }
        }

        return versions.iterator().next();
    }

    private Episode getBasicEpisode(ProgData progData, Specialization specialization) {
        Episode episode = specialization == Specialization.FILM 
                       ? new Episode(PaHelper.getFilmUri(programmeId(progData)), PaHelper.getFilmCurie(programmeId(progData)), Publisher.PA)
                       : new Episode(PaHelper.getEpisodeUri(programmeId(progData)), "pa:e-" + programmeId(progData), Publisher.PA);

        Version version = new Version();
        version.setProvider(Publisher.PA);
        episode.addVersion(version);

        Duration duration = Duration.standardMinutes(Long.valueOf(progData.getDuration()));
        version.setDuration(duration);

        episode.addVersion(version);

        return episode;
    }
    
    protected Specialization specialization(ProgData progData, Channel channel) {
        if (channelMap.isRadioChannel(channel)) {
            return Specialization.RADIO;
        }
        return YES.equals(progData.getAttr().getFilm()) ? Specialization.FILM : Specialization.TV;
    }

    protected static DateTime getTransmissionTime(String date, String time, DateTimeZone zone) {
        String dateString = date + "-" + time;
        return DateTimeFormat.forPattern("dd/MM/yyyy-HH:mm").withZone(zone).parseDateTime(dateString);
    }
    
    protected static String programmeId(ProgData progData) {
        return ! Strings.isNullOrEmpty(progData.getRtFilmnumber()) ? progData.getRtFilmnumber() : progData.getProgId();
    }
    
    private static Boolean getBooleanValue(String value) {
        if (value != null) {
            return value.equalsIgnoreCase(YES);
        }
        return null;
    }
}
