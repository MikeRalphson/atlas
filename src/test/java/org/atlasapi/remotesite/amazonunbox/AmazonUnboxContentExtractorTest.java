package org.atlasapi.remotesite.amazonunbox;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.CrewMember.Role;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Image;
import org.atlasapi.media.entity.ImageAspectRatio;
import org.atlasapi.media.entity.ImageType;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.MediaType;
import org.atlasapi.media.entity.Policy;
import org.atlasapi.media.entity.Policy.RevenueContract;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Specialization;
import org.atlasapi.media.entity.Version;
import org.atlasapi.remotesite.ContentExtractor;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.metabroadcast.common.currency.Price;
import com.metabroadcast.common.intl.Countries;
import com.metabroadcast.common.media.MimeType;


public class AmazonUnboxContentExtractorTest {

    private final ContentExtractor<AmazonUnboxItem, Iterable<Content>> extractor = new AmazonUnboxContentExtractor();
    
    @Test
    public void testExtractionOfAllLocations() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withQuality(Quality.SD)
                .withUnboxHdPurchasePrice("12.99")
                .withUnboxHdPurchaseUrl("http://hdpurchaseurl.org/")
                .withIsTrident(true)
                .withUnboxHdRentalPrice("4.99")
                .withUnboxHdRentalUrl("http://hdrentalurl.org/")
                .withUnboxSdRentalPrice("1.99")
                .withUnboxSdRentalUrl("http://sdrentalurl.org/")
                .build();
        
        
        
        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        Version version = Iterables.getOnlyElement(film.getVersions());
        
        Map<Integer, Encoding> encodingsByHorizontalScale = encodingsByHorizontalScale(version.getManifestedAs());
        Encoding hdEncoding = encodingsByHorizontalScale.get(1280);
        assertThat(hdEncoding.getHighDefinition(), is(true));

        Map<String, Location> hdLocationsByUri = locationsByUrl(hdEncoding.getAvailableAt());
        
        Location hdPurchaseLocation = hdLocationsByUri.get("http://hdpurchaseurl.org/");
        assertThat(hdPurchaseLocation.getPolicy().getRevenueContract(), is(RevenueContract.PAY_TO_BUY));
        assertThat(hdPurchaseLocation.getPolicy().getPrice().getAmount(), is(1299));
        
        Location hdRentalLocation = hdLocationsByUri.get("http://hdrentalurl.org/");
        assertThat(hdRentalLocation.getPolicy().getRevenueContract(), is(RevenueContract.PAY_TO_RENT));
        assertThat(hdRentalLocation.getPolicy().getPrice().getAmount(), is(499));
        
        Encoding sdEncoding = encodingsByHorizontalScale.get(720);
        Map<String, Location> sdLocationsByUri = locationsByUrl(sdEncoding.getAvailableAt());
        
        Location sdPurchaseLocation = sdLocationsByUri.get("http://www.amazon.co.uk/gp/product/B00EV5ROP4/");
        assertThat(sdPurchaseLocation.getPolicy().getRevenueContract(), is(RevenueContract.PAY_TO_BUY));
        assertThat(sdPurchaseLocation.getPolicy().getPrice().getAmount(), is(999));
        
        Location sdRentalLocation = sdLocationsByUri.get("http://sdrentalurl.org/");
        assertThat(sdRentalLocation.getPolicy().getRevenueContract(), is(RevenueContract.PAY_TO_RENT));
        assertThat(sdRentalLocation.getPolicy().getPrice().getAmount(), is(199));
        
        Location primeSubscriptionLocation = sdLocationsByUri.get("http://www.amazon.com/gp/product/B007FUIBHM/");
        assertThat(primeSubscriptionLocation.getPolicy().getRevenueContract(), is(RevenueContract.SUBSCRIPTION));
        
        
        
        
    }
    
    private Map<Integer, Encoding> encodingsByHorizontalScale(Iterable<Encoding> encodings) {
        return Maps.uniqueIndex(encodings, new Function<Encoding, Integer>() {
            @Override
            public Integer apply(Encoding input) {
                return input.getVideoHorizontalSize();
            }
        });
    }
    
    private Map<String, Location> locationsByUrl(Iterable<Location> locations) {
        return Maps.uniqueIndex(locations, Location.TO_URI);
    }
    
    private void assertVersionFeatures(Version version, int horizontalScale, int verticalScale, String aspectRatio, int bitRate,
            String locationUri, RevenueContract payToBuy, boolean isHd) {

        Encoding encoding = Iterables.getOnlyElement(version.getManifestedAs());
        assertThat(encoding.getVideoHorizontalSize(), is(equalTo(horizontalScale)));
        assertThat(encoding.getVideoVerticalSize(), is(equalTo(verticalScale)));
        assertEquals(aspectRatio, encoding.getVideoAspectRatio());
        assertThat(encoding.getBitRate(), is(equalTo(bitRate)));
        
    }

    @Test
    public void testExtractionOfHdContent() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withUnboxHdPurchaseUrl("http://hdlocation.org/")
                .withUnboxHdPurchasePrice("9.99")
                .withUnboxSdPurchasePrice(null)
                .withUnboxSdPurchaseUrl(null)
                .build();

        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        Version version = Iterables.getOnlyElement(film.getVersions());
        Encoding encoding = Iterables.getOnlyElement(version.getManifestedAs());
        
        assertThat(encoding.getVideoHorizontalSize(), is(equalTo(1280)));
        assertThat(encoding.getVideoVerticalSize(), is(equalTo(720)));
        assertEquals("16:9", encoding.getVideoAspectRatio());
        assertThat(encoding.getBitRate(), is(equalTo(3308)));
    }
    
    @Test
    public void testExtractionOfLanguages() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE).build();

        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        assertEquals(ImmutableSet.of("en"), film.getLanguages());
    }
    
    @Test
    public void testExtractionOfGenres() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withGenres(ImmutableSet.of(AmazonUnboxGenre.ACTION, AmazonUnboxGenre.ADVENTURE))
                .build();
        
        Content extractedContent = Iterables.getOnlyElement(extractor.extract(filmItem));
        Film film = (Film) extractedContent;
        
        assertEquals(ImmutableSet.of("http://unbox.amazon.co.uk/genres/action", "http://unbox.amazon.co.uk/genres/adventure"), film.getGenres());
    }
    
    @Test
    public void testExtractionOfPeople() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withDirector("Director")
                .withStarring("Cast 1")
                .withStarring("Cast 2")
                .withStarring("Cast 3")
                .build();
        
        Content extractedContent = Iterables.getOnlyElement(extractor.extract(filmItem));
        Film film = (Film) extractedContent;

        List<CrewMember> people = film.getPeople();
        Iterable<String> names = Iterables.transform(people, new Function<CrewMember, String>() {
            @Override
            public String apply(CrewMember input) {
                return input.name();
            }
        });
        assertEquals(ImmutableSet.of("Director", "Cast 1", "Cast 2", "Cast 3"), ImmutableSet.copyOf(names));
        
        CrewMember director = Iterables.getOnlyElement(Iterables.filter(people, new Predicate<CrewMember>() {
            @Override
            public boolean apply(CrewMember input) {
                return input.role() != null;
            }}));
        
        assertEquals(Role.DIRECTOR, director.role());
        assertEquals("Director", director.name());
    }
    
    @Test
    public void testExtractionOfCommonFields() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withTConst("ImdbId")
                .build();
        
        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        assertEquals("Synopsis of the item", film.getDescription());
        assertEquals(Publisher.AMAZON_UNBOX, film.getPublisher());
        assertEquals(Specialization.FILM, film.getSpecialization());
        assertEquals(MediaType.VIDEO, film.getMediaType());
        
        assertEquals("Large Image", film.getImage());
        
        Image image = Iterables.getOnlyElement(film.getImages());
        assertEquals("Large Image", image.getCanonicalUri());
        assertEquals(ImageType.PRIMARY, image.getType());
        assertThat(image.getWidth(), is(equalTo(320)));
        assertThat(image.getHeight(), is(equalTo(240)));
        assertEquals(MimeType.IMAGE_JPG, image.getMimeType());
        assertEquals(ImageAspectRatio.FOUR_BY_THREE, image.getAspectRatio());
        
        assertThat(film.getYear(), is(equalTo(2012)));
        
        Alias imdbAlias = new Alias("zz:imdb:id", "ImdbId");
        Alias asinAlias = new Alias("gb:amazon:asin", "filmAsin");
        assertEquals(ImmutableSet.of(imdbAlias, asinAlias), film.getAliases());
        assertEquals(ImmutableSet.of("http://imdb.com/title/ImdbId", "http://gb.amazon.com/asin/filmAsin"), film.getAliasUrls());
    }

    public void testExtractionOfVersions() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withDuration(Duration.standardMinutes(100))
                .build();

        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        Version version = Iterables.getOnlyElement(film.getVersions());
        assertEquals("http://unbox.amazon.co.uk/versions/filmAsin", version.getCanonicalUri());
        assertThat(version.getDuration(), is(equalTo(100)));
    }
    
    public void testExtractionOfPolicyWithRental() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withRental(true)
                .build();

        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        Version version = Iterables.getOnlyElement(film.getVersions());
        Encoding encoding = Iterables.getOnlyElement(version.getManifestedAs());
        Location location = Iterables.getOnlyElement(encoding.getAvailableAt());
        Policy policy = location.getPolicy();
        
        assertEquals(RevenueContract.PAY_TO_RENT, policy.getRevenueContract());
        assertEquals(new Price(Currency.getInstance("GBP"), 9.99), policy.getPrice());
        assertEquals(ImmutableSet.of(Countries.GB), policy.getAvailableCountries());
    }
    
    @Test
    public void testExtractionOfPolicyWithSubscription() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .withRental(false)
                .build();

        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        Version version = Iterables.getOnlyElement(film.getVersions());
        Encoding encoding = Iterables.getOnlyElement(version.getManifestedAs());
        Location location = Iterables.getOnlyElement(encoding.getAvailableAt());
        Policy policy = location.getPolicy();
        
        assertEquals(RevenueContract.PAY_TO_BUY, policy.getRevenueContract());
    }
    
    @Test
    public void testExtractionOfFilm() {
        AmazonUnboxItem filmItem = createAmazonUnboxItem("filmAsin", ContentType.MOVIE)
                .build();
        
        
        Film film = (Film) Iterables.getOnlyElement(extractor.extract(filmItem));
        
        assertEquals("http://unbox.amazon.co.uk/filmAsin", film.getCanonicalUri());
    }
    
    //TODO hierarchied episodes?
    @Test
    public void testExtractionOfEpisodeWithSeries() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("episodeAsin", ContentType.TVEPISODE)
                .withEpisodeNumber(5)
                .withSeasonAsin("seasonAsin")
                .withSeasonNumber(2)
                .build();
        
        
        Episode episode = Iterables.getOnlyElement(Iterables.filter(extractor.extract(episodeItem), Episode.class));
                
        assertEquals("http://unbox.amazon.co.uk/episodeAsin", episode.getCanonicalUri());
        assertEquals("http://unbox.amazon.co.uk/seasonAsin", episode.getSeriesRef().getUri());
        assertThat(episode.getEpisodeNumber(), is(equalTo(5)));
        assertThat(episode.getSeriesNumber(), is(equalTo(2)));
    }
    
    @Test
    public void testExtractionOfEpisodeWithBrand() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("episodeAsin", ContentType.TVEPISODE)
                .withEpisodeNumber(5)
                .withSeriesAsin("seriesAsin")
                .build();
        
        
        Episode episode = Iterables.getOnlyElement(Iterables.filter(extractor.extract(episodeItem), Episode.class));
        
        assertEquals("http://unbox.amazon.co.uk/episodeAsin", episode.getCanonicalUri());
        assertEquals("http://unbox.amazon.co.uk/seriesAsin", episode.getContainer().getUri());
        assertThat(episode.getEpisodeNumber(), is(equalTo(5)));
    }
    
    @Test
    public void testExtractionOfEpisodeWithSeriesAndBrand() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("episodeAsin", ContentType.TVEPISODE)
                .withEpisodeNumber(5)
                .withSeasonAsin("seasonAsin")
                .withSeasonNumber(2)
                .withSeriesAsin("seriesAsin")
                .withSeriesTitle("Series")
                .build();
        
        
        Episode episode = Iterables.getOnlyElement(Iterables.filter(extractor.extract(episodeItem), Episode.class));
                
        assertEquals("http://unbox.amazon.co.uk/episodeAsin", episode.getCanonicalUri());
        assertEquals("http://unbox.amazon.co.uk/seasonAsin", episode.getSeriesRef().getUri());
        assertEquals("http://unbox.amazon.co.uk/seriesAsin", episode.getContainer().getUri());
        assertThat(episode.getEpisodeNumber(), is(equalTo(5)));
        assertThat(episode.getSeriesNumber(), is(equalTo(2)));
        
        Brand brand = Iterables.getOnlyElement(Iterables.filter(extractor.extract(episodeItem), Brand.class));
        assertThat(brand.getCanonicalUri(), is(equalTo("http://unbox.amazon.co.uk/seriesAsin")));
        assertThat(Iterables.getOnlyElement(brand.getRelatedLinks()).getUrl(), is("http://www.amazon.co.uk/dp/seriesAsin/"));
    }
    
    @Test
    public void testExtractionOfItem() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("itemAsin", ContentType.TVEPISODE).build();
        
        Item item = (Item) Iterables.getOnlyElement(Iterables.filter(extractor.extract(episodeItem), Item.class));
        
        assertEquals("http://unbox.amazon.co.uk/itemAsin", item.getCanonicalUri());
    }
    
    @Test
    public void testExtractionOfSeriesWithBrand() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("seasonAsin", ContentType.TVSEASON)
                .withSeriesAsin("seriesAsin")
                .build();
        
        Series series = (Series) Iterables.getOnlyElement(extractor.extract(episodeItem));
        
        assertEquals("http://unbox.amazon.co.uk/seasonAsin", series.getCanonicalUri());
        assertEquals("http://unbox.amazon.co.uk/seriesAsin", series.getParent().getUri());
    }
    
    @Test
    public void testExtractionOfTopLevelSeries() {
        AmazonUnboxItem episodeItem = createAmazonUnboxItem("seasonAsin", ContentType.TVSEASON).build();
        
        Series series = (Series) Iterables.getOnlyElement(extractor.extract(episodeItem));
        
        assertEquals("http://unbox.amazon.co.uk/seasonAsin", series.getCanonicalUri());
        assertNull(series.getParent());
    }

    /**
     * Creates a Builder object for an AmazonUnboxItem, defaulting enough fields to
     * ensure that content extraction will succeed. Any of these fields can be overridden,
     * and more fields can be added to the return value of this method if needed.
     * 
     * @param asin - identifier for the item being created
     * @param type - type of item
     * @return
     */
    private AmazonUnboxItem.Builder createAmazonUnboxItem(String asin, ContentType type) {
        return AmazonUnboxItem.builder()
                .withAsin(asin)
                .withUrl("http://www.amazon.com/gp/product/B007FUIBHM/ref=atv_feed_catalog")
                .withSynopsis("Synopsis of the item")
                .withLargeImageUrl("Large Image")
                .withContentType(type)
                .withReleaseDate(new DateTime(2012, 6, 6, 0, 0, 0))
                .withQuality(Quality.SD)
                .withDuration(Duration.standardMinutes(100))
                .withPrice("9.99")
                .withUnboxSdPurchasePrice("9.99")
                .withUnboxSdPurchaseUrl("http://www.amazon.co.uk/gp/product/B00EV5ROP4/INSERT_TAG_HERE/ref=atv_feed_catalog/");
                
    }
}
