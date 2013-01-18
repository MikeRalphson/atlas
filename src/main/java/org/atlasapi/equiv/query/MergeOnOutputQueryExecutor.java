package org.atlasapi.equiv.query;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.media.common.Id;
import org.atlasapi.media.content.Container;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.entity.Broadcast;
import org.atlasapi.media.entity.Certificate;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.ReleaseDate;
import org.atlasapi.media.entity.Subtitles;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.content.query.KnownTypeQueryExecutor;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

public class MergeOnOutputQueryExecutor implements KnownTypeQueryExecutor {

    private static final Ordering<Episode> SERIES_ORDER = Ordering.from(new SeriesOrder());
    private final KnownTypeQueryExecutor delegate;

    public MergeOnOutputQueryExecutor(KnownTypeQueryExecutor delegate) {
        this.delegate = delegate;
    }

    @Override
    public Map<Id, List<Identified>> executeUriQuery(Iterable<String> uris, final ContentQuery query) {
        return mergeResults(query, delegate.executeUriQuery(uris, query));
    }

    @Override
    public Map<Id, List<Identified>> executeIdQuery(Iterable<Id> ids, final ContentQuery query) {
        return mergeResults(query, delegate.executeIdQuery(ids, query));
    }

    private Map<Id, List<Identified>> mergeResults(final ContentQuery query, Map<Id, List<Identified>> unmergedResult) {
        ApplicationConfiguration config = query.getConfiguration();
        if (!config.precedenceEnabled()) {
            return unmergedResult;
        }
        return Maps.transformValues(unmergedResult, new Function<List<Identified>, List<Identified>>() {

            @Override
            public List<Identified> apply(List<Identified> input) {

                List<Content> content = Lists.newArrayList();
                List<Identified> ids = Lists.newArrayList();

                for (Identified ided : input) {
                    if (ided instanceof Content) {
                        content.add((Content) ided);
                    } else {
                        ids.add(ided);
                    }
                }

                return ImmutableList.copyOf(Iterables.concat(mergeDuplicates(query.getConfiguration(), content), ids));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends Content> List<T> mergeDuplicates(ApplicationConfiguration config, List<T> contents) {
        Comparator<Content> contentComparator = toContentOrdering(config.publisherPrecedenceOrdering());

        List<T> merged = Lists.newArrayListWithCapacity(contents.size());
        Set<T> processed = Sets.newHashSet();

        for (T content : contents) {
            if (processed.contains(content)) {
                continue;
            }
            List<T> same = findSame(content, contents);
            processed.addAll(same);

            Collections.sort(same, contentComparator);

            T chosen = same.get(0);

            // defend against broken transitive equivalence
            if (merged.contains(chosen)) {
                continue;
            }

            List<T> notChosen = same.subList(1, same.size());

            if (chosen instanceof Container) {
                mergeIn(config, (Container) chosen, (List<Container>) notChosen);
            }
            if (chosen instanceof Item) {
                mergeIn(config, (Item) chosen, (List<Item>) notChosen);
            }
            merged.add(chosen);
        }
        return merged;
    }

    @SuppressWarnings("unchecked")
    private <T extends Content> List<T> findSame(T brand, Iterable<T> contents) {
        List<T> same = Lists.newArrayList(brand);
        for (T possiblyEquivalent : contents) {
            if (!brand.equals(possiblyEquivalent) && possiblyEquivalent.isEquivalentTo(brand)) {
                same.add(possiblyEquivalent);
            }
        }
        return same;
    }

    private static Ordering<Content> toContentOrdering(final Ordering<Publisher> byPublisher) {
        return new Ordering<Content>() {

            @Override
            public int compare(Content o1, Content o2) {
                int cmp = byPublisher.compare(o1.getPublisher(), o2.getPublisher());
                if (cmp != 0) {
                    return cmp;
                }
                return o1.getCanonicalUri().compareTo(o2.getCanonicalUri());
            }
        };
    }

    public <T extends Item> void mergeIn(ApplicationConfiguration config, T chosen, Iterable<T> notChosen) {
        for (Item notChosenItem : notChosen) {
            for (Clip clip : notChosenItem.getClips()) {
                chosen.addClip(clip);
            }
        }
        applyImagePrefs(config, chosen, notChosen);
        mergeTopics(chosen, notChosen);
        mergeKeyPhrases(chosen, notChosen);
        mergeVersions(chosen, notChosen);
        if (chosen instanceof Film) {
            mergeFilmProperties(config, (Film) chosen, Iterables.filter(notChosen, Film.class));
        }
    }

    private <T extends Content> void mergeKeyPhrases(T chosen, Iterable<T> notChosen) {
        chosen.setKeyPhrases(Iterables.concat(chosen.getKeyPhrases(),
                Iterables.concat(Iterables.transform(notChosen, new Function<T, Iterable<KeyPhrase>>() {

            @Override
            public Set<KeyPhrase> apply(T input) {
                return input.getKeyPhrases();
            }
        }))));
    }

    private <T extends Content> void mergeTopics(T chosen, Iterable<T> notChosen) {
        chosen.setTopicRefs(Iterables.concat(
                Iterables.transform(chosen.getTopicRefs(), new TopicPublisherSetter(chosen)),
                Iterables.concat(Iterables.transform(notChosen,
                new Function<T, Iterable<TopicRef>>() {

                    @Override
                    public Iterable<TopicRef> apply(T input) {
                        return Iterables.transform(input.getTopicRefs(), new TopicPublisherSetter(input));
                    }
                }))));
    }

    private void mergeFilmProperties(ApplicationConfiguration config, Film chosen, Iterable<Film> notChosen) {
        Builder<Subtitles> subtitles = ImmutableSet.<Subtitles>builder().addAll(chosen.getSubtitles());
        Builder<String> languages = ImmutableSet.<String>builder().addAll(chosen.getLanguages());
        Builder<Certificate> certs = ImmutableSet.<Certificate>builder().addAll(chosen.getCertificates());
        Builder<ReleaseDate> releases = ImmutableSet.<ReleaseDate>builder().addAll(chosen.getReleaseDates());

        for (Film film : notChosen) {
            subtitles.addAll(film.getSubtitles());
            languages.addAll(film.getLanguages());
            certs.addAll(film.getCertificates());
            releases.addAll(film.getReleaseDates());
        }

        chosen.setSubtitles(subtitles.build());
        chosen.setLanguages(languages.build());
        chosen.setCertificates(certs.build());
        chosen.setReleaseDates(releases.build());

        if (config.peoplePrecedenceEnabled()) {
            Iterable<Film> all = Iterables.concat(ImmutableList.of(chosen), notChosen);
            List<Film> topFilmMatches = toContentOrdering(config.peoplePrecedenceOrdering()).leastOf(Iterables.filter(all, HAS_PEOPLE), 1);

            if (!topFilmMatches.isEmpty()) {
                Film top = topFilmMatches.get(0);
                chosen.setPeople(top.getPeople());
            }
        }
    }

    private <T extends Content> void applyImagePrefs(ApplicationConfiguration config, T chosen, Iterable<T> notChosen) {
        if (config.imagePrecedenceEnabled()) {
            Iterable<T> all = Iterables.concat(ImmutableList.of(chosen), notChosen);
            List<T> topImageMatches = toContentOrdering(config.imagePrecedenceOrdering()).leastOf(Iterables.filter(all, HAS_IMAGE_FIELD_SET), 1);

            if (!topImageMatches.isEmpty()) {
                T top = topImageMatches.get(0);
                chosen.setImage(top.getImage());
                chosen.setThumbnail(top.getThumbnail());
            }
        }
    }

    private <T extends Item> void mergeVersions(T chosen, Iterable<T> notChosen) {
        for (T notChosenItem : notChosen) {
            for (Version version : notChosenItem.getVersions()) {
                // TODO When we have more granular precedence this broadcast masking can be removed
                version.setBroadcasts(Sets.<Broadcast>newHashSet());
                chosen.addVersion(version);
            }
        }
    }
    private static final Predicate<Content> HAS_IMAGE_FIELD_SET = new Predicate<Content>() {

        @Override
        public boolean apply(Content content) {
            return content.getImage() != null;
        }
    };
    private static final Predicate<Film> HAS_PEOPLE = new Predicate<Film>() {

        @Override
        public boolean apply(Film film) {
            return film.getPeople() != null && !film.getPeople().isEmpty();
        }
    };

    public <T extends Item> void mergeIn(ApplicationConfiguration config, Container chosen, List<Container> notChosen) {
        mergeTopics(chosen, notChosen);
        mergeKeyPhrases(chosen, notChosen);
        applyImagePrefs(config, chosen, notChosen);
    }

    enum ItemIdStrategy {

        SERIES_EPISODE_NUMBER {

            @Override
            public Predicate<Item> match() {
                return new Predicate<Item>() {

                    @Override
                    public boolean apply(Item item) {
                        if (item instanceof Episode) {
                            Episode episode = (Episode) item;
                            return episode.getSeriesNumber() != null && episode.getEpisodeNumber() != null;
                        }
                        return false;
                    }
                };
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T extends Item> Iterable<T> merge(List<T> items, List<T> matches) {
                Map<SeriesAndEpisodeNumber, Episode> chosenItemLookup = Maps.newHashMap();
                for (T item : Iterables.concat(items, matches)) {
                    Episode episode = (Episode) item;
                    SeriesAndEpisodeNumber se = new SeriesAndEpisodeNumber(episode);
                    if (!chosenItemLookup.containsKey(se)) {
                        chosenItemLookup.put(se, episode);
                    } else {
                        Item chosen = chosenItemLookup.get(se);
                        for (Clip clip : item.getClips()) {
                            chosen.addClip(clip);
                        }
                    }
                }

                return (Iterable<T>) SERIES_ORDER.immutableSortedCopy(chosenItemLookup.values());
            }
        };

        protected abstract Predicate<Item> match();

        static ItemIdStrategy findBest(Iterable<? extends Item> items) {
            if (Iterables.all(items, ItemIdStrategy.SERIES_EPISODE_NUMBER.match())) {
                return SERIES_EPISODE_NUMBER;
            }
            return null;
        }

        public abstract <T extends Item> Iterable<T> merge(List<T> items, List<T> matches);
    }

    private final static class TopicPublisherSetter implements Function<TopicRef, TopicRef> {

        private final Content publishedContent;

        public TopicPublisherSetter(Content publishedContent) {
            this.publishedContent = publishedContent;
        }

        @Override
        public TopicRef apply(TopicRef input) {
            input.setPublisher(publishedContent.getPublisher());
            return input;
        }
    }
}
