package org.atlasapi.remotesite.five;

import static org.atlasapi.media.content.Specialization.FILM;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nu.xom.Builder;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.NodeFactory;
import nu.xom.Nodes;

import org.atlasapi.genres.GenreMap;
import org.atlasapi.media.content.Brand;
import org.atlasapi.media.content.ContentWriter;
import org.atlasapi.media.content.Film;
import org.atlasapi.media.content.Item;
import org.atlasapi.media.content.MediaType;
import org.atlasapi.media.content.Publisher;
import org.atlasapi.media.content.Series;
import org.atlasapi.media.content.Specialization;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.AdapterLogEntry;
import org.atlasapi.persistence.logging.AdapterLogEntry.Severity;
import org.atlasapi.persistence.system.RemoteSiteClient;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.http.HttpResponse;

public class FiveBrandProcessor {
    
    private final static String WATCHABLES_URL_SUFFIX = "/watchables?expand=season%7Ctransmissions";
    private final ContentWriter writer;
    private final GenreMap genreMap = new FiveGenreMap();
    private final AdapterLog log;
    private final FiveEpisodeProcessor episodeProcessor;
    private final String baseApiUrl;
    private final RemoteSiteClient<HttpResponse> httpClient;

    public FiveBrandProcessor(ContentWriter writer, AdapterLog log, String baseApiUrl, RemoteSiteClient<HttpResponse> httpClient) {
        this.writer = writer;
        this.log = log;
        this.baseApiUrl = baseApiUrl;
        this.httpClient = httpClient;
        this.episodeProcessor = new FiveEpisodeProcessor(baseApiUrl, httpClient);
    }
    
    public void processShow(Element element) {
        
        String id = childValue(element, "id");
        Brand brand = new Brand(getShowUri(id), getBrandCurie(id), Publisher.FIVE);
        
        brand.setTitle(childValue(element, "title"));
        
        Maybe<String> description = getDescription(element);
        if (description.hasValue()) {
            brand.setDescription(description.requireValue());
        }
        
        brand.setGenres(getGenres(element));
        
        Maybe<String> image = getImage(element);
        if (image.hasValue()) {
            brand.setImage(image.requireValue());
        }
        
        Specialization specialization = specializationFrom(element);
        
        brand.setMediaType(MediaType.VIDEO);
        brand.setSpecialization(specialization);
        
        EpisodeProcessingNodeFactory nodeFactory = new EpisodeProcessingNodeFactory(episodeProcessor, specialization);
        try {
        	String responseBody = httpClient.get(getShowUri(id) + WATCHABLES_URL_SUFFIX).body();
            new Builder(nodeFactory).build(new StringReader(responseBody));
        } catch(Exception e) {
            log.record(new AdapterLogEntry(Severity.ERROR).withCause(e).withSource(getClass()).withDescription("Exception while trying to parse episodes for brand " + brand.getTitle()));
            return;
        }
        
        if(specialization == FILM && nodeFactory.items.size() == 1) {
            setFilmDescription((Film)Iterables.getOnlyElement(nodeFactory.items), element);
        }
        
        writer.createOrUpdate(brand);
        for (Series series : episodeProcessor.getSeriesMap().values()) {
            writer.createOrUpdate(series);
        }
        for (Item item : nodeFactory.items) {
            item.setContainer(brand);
        	writer.createOrUpdate(item);
        }
    }
    
    private static final Pattern FILM_YEAR = Pattern.compile(".*\\((\\d{4})\\)$");
    
    private void setFilmDescription(Film film, Element element) {
        Maybe<String> description = getDescription(element);
        if(description.hasValue()) {
            film.setDescription(description.requireValue());
        }
        String shortDesc = childValue(element, "short_description");
        if(!Strings.isNullOrEmpty(shortDesc)) {
            Matcher matcher = FILM_YEAR.matcher(shortDesc);
            if(matcher.matches()) {
                film.setYear(Integer.parseInt(matcher.group(1)));
            }
        }
    }

    private Specialization specializationFrom(Element element) {
        String progType = childValue(element, "programme_type");
        if(progType.equals("Feature Film") || progType.equals("TV Movie")) {
            return Specialization.FILM;
        }
        return Specialization.TV;
    }

    private String getShowUri(String id) {
        return baseApiUrl + "/shows/" + id;
    }
    
    private String getBrandCurie(String id) {
        return "five:b-" + id;
    }
    
    private String childValue(Element element, String childName) {
        Element firstChild = element.getFirstChildElement(childName);
        if(firstChild != null) {
            return firstChild.getValue();
        }
        return null;
    }
    
    private Maybe<String> getDescription(Element element) {
        String longDescription = element.getFirstChildElement("long_description").getValue();
        if (!Strings.isNullOrEmpty(longDescription)) {
            return Maybe.just(longDescription);
        }
        
        String shortDescription = element.getFirstChildElement("short_description").getValue();
        if (!Strings.isNullOrEmpty(shortDescription)) {
            return Maybe.just(shortDescription);
        }
        
        return Maybe.nothing();
    }
    
    private Set<String> getGenres(Element element) {
        return genreMap.mapRecognised(ImmutableSet.of("http://www.five.tv/genres/" + element.getFirstChildElement("genre").getValue()));
    }
    
    private Maybe<String> getImage(Element element) {
        Elements imageElements = element.getFirstChildElement("images").getChildElements("image");
        if (imageElements.size() > 0) {
            return Maybe.just(imageElements.get(0).getValue());
        }
        
        return Maybe.nothing();
    }
    
    private class EpisodeProcessingNodeFactory extends NodeFactory {
        
        private final FiveEpisodeProcessor episodeProcessor;
		private final List<Item> items = Lists.newArrayList();
        private final Specialization specialization;

        public EpisodeProcessingNodeFactory(FiveEpisodeProcessor episodeProcessor, Specialization specialization) {
			this.episodeProcessor = episodeProcessor;
            this.specialization = specialization;
        }
        
        @Override
        public Nodes finishMakingElement(Element element) {
            if (element.getLocalName().equalsIgnoreCase("watchable")) {
                try {
                    items.add(episodeProcessor.processEpisode(element, specialization));
                }
                catch (Exception e) {
                    log.record(new AdapterLogEntry(Severity.ERROR).withSource(FiveEpisodeProcessor.class).withCause(e).withDescription("Exception when processing episode"));
                }
                
                return new Nodes();
            }
            else {
                return super.finishMakingElement(element);
            }
        }
    }
}
