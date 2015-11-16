package org.atlasapi.remotesite.bbc.nitro.extract;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.Lists;
import com.metabroadcast.common.intl.Countries;
import org.atlasapi.media.entity.CrewMember;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.ParentRef;
import org.atlasapi.media.entity.Person;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.persistence.content.people.QueuingItemsPeopleWriter;
import org.atlasapi.persistence.content.people.QueuingPersonWriter;
import org.atlasapi.remotesite.ContentExtractor;
import org.atlasapi.remotesite.bbc.BbcFeeds;
import org.atlasapi.remotesite.bbc.nitro.v1.NitroGenreGroup;
import org.joda.time.DateTime;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.metabroadcast.atlas.glycerin.model.AncestorsTitles;
import com.metabroadcast.atlas.glycerin.model.AncestorsTitles.Brand;
import com.metabroadcast.atlas.glycerin.model.AncestorsTitles.Series;
import com.metabroadcast.atlas.glycerin.model.Brand.Image;
import com.metabroadcast.atlas.glycerin.model.Brand.People;
import com.metabroadcast.atlas.glycerin.model.Brand.MasterBrand;
import com.metabroadcast.atlas.glycerin.model.Episode;
import com.metabroadcast.atlas.glycerin.model.Format;
import com.metabroadcast.atlas.glycerin.model.PidReference;
import com.metabroadcast.atlas.glycerin.model.Synopses;
import com.metabroadcast.common.time.Clock;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * <p>
 * A {@link BaseNitroItemExtractor} for extracting {@link Item}s from
 * {@link Episode} sources.
 * </p>
 * 
 * <p>
 * Creates and {@link Item} or {@link org.atlasapi.media.entity.Episode Atlas
 * Episode} and sets the parent and episode number fields as necessary.
 * </p>
 * 
 * @see BaseNitroItemExtractor
 * @see NitroContentExtractor
 */
public final class NitroEpisodeExtractor extends BaseNitroItemExtractor<Episode, Item> {

    private static final String FILM_FORMAT_ID = "PT007";
    private static final Predicate<Format> IS_FILM_FORMAT = new Predicate<Format>() {
        @Override
        public boolean apply(Format input) {
            return FILM_FORMAT_ID.equals(input.getFormatId());
        }
    };
    private @Value("${updaters.bbcnitro.releasedateingest.enabled}") Boolean releaseDateIngestIsEnabled;

    private final ContentExtractor<List<NitroGenreGroup>, Set<String>> genresExtractor
        = new NitroGenresExtractor();

    private final NitroCrewMemberExtractor crewMemberExtractor = new NitroCrewMemberExtractor();
    private final NitroPersonExtractor personExtractor = new NitroPersonExtractor();
    private final QueuingPersonWriter personWriter;

    public NitroEpisodeExtractor(Clock clock, QueuingPersonWriter personWriter) {
        super(clock);
        this.personWriter = personWriter;
    }

    @Override
    protected Item createContent(NitroItemSource<Episode> source) {
        if (isEpisode(source.getProgramme())) {
            return new org.atlasapi.media.entity.Episode();
        }

        if (isFilmFormat(source.getProgramme())) {
            return new Film();
        }

        return new Item();
    }

    private boolean isFilmFormat(Episode episode) {
        if (episode.getFormats() == null) {
            return false;
        }

        return Iterables.any(episode.getFormats().getFormat(), IS_FILM_FORMAT);
    }

    private boolean isEpisode(Episode episode) {
        return episode.getEpisodeOf() != null;
    }

    @Override
    protected String extractPid(NitroItemSource<Episode> source) {
        return source.getProgramme().getPid();
    }

    @Override
    protected String extractTitle(NitroItemSource<Episode> source) {
        return source.getProgramme().getTitle();
    }

    @Override
    protected Synopses extractSynopses(NitroItemSource<Episode> source) {
        return source.getProgramme().getSynopses();
    }

    @Override
    protected People extractPeople(NitroItemSource<Episode> source) {
        return source.getProgramme().getPeople();
    }

    @Override
    protected Image extractImage(NitroItemSource<Episode> source) {
        return source.getProgramme().getImage();
    }

    protected XMLGregorianCalendar extractReleaseDate(NitroItemSource<Episode> source) {
        return source.getProgramme().getReleaseDate();
    }

    @Override
    protected void extractAdditionalItemFields(NitroItemSource<Episode> source, Item item, DateTime now) {
        Episode episode = source.getProgramme();
        if (item.getTitle() == null) {
            item.setTitle(episode.getPresentationTitle());
        } 
        if (hasMoreThanOneSeriesAncestor(episode)) {
            item.setTitle(compileTitleForSeriesSeriesEpisode(episode));
        }
        if (episode.getEpisodeOf() != null) {
            org.atlasapi.media.entity.Episode episodeContent = (org.atlasapi.media.entity.Episode) item;
            BigInteger position = episode.getEpisodeOf().getPosition();
            if (position != null) {
                episodeContent.setEpisodeNumber(position.intValue());
            }
            episodeContent.setSeriesRef(getSeriesRef(episode));
        }
        item.setParentRef(getBrandRef(episode));
        item.setGenres(genresExtractor.extract(source.getGenres()));
        if (releaseDateIngestIsEnabled && episode.getReleaseDate() != null) {
            setReleaseDate(item, source);
        }
        writeAndSetPeople(item, source);
    }

    private void writeAndSetPeople(Item item, NitroItemSource<Episode> source) {
        People people = source.getProgramme().getPeople();

        if (people != null) {
            ImmutableList.Builder<CrewMember> crewMembers = ImmutableList.builder();

            for (People.Contribution contribution : people.getPeopleMixinContribution()) {
                Optional<CrewMember> crewMember = crewMemberExtractor.extract(contribution);

                if (crewMember.isPresent()) {
                    crewMembers.add(crewMember.get());
                    Optional<Person> person = personExtractor.extract(contribution);

                    if (person.isPresent()) {
                        personWriter.addItemToPerson(person.get(), item);
                    }
                }
            }

            item.setPeople(crewMembers.build());
        }
    }

    private boolean hasMoreThanOneSeriesAncestor(Episode episode) {
        AncestorsTitles titles = episode.getAncestorsTitles();
        return titles != null && titles.getSeries().size() > 1;
    }

    private void setReleaseDate(Item item, NitroItemSource<Episode> source) {
        XMLGregorianCalendar date = extractReleaseDate(source);
        LocalDate localDate = new LocalDate(date.getYear(),date.getMonth(),date.getDay());
        ReleaseDate releaseDate = new ReleaseDate(localDate, Countries.GB, ReleaseDate.ReleaseType.FIRST_BROADCAST);
        item.setReleaseDates(Lists.newArrayList(releaseDate));
    }

    private String compileTitleForSeriesSeriesEpisode(Episode episode) {
        List<Series> series = episode.getAncestorsTitles().getSeries();
        String ssTitle = Iterables.getLast(series).getTitle();
        String suffix = "";
        if (episode.getPresentationTitle() != null) {
            suffix = " - " + episode.getPresentationTitle();
        } else if (episode.getTitle() != null) {
            suffix = " - " + episode.getTitle();
        }
        return ssTitle + suffix;
    }

    private ParentRef getBrandRef(Episode episode) {
        ParentRef brandRef = null;
        if (isBrandEpisode(episode)) {
            brandRef = new ParentRef(BbcFeeds.nitroUriForPid(episode.getEpisodeOf().getPid()));
        } else if (isBrandSeriesEpisode(episode)) {
            brandRef = getRefFromBrandAncestor(episode);
        } else if (isTopLevelSeriesEpisode(episode)) {
           Series topSeries = episode.getAncestorsTitles().getSeries().get(0);
           brandRef = new ParentRef(BbcFeeds.nitroUriForPid(topSeries.getPid()));
        }
        return brandRef;
    }

    private ParentRef getRefFromBrandAncestor(Episode episode) {
        Brand brandAncestor = episode.getAncestorsTitles().getBrand();
        return new ParentRef(BbcFeeds.nitroUriForPid(brandAncestor.getPid()));
    }

    private ParentRef getSeriesRef(Episode episode) {
        ParentRef seriesRef = null;
        if (isBrandSeriesEpisode(episode) || isTopLevelSeriesEpisode(episode)){
            Series topSeries = episode.getAncestorsTitles().getSeries().get(0);
            seriesRef = new ParentRef(BbcFeeds.nitroUriForPid(topSeries.getPid()));
        }
        return seriesRef;
    }
    
    private boolean isBrandEpisode(Episode episode) {
        PidReference episodeOf = episode.getEpisodeOf();
        return episodeOf != null
            && "brand".equals(episodeOf.getResultType());
    }
    
    private boolean isBrandSeriesEpisode(Episode episode) {
        PidReference episodeOf = episode.getEpisodeOf();
        return episodeOf != null
                && "series".equals(episodeOf.getResultType())
                && hasBrandAncestor(episode);
    }

    private boolean hasBrandAncestor(Episode episode) {
        return episode.getAncestorsTitles() != null
            && episode.getAncestorsTitles().getBrand() != null;
    }

    private boolean isTopLevelSeriesEpisode(Episode episode) {
        PidReference episodeOf = episode.getEpisodeOf();
        return episodeOf != null
                && "series".equals(episodeOf.getResultType())
                && !hasBrandAncestor(episode);
    }

    @Override
    protected String extractMediaType(NitroItemSource<Episode> source) {
        return source.getProgramme().getMediaType();
    }

    @Override
    protected MasterBrand extractMasterBrand(NitroItemSource<Episode> source) {
        return source.getProgramme().getMasterBrand();
    }
    
}
