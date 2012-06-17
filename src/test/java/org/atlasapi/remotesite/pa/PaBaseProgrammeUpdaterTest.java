package org.atlasapi.remotesite.pa;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import junit.framework.TestCase;

import org.atlasapi.media.channel.Channel;
import org.atlasapi.media.channel.ChannelResolver;
import org.atlasapi.media.content.Brand;
import org.atlasapi.media.content.Broadcast;
import org.atlasapi.media.content.ContentResolver;
import org.atlasapi.media.content.ContentWriter;
import org.atlasapi.media.content.Identified;
import org.atlasapi.media.content.Item;
import org.atlasapi.media.content.MediaType;
import org.atlasapi.media.content.MongoContentResolver;
import org.atlasapi.media.content.MongoContentWriter;
import org.atlasapi.media.content.Publisher;
import org.atlasapi.media.content.Specialization;
import org.atlasapi.media.content.Version;
import org.atlasapi.media.content.util.LookupResolvingContentResolver;
import org.atlasapi.persistence.content.people.DummyItemsPeopleWriter;
import org.atlasapi.persistence.content.schedule.MongoScheduleStore;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.SystemOutAdapterLog;
import org.atlasapi.persistence.lookup.MongoLookupEntryStore;
import org.atlasapi.remotesite.channel4.epg.BroadcastTrimmer;
import org.atlasapi.remotesite.pa.data.DefaultPaProgrammeDataStore;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.Interval;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.util.concurrent.MoreExecutors;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.persistence.MongoTestHelper;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.time.DateTimeZones;
import com.metabroadcast.common.time.TimeMachine;
import org.junit.Ignore;

@Ignore
@RunWith(JMock.class)
public class PaBaseProgrammeUpdaterTest extends TestCase {

    private Mockery context = new Mockery();
    
    private PaProgDataProcessor programmeProcessor;

    private TimeMachine clock = new TimeMachine();
    private AdapterLog log = new SystemOutAdapterLog();
    private ContentResolver resolver;
    private ContentWriter contentWriter;
	private MongoScheduleStore scheduleWriter;

	private ChannelResolver channelResolver;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        DatabasedMongo db = MongoTestHelper.anEmptyTestDatabase();
        MongoLookupEntryStore lookupStore = new MongoLookupEntryStore(db);
        resolver = new LookupResolvingContentResolver(new MongoContentResolver(db), lookupStore);
        
        channelResolver = new DummyChannelResolver();
        contentWriter = new MongoContentWriter(db, lookupStore, clock);
        programmeProcessor = new PaProgrammeProcessor(contentWriter, resolver, channelResolver, new DummyItemsPeopleWriter(), log);
        scheduleWriter = new MongoScheduleStore(db, resolver, channelResolver);
    }

    @Test
    public void testShouldCreateCorrectPaData() throws Exception {
        TestPaProgrammeUpdater updater = new TestPaProgrammeUpdater(programmeProcessor, channelResolver, log, scheduleWriter, ImmutableList.of(new File(Resources.getResource("20110115_tvdata.xml").getFile())), null);
        updater.run();
        Identified content = null;

        content = resolver.findByCanonicalUris(ImmutableList.of("http://pressassociation.com/brands/122139")).get("http://pressassociation.com/brands/122139").requireValue();

        assertNotNull(content);
        assertTrue(content instanceof Brand);
        Brand brand = (Brand) content;
        assertFalse(brand.getChildRefs().isEmpty());
        assertNotNull(brand.getImage());

        Item item = loadItemAtPosition(brand, 0);
        assertTrue(item.getCanonicalUri().contains("episodes"));
        assertNotNull(item.getImage());
        assertFalse(item.getVersions().isEmpty());
        assertEquals(MediaType.VIDEO, item.getMediaType());
        assertEquals(Specialization.TV, item.getSpecialization());

        assertEquals(17, item.people().size());
        assertEquals(14, item.actors().size());

        Version version = item.getVersions().iterator().next();
        assertFalse(version.getBroadcasts().isEmpty());

        Broadcast broadcast = version.getBroadcasts().iterator().next();
        assertEquals("pa:71118471", broadcast.getSourceId());

        updater.run();
        Thread.sleep(1000);

        content = resolver.findByCanonicalUris(ImmutableList.of("http://pressassociation.com/brands/122139")).get("http://pressassociation.com/brands/122139").requireValue();
        assertNotNull(content);
        assertTrue(content instanceof Brand);
        brand = (Brand) content;
        assertFalse(brand.getChildRefs().isEmpty());

        item =  loadItemAtPosition(brand, 0);
        assertFalse(item.getVersions().isEmpty());

        version = item.getVersions().iterator().next();
        assertFalse(version.getBroadcasts().isEmpty());

        broadcast = version.getBroadcasts().iterator().next();
        assertEquals("pa:71118471", broadcast.getSourceId());

//        // Test people get created
//        for (CrewMember crewMember : item.people()) {
//            content = store.findByCanonicalUri(crewMember.getCanonicalUri());
//            assertTrue(content instanceof Person);
//            assertEquals(crewMember.name(), ((Person) content).name());
//        }
    }
    
    @Test
    public void testBroadcastsTrimmerWindowNoTimesInFile() {
        
        final BroadcastTrimmer trimmer = context.mock(BroadcastTrimmer.class);
        final Interval firstFileInterval = new Interval(new DateTime(2011, DateTimeConstants.JANUARY, 15, 6, 0, 0, 0, DateTimeZones.LONDON), new DateTime(2011, DateTimeConstants.JANUARY, 16, 6, 0, 0, 0, DateTimeZones.LONDON));
         
        context.checking(new Expectations() {{
            oneOf (trimmer).trimBroadcasts(firstFileInterval, channelResolver.fromUri("http://www.bbc.co.uk/bbcone").requireValue(), ImmutableMap.of("pa:71118471", "http://pressassociation.com/episodes/1424497"));
        }});
        
        TestPaProgrammeUpdater updater = new TestPaProgrammeUpdater(programmeProcessor, channelResolver, log, scheduleWriter, ImmutableList.of(new File(Resources.getResource("20110115_tvdata.xml").getFile())), trimmer);
        updater.run();
    }

    @Test
    public void testBroadcastTrimmerWindowTimesInFile() {
        
        final BroadcastTrimmer trimmer = context.mock(BroadcastTrimmer.class);
        final Interval fileInterval = new Interval(new DateTime(2011, DateTimeConstants.JANUARY, 15, 21, 40, 0, 0, DateTimeZones.LONDON), new DateTime(2011, DateTimeConstants.JANUARY, 15, 23, 30, 0, 0, DateTimeZones.LONDON));  
        
        context.checking(new Expectations() {{
            oneOf (trimmer).trimBroadcasts(fileInterval, channelResolver.fromUri("http://www.bbc.co.uk/bbcone").requireValue(), ImmutableMap.of("pa:71118472", "http://pressassociation.com/episodes/1424497"));
        }});
        
        TestPaProgrammeUpdater updater = new TestPaProgrammeUpdater(programmeProcessor, channelResolver, log, scheduleWriter, ImmutableList.of(new File(Resources.getResource("201202251115_20110115_tvdata.xml").getFile())), trimmer);
        updater.run();
    }
    
    @Test
    public void testGroupAndOrderFilesByDay() throws Exception {
    	File file1 = new File(new URI("file:/data/pa/TV/201101010145_20110102_tvdata.xml"));
    	File file2 = new File(new URI("file:/data/pa/TV/20110101_tvdata.xml"));
    	File file3 = new File(new URI("file:/data/pa/TV/201101040130_20110101_tvdata.xml"));
    	File file4 = new File(new URI("file:/data/pa/TV/201101050145_20110101_tvdata.xml"));
    	File file5 = new File(new URI("file:/data/pa/TV/20110102_tvdata.xml"));
    	File file6 = new File(new URI("file:/data/pa/TV/201101010200_20110102_tvdata.xml"));
    	File file7 = new File(new URI("file:/data/pa/TV/20110103_tvdata.xml"));
    	File file8 = new File(new URI("file:/data/pa/TV/201101011930_20110102_tvdata.xml"));
    	
    	List<File> files = new ArrayList<File>();
    	files.add(file1);
    	files.add(file2);
    	files.add(file3);
    	files.add(file4);
    	files.add(file5);
    	files.add(file6);
    	files.add(file7);
    	files.add(file8);
    	
    	PaSingleDateUpdater updater = new PaSingleDateUpdater(null, null, null, new DummyChannelResolver(), null);
    	Set<Queue<File>> groupedFiles = updater.groupAndOrderFilesByDay(files);
    	assertEquals(3, groupedFiles.size());
    	
    	for(Queue<File> filesForDay : groupedFiles) {
	    	if(filesForDay.size() == 3) {
		    	assertEquals("file:/data/pa/TV/20110101_tvdata.xml", filesForDay.remove().toURI().toString());
		    	assertEquals("file:/data/pa/TV/201101040130_20110101_tvdata.xml", filesForDay.remove().toURI().toString());
		    	assertEquals("file:/data/pa/TV/201101050145_20110101_tvdata.xml", filesForDay.remove().toURI().toString());
	    	} else if(filesForDay.size() == 4) {
	    		assertEquals("file:/data/pa/TV/20110102_tvdata.xml", filesForDay.remove().toURI().toString());
		    	assertEquals("file:/data/pa/TV/201101010145_20110102_tvdata.xml", filesForDay.remove().toURI().toString());
		    	assertEquals("file:/data/pa/TV/201101010200_20110102_tvdata.xml", filesForDay.remove().toURI().toString());
		    	assertEquals("file:/data/pa/TV/201101011930_20110102_tvdata.xml", filesForDay.remove().toURI().toString());
	    	} else if(filesForDay.size() == 1) {
	    		assertEquals("file:/data/pa/TV/20110103_tvdata.xml", filesForDay.remove().toURI().toString());
	    	} else throw new IllegalStateException("Wasn't expecting this many files for any day");
    	}
    	
    }
    
    private Item loadItemAtPosition(Brand brand, int index) {
        return (Item) resolver.findByCanonicalUris(ImmutableList.of(brand.getChildRefs().get(index).getUri())).getFirstValue().requireValue();
    }

    static class TestPaProgrammeUpdater extends PaBaseProgrammeUpdater {

        private List<File> files;

        public TestPaProgrammeUpdater(PaProgDataProcessor processor, ChannelResolver channelResolver, AdapterLog log, MongoScheduleStore scheduleWriter, List<File> files, BroadcastTrimmer trimmer) {
            super(MoreExecutors.sameThreadExecutor(), new PaChannelProcessor(processor, trimmer, scheduleWriter), new DefaultPaProgrammeDataStore("/data/pa", null), channelResolver);
            this.files = files;
        }

        @Override
        public void runTask() {
            this.processFiles(files);
        }
    }
    
    static class DummyChannelResolver implements ChannelResolver {

		@Override
		public Maybe<Channel> fromKey(String key) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Maybe<Channel> fromId(long id) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Maybe<Channel> fromUri(String uri) {
			if("http://www.bbc.co.uk/bbcone".equals(uri)) {
				return Maybe.just(new Channel(Publisher.METABROADCAST, "BBC One", "bbcone", MediaType.VIDEO, "http://www.bbc.co.uk/bbcone"));
			}
			return Maybe.just(new Channel());
		}

		@Override
		public Iterable<Channel> all() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<String, Channel> forAliases(String aliasPrefix) {
			return ImmutableMap.of(
					"http://pressassociation.com/channels/4", new Channel(Publisher.METABROADCAST, "BBC One", "bbcone", MediaType.VIDEO, "http://www.bbc.co.uk/bbcone"));
		}

        @Override
        public Iterable<Channel> forIds(Iterable<Long> ids) {
            throw new UnsupportedOperationException();
        }
    	
    }
   
}
