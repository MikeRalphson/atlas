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

package org.atlasapi.remotesite.youtube;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Set;

import org.atlasapi.feeds.Defect;
import org.atlasapi.remotesite.BaseSource;
import org.joda.time.Duration;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * Object that wraps data fetched from YouTube
 *
 * @author Robert Chatley (robert@metabroadcast.com)
 */
public class YouTubeSource extends BaseSource {
	
	private static final String ATLAS_GENRES_SCHEME = "http://ref.atlasapi.org/genres/youtube/";
	private static final String ATLAS_TAGS_SCHEME = "http://ref.atlasapi.org/tags/";
	
	private final YouTubeFeedClient.VideoEntry videoEntry;
	
	public YouTubeSource(YouTubeFeedClient.VideoEntry entry, String uri) {
		super(uri);
		videoEntry = entry;
	}
		
	String getVideoTitle() {
		if (videoEntry.title != null) {
			return videoEntry.title;
		} else {
			return null;
		}
	}
	
	String getDescription() {
		if (videoEntry.description != null) {
			return videoEntry.description;
		} else {
			return null;
		}
	}
	
	List<Video> getVideos() {
		List<Video> result = Lists.newArrayList();
		Video video = new Video("", Duration.standardSeconds(videoEntry.duration), videoEntry.player.defaultUrl, 5, true);
		result.add(video);
		return result;
	}
	
	static class Video {

		private final String url;
		private final Duration duration;
		private final String type;
		private final int youtubeFormat;
		private final boolean embeddable;

		public Video(String type, Duration duration, String locationUri, int youtubeFormat, boolean embeddable) {
			this.type = type;
			this.duration = duration;
			this.embeddable = embeddable;
			if (locationUri.contains("&")) {
				url = locationUri.substring(0, locationUri.indexOf('&'));
			} else {
				url = locationUri;
			}
			this.youtubeFormat = youtubeFormat;
		}

		public String getUrl() {
			return url;
		}

		public String getType() {
			return type;
		}
		
		public Duration getDuration() {
			return duration;
		}
		
		public int getYoutubeFormat() {
			return youtubeFormat;
		}

		public boolean isEmbeddable() {
			return embeddable;
		}
		
	}
	
	Set<String> getCategories() {
        Set<String> result = Sets.newHashSet();
        try {
            result.add(ATLAS_GENRES_SCHEME + URLEncoder.encode(videoEntry.category, com.google.common.base.Charsets.UTF_8.name()));
        } catch (UnsupportedEncodingException e) {
            throw new Defect("UTF-8 not found");
        }
        return result;
    }

	Set<String> getTags() {
		Set<String> result = Sets.newHashSet();
		for (String category : videoEntry.tags) {
			try {
				result.add(ATLAS_TAGS_SCHEME + URLEncoder.encode(category.toLowerCase(), com.google.common.base.Charsets.UTF_8.name()));
			} catch (UnsupportedEncodingException e) {
				throw new Defect("UTF-8 not found");
			}
		}
		return result;
	}

	public String getThumbnailImageUri() {
	    if (videoEntry.thumbnail != null) {
	        if (videoEntry.thumbnail.defaultUrl != null) {
	            return videoEntry.thumbnail.defaultUrl;
	        } else if (videoEntry.thumbnail.sqDefault != null) {
	            return videoEntry.thumbnail.sqDefault;
	        }
	    }
	    return null;
	}
	
	public String getImageUri() {
	    if (videoEntry.thumbnail != null && videoEntry.thumbnail.hqDefault != null) {
            return videoEntry.thumbnail.hqDefault;
        }
	    return null;
	}
}
