package org.atlasapi.remotesite.bbc.schedule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.NullAdapterLog;
import org.atlasapi.persistence.system.RemoteSiteClient;
import org.atlasapi.remotesite.bbc.BbcProgrammeAdapter;
import org.atlasapi.remotesite.bbc.schedule.ChannelSchedule.Programme;
import org.jmock.Expectations;
import org.jmock.integration.junit3.MockObjectTestCase;

import com.google.common.collect.Lists;

@SuppressWarnings("unchecked")
public class BbcScheduledProgrammeFetcherTest extends MockObjectTestCase {

	RemoteSiteClient<ChannelSchedule> scheduleClient = mock(RemoteSiteClient.class);

	//BbcProgrammeAdapter fetcher = new TestBbcProgrammeAdapter(new NullAdapterLog());

	ContentResolver localFetcher = mock(ContentResolver.class);
	ContentWriter writer = mock(ContentWriter.class);
	
	ChannelSchedule schedule = new ChannelSchedule();
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		schedule.withProgrammes(Lists.newArrayList(new Programme("episode", "b00abcd"), new Programme("episode", "b00efgh"), new Programme("brand", "b00xyz")));
	}
	
	final Episode containedInBrand = new Episode("containedInBrandUri", "containedInBrandCurie", Publisher.BBC);
	final Brand brand = new Brand("brandUri", "brandCurie", Publisher.BBC);
	final Episode replacingEpisode = new Episode("containedInBrandUri", "replacingCurie", Publisher.BBC);
	final Item item = new Item("itemUri", "itemCurie", Publisher.BBC);
	
	public void testFetchExtractedEpisodePid() throws Exception {
    	fail("update this test");
//        brand.setContents(containedInBrand);
//        
//        replacingEpisode.setContainer(brand);
//		
//		BbcScheduledProgrammeUpdater scheduleFetcher = new BbcScheduledProgrammeUpdater(scheduleClient, localFetcher, fetcher, writer, Lists.newArrayList("http://www.bbc.co.uk/bbctwo/programmes/schedules/england/2009/11/05.xml"), new NullAdapterLog());
//		
//		checking(new Expectations() {{ 
//			one(scheduleClient).get("http://www.bbc.co.uk/bbctwo/programmes/schedules/england/2009/11/05.xml"); will(returnValue(schedule));
//
//			one(localFetcher).findByCanonicalUri("brandUri"); will(returnValue(brand));
//			one(writer).createOrUpdate(with(any(Brand.class)));
//			one(writer).createOrUpdate(with(any(Item.class)));
//		}});
//
//		scheduleFetcher.run();
//		
//		assertThat(brand.getContents().size(), is(equalTo(1)));
//		assertThat(brand.getContents().get(0).getCurie(), is(equalTo("replacingCurie")));
	}
	
//	private class TestBbcProgrammeAdapter extends BbcProgrammeAdapter {
//
//        public TestBbcProgrammeAdapter(AdapterLog log) {
//            super(log);
//        }
//	    
//        @Override
//        public Content fetch(String uri, boolean hydrate) {
//            return uri.endsWith("b00abcd") ? replacingEpisode : item;
//        }
//	}
}
