package org.atlasapi.remotesite.channel4;

import java.util.Set;

import org.atlasapi.media.content.Broadcast;
import org.atlasapi.media.content.ContentResolver;
import org.atlasapi.media.content.Encoding;
import org.atlasapi.media.content.Identified;
import org.atlasapi.media.content.Item;
import org.atlasapi.media.content.Location;
import org.atlasapi.media.content.Publisher;
import org.atlasapi.media.content.Version;
import org.joda.time.DateTime;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.time.DateTimeZones;

class C4PreviousVersionDataMerger {

	private final ContentResolver contentResolver;

	public C4PreviousVersionDataMerger(ContentResolver contentResolver) {
		this.contentResolver = contentResolver;
	}
	
	void merge(Item item) {
		
		String itemUri = item.getCanonicalUri();
        Maybe<Identified> maybeOldItem = contentResolver.findByCanonicalUris(ImmutableList.of(itemUri)).get(itemUri);
		
        if (!maybeOldItem.hasValue()) {
            // can't merge if this is the first time we've seen the item
            return;
        }
		
        Item oldItem = (Item) maybeOldItem.requireValue();
        
        if(oldItem.nativeVersions().isEmpty()) {
            return;
        }
        
		if (!Publisher.C4.equals(oldItem.getPublisher())) {
			// sanity check, if the item we got back was not in the C4 namespace
			// then we shouldn't do anything
			return;
		}
		
		Set<Version> versions = item.nativeVersions();
		if (Iterables.isEmpty(versions)) {
			item.setVersions(oldItem.nativeVersions());
			for (Version version : item.nativeVersions()) {
				markLocationsAsUnavailable(version);
			}
		}  else {
			Version version = Iterables.get(versions, 0);
			addInactiveBroadcasts(version, oldItem);
			addUnavailableLocations(version, oldItem);
		}
	}
	
	private void addInactiveBroadcasts(Version version, Item oldEpisode) {
	    for (Version oldVersion: oldEpisode.nativeVersions()) {
	        for (Broadcast broadcast: oldVersion.getBroadcasts()) {
	            if (!version.getBroadcasts().contains(broadcast)) {
	                version.addBroadcast(broadcast);
	            }
	        }
	    }
	}
	
	private void addUnavailableLocations(Version version, Item oldEpisode) {
	    if (version.getManifestedAs().isEmpty()) {
	        Version oldVersion = oldEpisode.nativeVersions().iterator().next();
	        version.setManifestedAs(oldVersion.getManifestedAs());
	        markLocationsAsUnavailable(version);
	    }
    }

	private void markLocationsAsUnavailable(Version version) {
		for (Encoding encoding: version.getManifestedAs()) {
		    for (Location location: encoding.getAvailableAt()) {
		        if (location.getAvailable()) {
		            location.setLastUpdated(new DateTime(DateTimeZones.UTC));
		        }
		        location.setAvailable(false);
		    }
		}
	}
}
