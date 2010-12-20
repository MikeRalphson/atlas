package org.atlasapi.remotesite.bbc.schedule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Iterator;

import org.atlasapi.feeds.radioplayer.RadioPlayerService;
import org.atlasapi.feeds.radioplayer.RadioPlayerServices;
import org.jmock.Expectations;
import org.jmock.integration.junit3.MockObjectTestCase;
import org.joda.time.DateTime;

import com.google.common.collect.ImmutableSet;
import com.metabroadcast.common.time.Clock;

/**
 * @author Robert Chatley (robert@metabroadcast.com)
 */
public class DatedBbcScheduleUriSourceTest extends MockObjectTestCase {

	Clock clock = mock(Clock.class);
	
	DateTime octoberThirtieth = new DateTime(2009, 10, 30, 14, 15, 00, 000);
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		checking(new Expectations() {{ 
			allowing(clock).now(); will(returnValue(octoberThirtieth));
		}});
	}
	
	public void testGeneratesUrisForCurrentDayByDefault() throws Exception {
		
		DatedBbcScheduleUriSource uriBuilder = new DatedBbcScheduleUriSource(clock);
		
		Iterator<String> source = uriBuilder.iterator();

		assertThat(source.next(), is("http://www.bbc.co.uk/bbcone/programmes/schedules/london/2009/10/30.xml"));
		assertThat(source.next(), is("http://www.bbc.co.uk/bbctwo/programmes/schedules/england/2009/10/30.xml"));
		assertThat(source.next(), is("http://www.bbc.co.uk/bbcthree/programmes/schedules/2009/10/30.xml"));
		assertThat(source.next(), is("http://www.bbc.co.uk/bbcfour/programmes/schedules/2009/10/30.xml"));
		for (RadioPlayerService service : RadioPlayerServices.services) {
			assertThat(source.next(), is(service.getScheduleUri() + "/2009/10/30.xml"));
		}
		
		assertFalse(source.hasNext());
		
	}
	
	public void testGeneratesUrisForDaysInTheFuture() throws Exception {
		
		DatedBbcScheduleUriSource uriBuilder = new DatedBbcScheduleUriSource(clock);
		
		uriBuilder.setDaysToLookAhead(2);
		
		Iterator<String> source = uriBuilder.iterator();
		
		for (String suffix : ImmutableSet.of("2009/10/30.xml", "2009/10/31.xml", "2009/11/01.xml")) {
			assertThat(source.next(), is("http://www.bbc.co.uk/bbcone/programmes/schedules/london/" + suffix));
			assertThat(source.next(), is("http://www.bbc.co.uk/bbctwo/programmes/schedules/england/" + suffix));
			assertThat(source.next(), is("http://www.bbc.co.uk/bbcthree/programmes/schedules/" + suffix));
			assertThat(source.next(), is("http://www.bbc.co.uk/bbcfour/programmes/schedules/" + suffix));
			for (RadioPlayerService service : RadioPlayerServices.services) {
				assertThat(source.next(), is(service.getScheduleUri() + "/" + suffix));
			}
		}
		
		assertFalse(source.hasNext());
		
	}
}
