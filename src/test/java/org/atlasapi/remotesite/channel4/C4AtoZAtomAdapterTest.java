/* Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.remotesite.channel4;

import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Playlist;
import org.atlasapi.persistence.system.RemoteSiteClient;
import org.atlasapi.remotesite.SiteSpecificAdapter;
import org.jmock.Expectations;
import org.jmock.integration.junit3.MockObjectTestCase;

import com.google.common.io.Resources;
import com.sun.syndication.feed.atom.Feed;
/**
 * Unit test for {@link C4AtoZAtomAdapter}.
 * 
 * @author Robert Chatley (robert@metabroadcast.com)
 */
public class C4AtoZAtomAdapterTest extends MockObjectTestCase {

	String uri = "http://www.channel4.com/programmes/atoz/a";
	
	SiteSpecificAdapter<Brand> brandAdapter;
	RemoteSiteClient<Feed> itemClient;
	C4AtoZAtomAdapter adapter;
	
	Brand brand101 = new Brand();
	
	private final AtomFeedBuilder atoza = new AtomFeedBuilder(Resources.getResource(getClass(), "a.atom"));
	private final AtomFeedBuilder atoza2 = new AtomFeedBuilder(Resources.getResource(getClass(), "a2.atom"));
	
	@SuppressWarnings("unchecked")
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		brandAdapter = mock(SiteSpecificAdapter.class);
		itemClient = mock(RemoteSiteClient.class);
		adapter = new C4AtoZAtomAdapter(itemClient, brandAdapter);
	}
	
	public void testPerformsGetCorrespondingGivenUriAndPassesResultToExtractor() throws Exception {
		
		checking(new Expectations() {{
			one(itemClient).get("http://api.channel4.com/programmes/atoz/a.atom"); will(returnValue(atoza.build()));
			one(itemClient).get("http://api.channel4.com/programmes/atoz/a/page-2.atom"); will(returnValue(atoza2.build()));
			allowing(brandAdapter).fetch("http://www.channel4.com/programmes/a-bipolar-expedition"); will(returnValue(brand101));
			allowing(brandAdapter).canFetch("http://www.channel4.com/programmes/a-bipolar-expedition"); will(returnValue(true));
		}});
		
		Playlist playlist = adapter.fetch(uri);
		assertEquals("http://www.channel4.com/programmes/atoz/a", playlist.getCanonicalUri());
		assertEquals(2, playlist.getPlaylists().size());
	}
}
