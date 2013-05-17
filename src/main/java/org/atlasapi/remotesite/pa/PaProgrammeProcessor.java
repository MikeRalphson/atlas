package org.atlasapi.remotesite.pa;

import static org.atlasapi.persistence.logging.AdapterLogEntry.errorEntry;
import static org.atlasapi.persistence.logging.AdapterLogEntry.warnEntry;
import static org.atlasapi.remotesite.pa.PaHelper.getBrandUri;
import static org.atlasapi.remotesite.pa.PaHelper.getEpisodeCurie;
import static org.atlasapi.remotesite.pa.PaHelper.getEpisodeUri;
import static org.atlasapi.remotesite.pa.PaHelper.getFilmCurie;
import static org.atlasapi.remotesite.pa.PaHelper.getFilmUri;

import java.util.List;
import java.util.Set;

import org.atlasapi.genres.GenreMap;
import org.atlasapi.media.channel.Channel;
import org.atlasapi.persistence.media.channel.ChannelResolver;
import org.atlasapi.media.entity.Actor;
import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Certificate;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Image;
import org.atlasapi.media.entity.ImageAspectRatio;
import org.atlasapi.media.entity.ImageType;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.CrewMember.Role;
import org.atlasapi.media.entity.ScheduleEntry.ItemRefAndBroadcast;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Specialization;
import org.atlasapi.media.entity.Version;
import org.atlasapi.media.util.ItemAndBroadcast;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.content.people.ItemsPeopleWriter;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.AdapterLogEntry;
import org.atlasapi.persistence.logging.AdapterLogEntry.Severity;
import org.atlasapi.remotesite.pa.listings.bindings.Attr;
import org.atlasapi.remotesite.pa.listings.bindings.Billing;
import org.atlasapi.remotesite.pa.listings.bindings.CastMember;
import org.atlasapi.remotesite.pa.listings.bindings.Category;
import org.atlasapi.remotesite.pa.listings.bindings.Person;
import org.atlasapi.remotesite.pa.listings.bindings.PictureUsage;
import org.atlasapi.remotesite.pa.listings.bindings.Pictures;
import org.atlasapi.remotesite.pa.listings.bindings.ProgData;
import org.atlasapi.remotesite.pa.listings.bindings.StaffMember;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.Interval;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.intl.Countries;
import com.metabroadcast.common.media.MimeType;
import com.metabroadcast.common.text.MoreStrings;
import com.metabroadcast.common.time.Timestamp;

public class PaProgrammeProcessor implements PaProgDataProcessor {
    
    static final String PA_PICTURE_TYPE_EPISODE = "episode";
    static final String PA_PICTURE_TYPE_BRAND   = "series";  // Counter-intuitively PA use 'series' where we use 'brand'
    static final String PA_PICTURE_TYPE_SERIES  = "season";  // .. and 'season' where we use 'series'
    
    static final String PA_BASE_IMAGE_URL = "http://images.atlasapi.org/pa/";
    static final String NEW_IMAGE_BASE_IMAGE_URL = "http://images.atlas.metabroadcast.com/pressassociation.com/";
    public static final String BROADCAST_ID_PREFIX = "pa:";
    
    private static final DateTimeFormatter PA_DATE_FORMAT = DateTimeFormat.forPattern("dd/MM/yyyy");
    
    private static final String YES = "yes";
    private static final String CLOSED_BRAND = getBrandUri("8267");
    private static final String CLOSED_EPISODE_URI_PREFIX = getEpisodeUri("closed");
    private static final String CLOSED_CURIE_PREFIX = "pa:closed";
    
    public static final String TBC_FILM_BRAND_URI = getBrandUri("84575");
    public static final String TBC_EPISODE_BRAND_URI = getBrandUri("70214");
    public static final String TBC_FILM_URI_PREFIX = getFilmUri("tbc");
    public static final String TBC_EPISODE_URI_PREFIX = getEpisodeUri("tbc");
    public static final String TBC_FILM_CURIE_PREFIX = getFilmCurie("tbc");
    public static final String TBC_EPISODE_CURIE_PREFIX = getEpisodeCurie("tbc");
    
    private final ContentWriter contentWriter;
    private final ContentResolver contentResolver;
    private final ChannelResolver channelResolver;
    private final AdapterLog log;
    private final PaCountryMap countryMap = new PaCountryMap();
    
    private final GenreMap genreMap = new PaGenreMap();
    
    private final ItemsPeopleWriter personWriter;
	private final ImmutableList<Channel> terrestrialChannels;

    public PaProgrammeProcessor(ContentWriter contentWriter, ContentResolver contentResolver, ChannelResolver channelResolver, ItemsPeopleWriter itemsPeopleWriter, AdapterLog log) {
        this.contentWriter = contentWriter;
        this.contentResolver = contentResolver;
        this.log = log;
        this.personWriter = itemsPeopleWriter;
        this.channelResolver = channelResolver;
        this.terrestrialChannels = ImmutableList.<Channel>builder()
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/east").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/london").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbchd").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/west_midlands").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/east_midlands").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/yorkshire").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/north_east").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/north_west").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/ni").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/wales").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/scotland").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/south").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/south_west").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/west").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcone/south_east").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbctwo/england").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbctwo/ni").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbctwo/scotland").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbctwo/wales").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/cbbc").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/cbeebies").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/anglia").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/bordersouth").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/london").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/carltoncentral").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/channel").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/granada").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/meridian").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/tynetees").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/hd").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv2/hd").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv3/hd").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv4/hd").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/ytv").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/carltonwestcountry").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/wales").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/west").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/stvcentral").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/ulster").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv1/bordernorth").requireValue())
        		.add(channelResolver.fromUri("http://www.channel4.com").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/s4c").requireValue())
        		.add(channelResolver.fromUri("http://www.five.tv").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio1/england").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio2").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio3").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio4/fm").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio7").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/radio4/lw").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/5live").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/5livesportsextra").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/6music").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/1xtra").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/asiannetwork").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/worldservice").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcthree").requireValue())
        		.add(channelResolver.fromUri("http://www.bbc.co.uk/services/bbcfour").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv2").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv3").requireValue())
        		.add(channelResolver.fromUri("http://www.itv.com/channels/itv4").requireValue())
        		.add(channelResolver.fromUri("http://www.five.tv/channels/five_hd").requireValue())
        		.add(channelResolver.fromUri("http://www.five.tv/channels/five-usa").requireValue())
        		.add(channelResolver.fromUri("http://www.e4.com/hd").requireValue())
        		.add(channelResolver.fromUri("http://www.e4.com").requireValue())
        		.add(channelResolver.fromUri("http://www.channel4.com/more4").requireValue())
        		.add(channelResolver.fromUri("http://film4.com").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/stvhd").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/channel4hd").requireValue())
        		.add(channelResolver.fromUri("http://ref.atlasapi.org/channels/film4hd").requireValue())
        		.build();
    }

    @Override
    public ItemRefAndBroadcast process(ProgData progData, Channel channel, DateTimeZone zone, Timestamp updatedAt) {
        try {
            if (Strings.isNullOrEmpty(progData.getSeriesId())) {
                return null;
            }
            
            Maybe<Brand> possibleBrand = getBrand(progData, channel);
            if (possibleBrand.hasValue()) {
                Brand brand = possibleBrand.requireValue();
                if (isClosedBrand(possibleBrand)) {
                    brand.setScheduleOnly(true);
                }
                brand.setLastUpdated(updatedAt.toDateTimeUTC());
            	contentWriter.createOrUpdate(brand);
            }
            
            Maybe<Series> series = getSeries(progData, channel, possibleBrand.hasValue());
            if (series.hasValue()) {
            	if (possibleBrand.hasValue()) {
            		series.requireValue().setParent(possibleBrand.requireValue());
            	}
            	series.requireValue().setLastUpdated(updatedAt.toDateTimeUTC());
            	contentWriter.createOrUpdate(series.requireValue());
            }
            
            
            Maybe<ItemAndBroadcast> itemAndBroadcast;
            
            if (isClosedBrand(possibleBrand)) {
                itemAndBroadcast = getClosedEpisode(possibleBrand.requireValue(), progData, channel, zone, updatedAt);
            } else if (isTbcFilm(possibleBrand)) {
                itemAndBroadcast = getTbcFilm(possibleBrand.requireValue(), progData, channel, zone, updatedAt);
            } else if (isTbcEpisode(possibleBrand)) {
                itemAndBroadcast = getTbcEpisode(possibleBrand.requireValue(), progData, channel, zone, updatedAt);
            }
            else {
                itemAndBroadcast = getFilmOrEpisode(progData, channel, zone, possibleBrand.hasValue() || series.hasValue(), updatedAt);
            }

            if (itemAndBroadcast.hasValue()) {
            	Item item = itemAndBroadcast.requireValue().getItem();
                if (series.hasValue() && item instanceof Episode) {
                	Episode episode = (Episode) item;
                    episode.setSeries(series.requireValue());
                }
                if (possibleBrand.hasValue()) {
                	item.setContainer(possibleBrand.requireValue());
                } else if (series.hasValue()) {
                	item.setContainer(series.requireValue());
                }
                item.setLastUpdated(updatedAt.toDateTimeUTC());
                contentWriter.createOrUpdate(item);
                personWriter.createOrUpdatePeople(item);
            }
            return new ItemRefAndBroadcast(itemAndBroadcast.requireValue().getItem(), itemAndBroadcast.requireValue().getBroadcast().requireValue());
        } catch (Exception e) {
        	e.printStackTrace();
        	log.record(new AdapterLogEntry(Severity.ERROR).withCause(e).withSource(PaProgrammeProcessor.class).withDescription(e.getMessage()));
        }
        return null;
    }
    
    private boolean isClosedBrand(Maybe<Brand> brand) {
        return brand.hasValue() && CLOSED_BRAND.equals(brand.requireValue().getCanonicalUri());
    }
    
    private boolean isTbcFilm(Maybe<Brand> brand) {
        return brand.hasValue() && TBC_FILM_BRAND_URI.equals(brand.requireValue().getCanonicalUri());
    }
    
    private boolean isTbcEpisode(Maybe<Brand> brand) {
        return brand.hasValue() && TBC_EPISODE_BRAND_URI.equals(brand.requireValue().getCanonicalUri());
    }
    
    private Maybe<ItemAndBroadcast> getClosedEpisode(Brand brand, ProgData progData, Channel channel, DateTimeZone zone, Timestamp updatedAt) {
        String uri = CLOSED_EPISODE_URI_PREFIX+getClosedOrTbcPostfix(channel);
        Maybe<Identified> resolvedContent = contentResolver.findByCanonicalUris(ImmutableList.of(uri)).getFirstValue();

        Episode episode;
        if (resolvedContent.hasValue() && resolvedContent.requireValue() instanceof Episode) {
            episode = (Episode) resolvedContent.requireValue();
        } else {
            episode = (Episode) getBasicEpisode(progData, true);
        }
        
        return Maybe.just(getClosedOrTbcItemAndBroadcast(episode, uri, CLOSED_CURIE_PREFIX, channel, progData, zone, updatedAt));
    }
    
    private Maybe<ItemAndBroadcast> getTbcEpisode(Brand brand, ProgData progData, Channel channel, DateTimeZone timeZone, Timestamp updatedAt) {
        
        String uri = TBC_EPISODE_URI_PREFIX + getClosedOrTbcPostfix(channel);
        Maybe<Identified> resolvedContent = contentResolver.findByCanonicalUris(ImmutableList.of(uri)).getFirstValue();
        
        Episode episode;
        if (resolvedContent.hasValue() && resolvedContent.requireValue() instanceof Episode) {
            episode = (Episode) resolvedContent.requireValue();
        } else {
            episode = (Episode) getBasicEpisode(progData, true);
        }
        
        return Maybe.just(getClosedOrTbcItemAndBroadcast(episode, uri, TBC_EPISODE_CURIE_PREFIX, channel, progData, timeZone, updatedAt));
    }
    
    private Maybe<ItemAndBroadcast> getTbcFilm(Brand brand, ProgData progData, Channel channel, DateTimeZone timeZone, Timestamp updatedAt) {
        String uri = TBC_FILM_URI_PREFIX + getClosedOrTbcPostfix(channel);
        Maybe<Identified> resolvedContent = contentResolver.findByCanonicalUris(ImmutableList.of(uri)).getFirstValue();
        
        Film episode;
        if (resolvedContent.hasValue() && resolvedContent.requireValue() instanceof Film) {
            episode = (Film) resolvedContent.requireValue();
        } else {
            episode = getBasicFilm(progData);
        }
        
        ItemAndBroadcast itemAndBroadcast = getClosedOrTbcItemAndBroadcast(episode, uri, TBC_FILM_CURIE_PREFIX, channel, progData, timeZone, updatedAt);
        
        return Maybe.just(itemAndBroadcast);
    }
    
    private ItemAndBroadcast getClosedOrTbcItemAndBroadcast(Item item, String uri, String curiePrefix, Channel channel, ProgData progData, DateTimeZone timeZone, Timestamp updatedAt) {
        item.setCanonicalUri(uri);
        item.setCurie(curiePrefix+getClosedOrTbcPostfix(channel));
        item.setTitle(progData.getTitle());
        item.setScheduleOnly(true);
        item.setMediaType(channel.mediaType());
        
        Version version = findBestVersion(item.getVersions());

        Broadcast broadcast = broadcast(progData, channel, timeZone, updatedAt);
        addBroadcast(version, broadcast);

        return new ItemAndBroadcast(item, Maybe.just(broadcast));
    }
    
    private String getClosedOrTbcPostfix(Channel channel) {
        return "_"+channel.key();
    }
    
    private Maybe<Brand> getBrand(ProgData progData, Channel channel) {
        String brandId = progData.getSeriesId();
        if (Strings.isNullOrEmpty(brandId) || Strings.isNullOrEmpty(brandId.trim())) {
            return Maybe.nothing();
        }

        String brandUri = PaHelper.getBrandUri(brandId);
        Alias brandAlias = PaHelper.getBrandAlias(brandId);
        
        Maybe<Identified> possiblePrevious = contentResolver.findByCanonicalUris(ImmutableList.of(brandUri)).getFirstValue();
        
        Brand brand = possiblePrevious.hasValue() ? (Brand) possiblePrevious.requireValue() : new Brand(brandUri, PaHelper.getBrandCurie(brandId), Publisher.PA);
        
        brand.addAlias(brandAlias);
        brand.setTitle(progData.getTitle());
        brand.setDescription(progData.getSeriesSynopsis());
        brand.setSpecialization(specialization(progData, channel));
        setCertificate(progData, brand);
        setGenres(progData, brand);

        selectImages(progData.getPictures(), brand, PA_PICTURE_TYPE_BRAND, PA_PICTURE_TYPE_SERIES, Maybe.<String>nothing());

        return Maybe.just(brand);
    }

    /**
     * If pictures is not null, add a list of images of the given primary type to the described object.
     * If no images of the primary type are found, fall back to the first, or optional second type. Only
     * one (the first) fallback image will be used. Ordering is preserved for images. If pictures is null,
     * this method does nothing.
     * @param pictures The picture collection to use
     * @param described The object that will have images added
     * @param primaryImageType The primary image type to add
     * @param firstFallbackType The preferred fallback image type to add
     * @param secondFallbackType The optional fallback image type to add
     */
    void selectImages(Pictures pictures, Described described, String primaryImageType, String firstFallbackType, Maybe<String> secondFallbackType) {
        if (pictures != null) {
            Set<Image> images = Sets.newLinkedHashSet();
            Image fallbackImage = null;
            boolean hasFirstFallbackType = false;
            
            for (PictureUsage picture : pictures.getPictureUsage()) {
                Image image = createImage(picture);
                
                if (secondFallbackType.hasValue() && 
                    picture.getType().equals(secondFallbackType.requireValue()) && 
                    images.isEmpty() && 
                    fallbackImage == null) {
                    setPrimaryImage(described, image, picture);
                    fallbackImage = image;
                }
                if (picture.getType().equals(firstFallbackType) && images.isEmpty() && !hasFirstFallbackType) {
                    setPrimaryImage(described, image, picture);
                    fallbackImage = image;
                    hasFirstFallbackType = true;
                }
                if (picture.getType().equals(primaryImageType)) {
                    if (images.size() == 0) {
                        setPrimaryImage(described, image, picture);
                    }
                    images.add(image);
                }
            }
            
            if (!images.isEmpty()) {
                described.setImages(images);
            } else if (fallbackImage != null) {
                described.setImages(ImmutableSet.of(fallbackImage));
            }
        }
    }
    
    private void setPrimaryImage(Described described, Image image, PictureUsage picture) {
        image.setType(ImageType.PRIMARY);
        // The image URL is set to the "legacy" URL of http://images.../pa/image.jpg since there
        // are external dependencies on it. The new image block moves to the new URL scheme of
        // http://images.../pressassociation.com/image.jpg
        described.setImage(PA_BASE_IMAGE_URL + picture.getvalue());
    }
    
    private Image createImage(PictureUsage pictureUsage) {
        String imageUri = PA_BASE_IMAGE_URL + pictureUsage.getvalue();
        Image image = new Image(imageUri);
        
        image.setHeight(360);
        image.setWidth(640);
        image.setType(ImageType.ADDITIONAL); 
        image.setAspectRatio(ImageAspectRatio.SIXTEEN_BY_NINE);
        image.setMimeType(MimeType.IMAGE_JPG);
        image.setCanonicalUri(NEW_IMAGE_BASE_IMAGE_URL + pictureUsage.getvalue());
        image.setAvailabilityStart(fromPaDate(pictureUsage.getStartDate()));
        DateTime expiry = fromPaDate(pictureUsage.getExpiryDate());
        if (expiry != null) {
            image.setAvailabilityEnd(expiry.plusDays(1));
        } else {
            image.setAvailabilityEnd(null);
        }
        return image;
    }
    

    private Maybe<Series> getSeries(ProgData progData, Channel channel, boolean hasBrand) {
        if (Strings.isNullOrEmpty(progData.getSeriesNumber()) || Strings.isNullOrEmpty(progData.getSeriesId())) {
            return Maybe.nothing();
        }
        String seriesUri = PaHelper.getSeriesUri(progData.getSeriesId(), progData.getSeriesNumber());
        Alias seriesAlias = PaHelper.getSeriesAlias(progData.getSeriesId(), progData.getSeriesNumber());
        
        
        Maybe<Identified> possiblePrevious = contentResolver.findByCanonicalUris(ImmutableList.of(seriesUri)).getFirstValue();
        
        Series series = possiblePrevious.hasValue() ? (Series) possiblePrevious.requireValue() : new Series(seriesUri, PaHelper.getSeriesCurie(progData.getSeriesId(), progData.getSeriesNumber()), Publisher.PA);
        
        series.addAlias(seriesAlias);
        
        if(progData.getEpisodeTotal() != null && progData.getEpisodeTotal().trim().length() > 0) {
            try {
                series.setTotalEpisodes(Integer.parseInt(progData.getEpisodeTotal().trim()));
            } catch (NumberFormatException e) {
                log.record(warnEntry().withCause(e).withSource(getClass()).withDescription("Couldn't parse episode_total %s", progData.getEpisodeTotal().trim()));
            }
        }
        
        if(progData.getSeriesNumber() != null && progData.getSeriesNumber().trim().length() > 0) {
            try {
                series.withSeriesNumber(Integer.parseInt(progData.getSeriesNumber().trim()));
            } catch (NumberFormatException e) {
                log.record(warnEntry().withCause(e).withSource(getClass()).withDescription("Couldn't parse series_number %s", progData.getSeriesNumber().trim()));
            }
        }
    
        series.setPublisher(Publisher.PA);
        series.setSpecialization(specialization(progData, channel));
        setCertificate(progData, series);
        setGenres(progData, series);
        
        selectImages(progData.getPictures(), series, PA_PICTURE_TYPE_SERIES, PA_PICTURE_TYPE_BRAND, Maybe.<String>nothing());
        
        return Maybe.just(series);
    }
    
    private DateTime fromPaDate(String paDate) {
        if (Strings.isNullOrEmpty(paDate)) {
            return null;
        }
        return PA_DATE_FORMAT.parseDateTime(paDate).withZone(DateTimeZone.UTC);
    }
    
    private Maybe<ItemAndBroadcast> getFilmOrEpisode(ProgData progData, Channel channel, DateTimeZone zone, boolean isEpisode, Timestamp updatedAt) {
        return specialization(progData, channel) == Specialization.FILM ? getFilm(progData, channel, zone, updatedAt) : getEpisode(progData, channel, zone, isEpisode, updatedAt);
    }
    
    private Maybe<ItemAndBroadcast> getFilm(ProgData progData, Channel channel, DateTimeZone zone, Timestamp updatedAt) {
        String rtFilmAlias = PaHelper.getFilmRtAlias(progData.getRtFilmnumber());
        String filmUri = PaHelper.getFilmUri(programmeId(progData));
        ResolvedContent possiblePreviousData = contentResolver.findByCanonicalUris(ImmutableList.of(rtFilmAlias, filmUri));
        
        Film film;
        Maybe<Identified> oldFilm = possiblePreviousData.get(rtFilmAlias);
        Maybe<Identified> newFilm = possiblePreviousData.get(filmUri);
        if (oldFilm.hasValue()) {
        	Identified previous = oldFilm.requireValue();
            if (previous instanceof Film) {
                film = (Film) previous;
            }
            else {
                film = new Film();
                Item.copyTo((Episode) previous, film);
            }
            film.addAliasUrl(filmUri);
        } else if (newFilm.hasValue()) {
            Identified previous = newFilm.requireValue();
            if (previous instanceof Film) {
                film = (Film) previous;
            }
            else {
                film = new Film();
                Item.copyTo((Episode) previous, film);
            }
            film.addAliasUrl(rtFilmAlias);
        } else {
            film = getBasicFilm(progData);
        }

        String certificateValue = progData.getCertificate();
        if (!Strings.isNullOrEmpty(certificateValue)) {
            Certificate certificate = new Certificate(certificateValue, Countries.GB);
            film.setCertificates(ImmutableSet.of(certificate));
        }
        film.addAlias(PaHelper.getFilmAlias(programmeId(progData)));
        
        Broadcast broadcast = setCommonDetails(progData, channel, zone, film, updatedAt);
        
        if (progData.getFilmYear() != null && MoreStrings.containsOnlyAsciiDigits(progData.getFilmYear())) {
            film.setYear(Integer.parseInt(progData.getFilmYear()));
        }
        
        return Maybe.just(new ItemAndBroadcast(film, Maybe.just(broadcast)));
    }
    
    private Broadcast setCommonDetails(ProgData progData, Channel channel, DateTimeZone zone, Item episode, Timestamp updatedAt) {
        
        //currently Welsh channels have Welsh titles/descriptions 
        // which flip the English ones, resulting in many writes. We'll only take the Welsh title if we don't
    	// already have a title from another channel
        if (episode.getTitle() == null || !channel.uri().contains("wales")) {
            if (progData.getEpisodeTitle() != null) {
                episode.setTitle(progData.getEpisodeTitle());
            } else {
                episode.setTitle(progData.getTitle());
            }
        }

        if (progData.getBillings() != null) {
            for (Billing billing : progData.getBillings().getBilling()) {
                if((episode.getDescription() == null || !channel.uri().contains("wales")) 
                        && billing.getType().equals("synopsis")) {
                    episode.setDescription(billing.getvalue());
                }
                if ((episode.getShortDescription() == null || !channel.uri().contains("wales"))
                        && billing.getType().equals("pa_detail1")) {
                    episode.setShortDescription(billing.getvalue());
                }
                if ((episode.getMediumDescription() == null || !channel.uri().contains("wales"))
                        && billing.getType().equals("pa_detail2")) {
                    episode.setMediumDescription(billing.getvalue());
                }
                if ((episode.getLongDescription() == null || !channel.uri().contains("wales"))
                        && billing.getType().equals("pa_detail3")) {
                    episode.setLongDescription(billing.getvalue());
                }
            }
        }

        episode.setMediaType(channel.mediaType());
        episode.setSpecialization(specialization(progData, channel));
        setGenres(progData, episode);
        
        if (progData.getCountry() != null) {
            episode.setCountriesOfOrigin(countryMap.parseCountries(progData.getCountry()));
        }
        
        if (progData.getAttr() != null) {
            episode.setBlackAndWhite(getBooleanValue(progData.getAttr().getBw()));
        }

        selectImages(progData.getPictures(), episode, PA_PICTURE_TYPE_EPISODE, PA_PICTURE_TYPE_SERIES, Maybe.just(PA_PICTURE_TYPE_BRAND));
        
        episode.setPeople(people(progData, episode.getPeople()));

        Version version = findBestVersion(episode.getVersions());

        version.set3d(getBooleanValue(progData.getAttr().getThreeD()));
        Duration duration = Duration.standardMinutes(Long.valueOf(progData.getDuration()));
        version.setDuration(duration);
        setCertificate(progData, episode);

        Broadcast broadcast = broadcast(progData, channel, zone, updatedAt);
        addBroadcast(version, broadcast);
        return broadcast;
    }

    public static Function<Category, String> TO_GENRE_URIS = new Function<Category, String>() {
        @Override
        public String apply(Category from) {
            return "http://pressassociation.com/genres/" + from.getCategoryCode();
        }
    };

    private void setCertificate(ProgData progData, Content content) {
        if (progData.getCertificate() != null) {
            content.setCertificates(ImmutableList.of(new Certificate(progData.getCertificate(), Countries.GB)));
        }
    }
    
    private void setGenres(ProgData progData, Content content) {
        Set<String> extractedGenres = genreMap.map(ImmutableSet.copyOf(Iterables.transform(progData.getCategory(), TO_GENRE_URIS)));
        extractedGenres.remove("http://pressassociation.com/genres/BE00");
        if(!extractedGenres.isEmpty()) {
            content.setGenres(extractedGenres);
        }
    }

    private Maybe<ItemAndBroadcast> getEpisode(ProgData progData, Channel channel, DateTimeZone zone, boolean isEpisode, Timestamp updatedAt) {
        
        String episodeUri = PaHelper.getEpisodeUri(programmeId(progData));
        Maybe<Identified> possiblePrevious = contentResolver.findByCanonicalUris(ImmutableList.of(episodeUri)).getFirstValue();

        Item item;
        if (possiblePrevious.hasValue()) {
            item = (Item) possiblePrevious.requireValue();
            if (!(item instanceof Episode) && isEpisode) {
                log.record(warnEntry().withSource(getClass()).withDescription("%s resolved as %s being ingested as Episode", episodeUri, item.getClass().getSimpleName()));
                item = convertItemToEpisode(item);
            } else if(item instanceof Episode && !isEpisode) {
                log.record(errorEntry().withSource(getClass()).withDescription("%s resolved as %s being ingested as Item", episodeUri, item.getClass().getSimpleName()));
            }
        } else {
            item = getBasicEpisode(progData, isEpisode);
        }
        
        item.addAlias(PaHelper.getEpisodeAlias(programmeId(progData)));
        
        Broadcast broadcast = setCommonDetails(progData, channel, zone, item, updatedAt);
        
        try {
            if (item instanceof Episode) {
                if (progData.getEpisodeNumber() != null) {
                    ((Episode) item).setEpisodeNumber(Integer.valueOf(progData.getEpisodeNumber()));
                }
                if (progData.getSeriesNumber() != null) {
                	 ((Episode) item).setSeriesNumber(Integer.valueOf(progData.getSeriesNumber()));
                }
            }
        } catch (NumberFormatException e) {
            // sometimes we don't get valid numbers
            //log.
        }
        
        return Maybe.just(new ItemAndBroadcast(item, Maybe.just(broadcast)));
    }

    private Item convertItemToEpisode(Item item) {
        Episode episode = new Episode(item.getCanonicalUri(), item.getCurie(),item.getPublisher());
        episode.setAliases(item.getAliases());
        episode.setAliasUrls(item.getAliasUrls());
        episode.setBlackAndWhite(item.getBlackAndWhite());
        episode.setClips(item.getClips());
        episode.setParentRef(item.getContainer());
        episode.setCountriesOfOrigin(item.getCountriesOfOrigin());
        episode.setDescription(item.getDescription());
        episode.setFirstSeen(item.getFirstSeen());
        episode.setGenres(item.getGenres());
        episode.setImage(item.getImage());
        episode.setImages(item.getImages());
        episode.setIsLongForm(item.getIsLongForm());
        episode.setLastFetched(item.getLastFetched());
        episode.setLastUpdated(item.getLastUpdated());
        episode.setMediaType(item.getMediaType());
        episode.setPeople(item.getPeople());
        episode.setScheduleOnly(item.isScheduleOnly());
        episode.setSpecialization(item.getSpecialization());
        episode.setTags(item.getTags());
        episode.setThisOrChildLastUpdated(item.getThisOrChildLastUpdated());
        episode.setThumbnail(item.getThumbnail());
        episode.setTitle(item.getTitle());
        episode.setVersions(item.getVersions());
        return episode;
    }

    private Broadcast broadcast(ProgData progData, Channel channel, DateTimeZone zone, Timestamp updateAt) {
        Duration duration = Duration.standardMinutes(Long.valueOf(progData.getDuration()));

        DateTime transmissionTime = getTransmissionTime(progData.getDate(), progData.getTime(), zone);
        
        Broadcast broadcast = new Broadcast(channel.uri(), transmissionTime, duration).withId(PaHelper.getBroadcastId(progData.getShowingId()));
        
        if (progData.getAttr() != null) {
            broadcast.setRepeat(isRepeat(channel, progData.getAttr()));
            broadcast.setSubtitled(getBooleanValue(progData.getAttr().getSubtitles()));
            broadcast.setSigned(getBooleanValue(progData.getAttr().getSignLang()));
            broadcast.setAudioDescribed(getBooleanValue(progData.getAttr().getAudioDes()));
            broadcast.setHighDefinition(getBooleanValue(progData.getAttr().getHd()));
            broadcast.setWidescreen(getBooleanValue(progData.getAttr().getWidescreen()));
            broadcast.setLive(getBooleanValue(progData.getAttr().getLive()));
            broadcast.setSurround(getBooleanValue(progData.getAttr().getSurround()));
            broadcast.setPremiere(getBooleanValue(progData.getAttr().getPremiere()));
            broadcast.setNewSeries(getBooleanValue(progData.getAttr().getNewSeries()));
        }
        broadcast.setLastUpdated(updateAt.toDateTimeUTC());
        return broadcast;
    }
    
    private Boolean isRepeat(Channel channel, Attr attr) {
        // If the broadcast is on a 'terrestrial' channel only inspect repeat flag
        if (terrestrialChannels.contains(channel)) {
            return getBooleanValue(attr.getRepeat());
        }
        // check new episode flag, will be set to yes only if definitely new content 
        // so only then can we assert it's NOT a repeat. Return null (don't know) otherwise.
        return Boolean.TRUE.equals(getBooleanValue(attr.getNewEpisode())) ? Boolean.FALSE : null;
    }

    private void addBroadcast(Version version, Broadcast broadcast) {
        if (! Strings.isNullOrEmpty(broadcast.getSourceId())) {
            Set<Broadcast> broadcasts = Sets.newHashSet();
            Maybe<Interval> broadcastInterval = broadcast.transmissionInterval();
            
            for (Broadcast currentBroadcast: version.getBroadcasts()) {
                // I know this is ugly, but it's easier to read.
                if (Strings.isNullOrEmpty(currentBroadcast.getSourceId())) {
                    continue;
                }
                if (broadcast.getSourceId().equals(currentBroadcast.getSourceId())) {
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
    
    private List<CrewMember> people(ProgData progData, List<CrewMember> currentCrew) {
        List<CrewMember> people = Lists.newArrayList();
        
        for (CastMember cast : progData.getCastMember()) {
            Person person = cast.getPerson();
            if (person != null && !Strings.isNullOrEmpty(person.getPersonId())) {
                Actor actor = Actor.actor(person.getPersonId(), person.getvalue(), cast.getCharacter(), Publisher.PA);
                actor.withRole(Role.fromKey(cast.getRole().toLowerCase().replace(' ', '_')));
                if (! people.contains(actor)) {
                    people.add(actor);
                }
            }
        }
        
        if (people.isEmpty()) {
            Iterables.addAll(people, Iterables.filter(currentCrew, Actor.class));
        }
        
        for (StaffMember staffMember : progData.getStaffMember()) {
            if (!Strings.isNullOrEmpty(staffMember.getPerson().getPersonId())) {
                String roleKey = staffMember.getRole().toLowerCase().replace(' ', '_');
                CrewMember crewMember = CrewMember.crewMember(staffMember.getPerson().getPersonId(), staffMember.getPerson().getvalue(), roleKey, Publisher.PA);
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
    
    private Film getBasicFilm(ProgData progData) {
        Film film = new Film(PaHelper.getFilmUri(programmeId(progData)), PaHelper.getEpisodeCurie(programmeId(progData)), Publisher.PA);
        // TODO new alias
        film.addAliasUrl(PaHelper.getFilmRtAlias(progData.getRtFilmnumber()));
        
        setBasicDetails(progData, film);
        
        return film;
    }

    private Item getBasicEpisode(ProgData progData, boolean isEpisode) {
        Item item = isEpisode ? new Episode() : new Item();
        item.setCanonicalUri(getEpisodeUri(programmeId(progData)));
        item.setCurie(PaHelper.getEpisodeCurie(programmeId(progData)));
        item.setPublisher(Publisher.PA);
        setBasicDetails(progData, item);
        return item;
    }
    
    private void setBasicDetails(ProgData progData, Item item) {
        setCertificate(progData, item);
        Version version = new Version();
        version.setProvider(Publisher.PA);
        version.set3d(getBooleanValue(progData.getAttr().getThreeD()));
        item.addVersion(version);

        Duration duration = Duration.standardMinutes(Long.valueOf(progData.getDuration()));
        version.setDuration(duration);

        item.addVersion(version);
    }
    
    protected Specialization specialization(ProgData progData, Channel channel) {
        if (MediaType.AUDIO.equals(channel.mediaType())) {
            return Specialization.RADIO;
        }
        return Strings.isNullOrEmpty(progData.getRtFilmnumber()) ? Specialization.TV : Specialization.FILM;
    }

    protected static DateTime getTransmissionTime(String date, String time, DateTimeZone zone) {
        String dateString = date + "-" + time;
        return DateTimeFormat.forPattern("dd/MM/yyyy-HH:mm").withZone(zone).parseDateTime(dateString);
    }
    
    protected static String programmeId(ProgData progData) {
        return progData.getProgId();
    }
    
    private static Boolean getBooleanValue(String value) {
        if (value != null) {
            return value.equalsIgnoreCase(YES);
        }
        return null;
    }
}
