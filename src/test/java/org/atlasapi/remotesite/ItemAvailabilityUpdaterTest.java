package org.atlasapi.remotesite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import java.util.List;

import junit.framework.TestCase;

import org.atlasapi.media.entity.Clip;
import org.atlasapi.media.entity.Encoding;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Location;
import org.atlasapi.media.entity.Policy;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Version;
import org.atlasapi.persistence.logging.NullAdapterLog;
import org.atlasapi.persistence.media.entity.ItemTranslator;
import org.joda.time.DateTime;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.persistence.MongoTestHelper;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.time.DateTimeZones;
import com.metabroadcast.common.time.TimeMachine;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class ItemAvailabilityUpdaterTest extends TestCase {
	
	private final TimeMachine clock = new TimeMachine(); 
	private final ItemTranslator translator = new ItemTranslator(true);

	private DBCollection itemsColl;
	private ItemAvailabilityUpdater updater;

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		
		DatabasedMongo mongo = MongoTestHelper.anEmptyTestDatabase();
		
		itemsColl = mongo.collection("items");
		updater = new ItemAvailabilityUpdater(mongo, new NullAdapterLog(), clock);
		
		Policy p = new Policy();
		p.setAvailabilityStart(new DateTime(10L, DateTimeZones.UTC));
		p.setAvailabilityEnd(new DateTime(20L, DateTimeZones.UTC));
		
		Location l = new Location();
		l.setPolicy(p);
		l.setAvailable(false);
		
		Encoding e = new Encoding();
		e.addAvailableAt(l);
		
		Version v = new Version();
		v.addManifestedAs(e);
		
		Item i = new Item("testUri", "testCurie", Publisher.C4);
		i.addVersion(v);
		
		Clip c = new Clip("clipUri", "clipCurie", Publisher.C4);
		i.addClip(c);
		
		itemsColl.insert(translator.toDBObject(new BasicDBObject(), i));
	}

	public void testRun() {
		clock.jumpTo(new DateTime(5L, DateTimeZones.UTC));
		updater.run();
		
		List<Item> items = getItems();
		assertThat(items.size(), is(equalTo(1)));
		assertThat(items.get(0).isAvailable(), is(false));
		
		clock.jumpTo(new DateTime(15L, DateTimeZones.UTC));
		updater.run();
		
		items = getItems();
		assertThat(items.size(), is(equalTo(1)));
		assertThat(items.get(0).isAvailable(), is(true));
		
		clock.jumpTo(new DateTime(25L, DateTimeZones.UTC));
		updater.run();
		
		items = getItems();
		assertThat(items.size(), is(equalTo(1)));
		assertThat(items.get(0).isAvailable(), is(false));
		
		clock.jumpTo(new DateTime(15L, DateTimeZones.UTC));
		updater.run();
		
		items = getItems();
		assertThat(items.size(), is(equalTo(1)));
		assertThat(items.get(0).isAvailable(), is(true));
		
		clock.jumpTo(new DateTime(5L, DateTimeZones.UTC));
		updater.run();
		
		items = getItems();
		assertThat(items.size(), is(equalTo(1)));
		assertThat(items.get(0).isAvailable(), is(false));
		
	}

	private List<Item> getItems() {
		List<Item> items = ImmutableList.copyOf(Iterables.transform(itemsColl.find(), new Function<DBObject, Item>() {
			@Override
			public Item apply(DBObject from) {
				return translator.fromDBObject(from, null);
			}
		}));
		return items;
	}

}
