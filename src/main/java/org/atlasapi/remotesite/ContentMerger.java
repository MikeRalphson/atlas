package org.atlasapi.remotesite;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Film;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.TopicRef;
import org.atlasapi.media.entity.Version;

import com.google.common.base.Equivalence;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public class ContentMerger {

    private static interface TopicMergeStrategy {
        Item mergeTopics(Item current, Item extracted);
    }

    private static interface VersionMergeStrategy {
        Item mergeVersions(Item current, Item extracted);
    }

    public static abstract class MergeStrategy {
        private MergeStrategy(){}

        public static final LeaveEverythingAlone KEEP = new LeaveEverythingAlone();
        public static final ReplaceEverything REPLACE = new ReplaceEverything();
        public static final StandardMerge MERGE = new StandardMerge();

        public static ReplaceTopicsBasedOnEquivalence replaceTopicsBasedOn(final Equivalence<TopicRef> equivalence) {
            Preconditions.checkNotNull(equivalence);
            return new ReplaceTopicsBasedOnEquivalence(equivalence);
        }
    }

    private final VersionMergeStrategy versionMergeStrategy;
    private final TopicMergeStrategy topicsMergeStrategy;

    public ContentMerger(VersionMergeStrategy versionMergeStrategy, TopicMergeStrategy topicsMergeStrategy) {
        this.versionMergeStrategy = versionMergeStrategy;
        this.topicsMergeStrategy = topicsMergeStrategy;
    }

    public Item merge(Item current, Item extracted) {

        current = versionMergeStrategy.mergeVersions(current, extracted);
        current = topicsMergeStrategy.mergeTopics(current, extracted);
        current = mergeContents(current, extracted);

        if ( current.getContainer() == null
                || extracted.getContainer() == null
                || !current.getContainer().getUri().equals(extracted.getContainer().getUri())) {
            current.setParentRef(extracted.getContainer());
        }

        if (current instanceof Episode && extracted instanceof Episode) {
            Episode currentEp = (Episode) current;
            Episode extractedEp = (Episode) extracted;
            currentEp.setEpisodeNumber(extractedEp.getEpisodeNumber());

            if ( currentEp.getSeriesRef() == null
                    || extractedEp.getSeriesRef() == null
                    || !currentEp.getSeriesRef().getUri().equals(extractedEp.getSeriesRef().getUri())) {
                currentEp.setSeriesRef(extractedEp.getSeriesRef());
            }
        }

        if (current instanceof Film && extracted instanceof Film) {
            Film currentFilm = (Film) current;
            Film extractedFilm = (Film) extracted;
            currentFilm.setYear(extractedFilm.getYear());
        }

        return current;
    }

    public Container merge(Container current, Container extracted) {
        current = mergeContents(current, extracted);
        if (current instanceof Series && extracted instanceof Series) {
            ((Series) current).withSeriesNumber(((Series) extracted).getSeriesNumber());
            ((Series) current).setParentRef(((Series) extracted).getParent());
        }
        return current;
    }

    private <C extends Content> C mergeContents(C current, C extracted) {
        current.setActivelyPublished(extracted.isActivelyPublished());
        current.setAliasUrls(extracted.getAliasUrls());
        current.setAliases(extracted.getAliases());
        current.setTitle(extracted.getTitle());
        current.setDescription(extracted.getDescription());
        current.setShortDescription(extracted.getShortDescription());
        current.setMediumDescription(extracted.getMediumDescription());
        current.setLongDescription(current.getLongDescription());
        current.setImage(extracted.getImage());
        if (extracted.getImages() != null) {
            current.setImages(extracted.getImages());
        }
        current.setYear(extracted.getYear());
        current.setGenres(extracted.getGenres());
        current.setPeople(extracted.people());
        current.setLanguages(extracted.getLanguages());
        current.setCertificates(extracted.getCertificates());
        current.setMediaType(extracted.getMediaType());
        current.setSpecialization(extracted.getSpecialization());
        current.setLastUpdated(extracted.getLastUpdated());
        current.setClips(extracted.getClips());
        current.setEquivalentTo(extracted.getEquivalentTo());
        current.setRelatedLinks(extracted.getRelatedLinks());
        current.setPresentationChannel(extracted.getPresentationChannel());
        current.setMediaType(extracted.getMediaType());
        current.setSpecialization(extracted.getSpecialization());
        
        return current;
    }

    public static Container asContainer(Identified identified) {
        return castTo(identified, Container.class);
    }

    public static Item asItem(Identified identified) {
        return castTo(identified, Item.class);
    }

    private static <T> T castTo(Identified identified, Class<T> cls) {
        try {
            return cls.cast(identified);
        } catch (ClassCastException e) {
            throw new ClassCastException(String.format("%s: expected %s got %s",
                    identified.getCanonicalUri(),
                    cls.getSimpleName(),
                    identified.getClass().getSimpleName()));
        }
    }

    private static class LeaveEverythingAlone extends MergeStrategy implements TopicMergeStrategy, VersionMergeStrategy {
        @Override
        public Item mergeTopics(Item current, Item extracted) {
            return current;
        }

        @Override
        public Item mergeVersions(Item current, Item extracted) {
            return current;
        }
    }

    private static class ReplaceEverything extends MergeStrategy implements TopicMergeStrategy, VersionMergeStrategy {
        @Override
        public Item mergeTopics(Item current, Item extracted) {
            current.setTopicRefs(extracted.getTopicRefs());
            return current;
        }

        @Override
        public Item mergeVersions(Item current, Item extracted) {
            current.setVersions(extracted.getVersions());
            return current;
        }
    }

    private static class StandardMerge extends MergeStrategy implements VersionMergeStrategy {
        @Override
        public Item mergeVersions(Item current, Item extracted) {
            Map<String, Version> mergedVersions = Maps.newHashMap();
            for (Version version : current.getVersions()) {
                mergedVersions.put(version.getCanonicalUri(), version);
            }
            for (Version version : extracted.getVersions()) {
                if (mergedVersions.containsKey(version.getCanonicalUri())) {
                    Version mergedVersion = mergedVersions.get(version.getCanonicalUri());
                    mergedVersion.setBroadcasts(Sets.union(version.getBroadcasts(), mergedVersion.getBroadcasts()));
                    mergedVersion.setManifestedAs(version.getManifestedAs());
                    mergedVersion.setRestriction(version.getRestriction());
                    mergedVersions.put(version.getCanonicalUri(), mergedVersion);
                } else {
                    mergedVersions.put(version.getCanonicalUri(), version);
                }
            }
            current.setVersions(Sets.newHashSet(mergedVersions.values()));
            return current;
        }
    }

    private static class ReplaceTopicsBasedOnEquivalence extends MergeStrategy implements TopicMergeStrategy {
        private final Equivalence<TopicRef> equivalence;

        private ReplaceTopicsBasedOnEquivalence(Equivalence<TopicRef> equivalence) {
            this.equivalence = equivalence;
        }

        @Override
        public Item mergeTopics(Item current, Item extracted) {
            Set<Equivalence.Wrapper<TopicRef>> mergedRefs = new HashSet<>();

            for (TopicRef topicRef : current.getTopicRefs()) {
                mergedRefs.add(equivalence.wrap(topicRef));
            }
            for (TopicRef topicRef : extracted.getTopicRefs()) {
                Equivalence.Wrapper<TopicRef> wrapped = equivalence.wrap(topicRef);
                if (! mergedRefs.add(wrapped)) {
                    mergedRefs.remove(wrapped);  // force replacement
                    mergedRefs.add(wrapped);
                }
            }

            current.setTopicRefs(Iterables.transform(mergedRefs, new Function<Equivalence.Wrapper<TopicRef>, TopicRef>() {
                @Override
                public TopicRef apply(Equivalence.Wrapper<TopicRef> input) {
                    return input.get();
                }
            }));

            return current;
        }
    }

}
