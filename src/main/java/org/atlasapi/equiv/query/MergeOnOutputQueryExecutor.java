package org.atlasapi.equiv.query;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.ContentGroup;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.query.KnownTypeQueryExecutor;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;

public class MergeOnOutputQueryExecutor implements KnownTypeQueryExecutor {

	private static final Ordering<Episode> SERIES_ORDER = Ordering.from(new SeriesOrder());
	
	private final KnownTypeQueryExecutor delegate;

	public MergeOnOutputQueryExecutor(KnownTypeQueryExecutor delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public List<Identified> executeUriQuery(Iterable<String> uris, ContentQuery query) {
		ApplicationConfiguration config = query.getConfiguration();
		if (!config.precedenceEnabled()) {
			return delegate.executeUriQuery(uris, query);
		}
		List<Content> content = Lists.newArrayList();
		List<Identified> identified = Lists.newArrayList();
		for (Identified id : delegate.executeUriQuery(uris, query)) {
			if (id instanceof Content) {
				content.add((Content) id);
			} else {
				identified.add((ContentGroup) id);
			}
		}
		return ImmutableList.copyOf(Iterables.<Identified>concat(mergeDuplicates(config, content), identified));
	}
	
	@Override
	public List<Content> discover(ContentQuery query) {
		return merge(query.getConfiguration(), delegate.discover(query));
	}

	private List<Content> merge(ApplicationConfiguration config, List<Content> content) {
		if (!config.precedenceEnabled()) {
			return content;
		}
		return mergeDuplicates(config, content);
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
			
			if (chosen instanceof Container<?>) {
				mergeIn(config, (Container<Item>) chosen, (List<Container<Item>>) notChosen);
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
				return byPublisher.compare(o1.getPublisher(), o2.getPublisher());
			}
		};
	}
	

	public void mergeIn(ApplicationConfiguration config, Item chosen, Iterable<? extends Item> notChosen) {
		for (Item notChosenItem : notChosen) {
			for (Clip clip : notChosenItem.getClips()) {
				chosen.addClip(clip);
			}
		}
		if (config.imagePrecedenceEnabled()) {
			Iterable<Item> all = Iterables.concat(ImmutableList.of(chosen), notChosen);
			List<Item> topImageMatches = toContentOrdering(config.imagePrecedenceOrdering()).leastOf(Iterables.filter(all, HAS_IMAGE_FIELD_SET), 1);
			
			if (!topImageMatches.isEmpty()) {
				Item top = topImageMatches.get(0);
				chosen.setImage(top.getImage());
				chosen.setThumbnail(top.getThumbnail());
			}
		}
	}
	
	private static final Predicate<Content> HAS_IMAGE_FIELD_SET = new Predicate<Content>() {
		@Override
		public boolean apply(Content content) {
			return content.getImage() != null;
		}
	};
	
	public <T extends Item> void mergeIn(ApplicationConfiguration config, Container<T> chosen, List<Container<T>> notChosen) {
		Iterable<T> merged = addMissingItems(chosen, notChosen);
		chosen.setContents(mergeInUnSelectedData(config, merged, notChosen));
	}

	private <T extends Item> Iterable<T> addMissingItems(Container<T> chosen, List<Container<T>> notChosen) {
		List<T> items = findItemsSuitableForMerging(chosen, notChosen);
		if (items.isEmpty()) {
			// nothing to merge
			return chosen.getContents();
		}
		ItemIdStrategy strategy = ItemIdStrategy.findBest(items);
		
		if (strategy == null) {
			return chosen.getContents();
		}
		List<T> matches = Lists.newArrayList();
		for (Container<T> equivalent : notChosen) {
			if (strategy.equals(ItemIdStrategy.findBest(equivalent.getContents()))) {
				matches.addAll(matches);
			}
		}
		return strategy.merge(items, matches);
	}
	
	private <T extends Item> Iterable<T> mergeInUnSelectedData(ApplicationConfiguration config, Iterable<T> chosen, List<Container<T>> notChosenList) {
		Map<T, Collection<T>> alternativeItemLookup = buildChosenItemLookup(chosen, notChosenList);
		for (Entry<T, Collection<T>> entry : alternativeItemLookup.entrySet()) {
			mergeIn(config, entry.getKey(), entry.getValue());
		}
		return chosen;
	}

	private <T extends Item> Map<T, Collection<T>> buildChosenItemLookup(Iterable<T> chosen, List<Container<T>> notChosenList) {
		Multimap<T, T> alternativeItemLookup = HashMultimap.create(); 
		for (T item: chosen) {
	        for (Container<T> notChosen: notChosenList) {
	            for (T notChosenItem: notChosen.getContents()) {
	                if (notChosenItem.getEquivalentTo().contains(item.getCanonicalUri())) {
	                	alternativeItemLookup.put(item, notChosenItem);
	                }
	            }
	        }
	    }
		return alternativeItemLookup.asMap();
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
	

	private <T  extends Item> List<T> findItemsSuitableForMerging(Container<T> brand, Iterable<Container<T>> equivalentBrands) {
		List<T> items = brand.getContents();
		if (items.isEmpty()) {
			for (Container<T> equivalent : equivalentBrands) {
				if (!equivalent.getContents().isEmpty()) {
					if (ItemIdStrategy.findBest(equivalent.getContents()) != null) {
						return equivalent.getContents();
					}
				}
			}
		}
		return items;
	}
}
