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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import junit.framework.TestCase;

import org.atlasapi.remotesite.FetchException;
import org.atlasapi.remotesite.youtube.YouTubeFeedClient.VideoEntry;

/**
 * Test of the behaviour of the third-party YouTube GData client from Google.
 * Exercised through our thin wrapper.
 *
 * @author Robert Chatley (robert@metabroadcast.com)
 */
public class YouTubeGdataClientTest extends TestCase {

	YouTubeGDataClient gdataClient = new YouTubeGDataClient();

	public void testCanRetrieveDataRelatingToGivenYouTubePage() throws Exception {
		VideoEntry entry = gdataClient.get("http://www.youtube.com/watch?v=pdyYe7sDlhA");
		assertThat(entry.title, containsString("BBC News 24"));
		assertNotNull(entry.thumbnail);
		assertNotNull(entry.thumbnail.hqDefault);
		assertFalse(entry.tags.isEmpty());
		assertNotNull(entry.category);
		assertNotNull(entry.player.defaultUrl);
	}
	
	public void testhrowsExceptionIfSubmittedUriDoesNotContainVideoId() throws Exception {
		
		try {
			gdataClient.get("http://uk.youtube.com/watch/blah");
		} catch (FetchException fe) {
			assertThat(fe.getMessage(), containsString("URI did not contain a recognised video id"));
		}
	}
}
