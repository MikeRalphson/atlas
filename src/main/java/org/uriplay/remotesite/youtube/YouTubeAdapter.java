/* Copyright 2009 British Broadcasting Corporation
   Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.uriplay.remotesite.youtube;

import java.util.regex.Pattern;

import org.uriplay.media.entity.Item;
import org.uriplay.persistence.system.RemoteSiteClient;
import org.uriplay.persistence.system.RequestTimer;
import org.uriplay.remotesite.ContentExtractor;
import org.uriplay.remotesite.FetchException;
import org.uriplay.remotesite.SiteSpecificAdapter;

import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.util.ResourceNotFoundException;

/**
 * {@link SiteSpecificAdapter} for querying data about video clips from YouTube.
 *
 * @author Robert Chatley (robert@metabroadcast.com)
 */
public class YouTubeAdapter implements SiteSpecificAdapter<Item> {

	private static final Pattern YOUTUBE_CANONICAL_URI_PATTERN = Pattern.compile("http://www\\.youtube\\.com/watch\\?v=[^\\./&=]+");
	
	private final RemoteSiteClient<VideoEntry> gdataClient;
	private final ContentExtractor<YouTubeSource, Item> contentExtractor;
	
	public YouTubeAdapter() {
		this(new YouTubeGDataClient(), new YouTubeGraphExtractor()); 
	}
	
	YouTubeAdapter(RemoteSiteClient<VideoEntry> gdataClient, ContentExtractor<YouTubeSource, Item> youTubeGraphExtractor) {
		this.gdataClient = gdataClient;
		this.contentExtractor = youTubeGraphExtractor;
	}

	@Override
	public Item fetch(String uri, RequestTimer timer) {
		try {
			timer.nest();
			timer.start(this, "Querying YouTube api for data about " + uri);
			VideoEntry videoEntry = gdataClient.get(uri);
			timer.stop(this, "Querying YouTube api for data about " + uri);
			timer.unnest();
			return contentExtractor.extract(new YouTubeSource(videoEntry, uri));
		} catch (ResourceNotFoundException e) {
			throw new FetchException("Video not found on YouTube: " + uri, e);
		} catch (Exception e) {
			throw new FetchException("Failed to fetch: " + uri, e);
		}
	}

	public boolean canFetch(String uri) {
		return YOUTUBE_CANONICAL_URI_PATTERN.matcher(uri).matches();
	}
}
