package org.atlasapi.query.content;

import static org.hamcrest.Matchers.hasItems;

import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.atlasapi.content.criteria.MatchesNothing;
import org.atlasapi.media.entity.Alias;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.LookupRef;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.KnownTypeContentResolver;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.lookup.InMemoryLookupEntryStore;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.atlasapi.application.ApplicationConfiguration;
import org.junit.Ignore;

@RunWith(JMock.class)
public class LookupResolvingQueryExecutorTest extends TestCase {
    private final Mockery context = new Mockery();
    
    private KnownTypeContentResolver cassandraContentResolver = context.mock(KnownTypeContentResolver.class, "cassandraContentResolver");
    private KnownTypeContentResolver mongoContentResolver = context.mock(KnownTypeContentResolver.class, "mongoContentResolver");
    
    private final InMemoryLookupEntryStore lookupStore = new InMemoryLookupEntryStore();
    
    private LookupResolvingQueryExecutor executor;

    @Before
    public void setup(){
        executor = new LookupResolvingQueryExecutor(cassandraContentResolver, mongoContentResolver, lookupStore, true);

    }

    @Test
    @Ignore("Doesn't currently work as equivalence isn't implemented yet in Cassandra.")
    public void setsSameAs() {
        final String query = "query";
        final Item queryItem = new Item(query, "qcurie", Publisher.BBC);
        final Item equivItem = new Item("equiv", "ecurie", Publisher.YOUTUBE);
        
        LookupEntry queryEntry = LookupEntry.lookupEntryFrom(queryItem);
        LookupEntry equivEntry = LookupEntry.lookupEntryFrom(equivItem);
        
        lookupStore.store(queryEntry
            .copyWithDirectEquivalents(ImmutableSet.of(equivEntry.lookupRef()))
            .copyWithEquivalents(ImmutableSet.of(equivEntry.lookupRef())));
        lookupStore.store(equivEntry
            .copyWithDirectEquivalents(ImmutableSet.of(queryEntry.lookupRef()))
            .copyWithDirectEquivalents(ImmutableSet.of(queryEntry.lookupRef())));
        
        context.checking(new Expectations(){{
            one(mongoContentResolver).findByLookupRefs(with(hasItems(LookupRef.from(queryItem), LookupRef.from(equivItem))));
            will(returnValue(ResolvedContent.builder().put(queryItem.getCanonicalUri(), queryItem).put(equivItem.getCanonicalUri(), equivItem).build()));
        }});
        context.checking(new Expectations(){{
            never(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(query), MatchesNothing.asQuery());
        
        assertEquals(2, result.get(query).size());
        for (Identified resolved : result.get(query)) {
            if(resolved.getCanonicalUri().equals(query)) {
                assertEquals(ImmutableSet.of(LookupRef.from(equivItem)), resolved.getEquivalentTo());
            } else if(resolved.getCanonicalUri().equals("equiv")) {
                assertEquals(ImmutableSet.of(LookupRef.from(queryItem)), resolved.getEquivalentTo());
            }
        }
        
        context.assertIsSatisfied();
    }
    
    @Test
    public void testNoEquivalence() {
        final String query = "query";
        final Item queryItem = new Item(query, "qcurie", Publisher.BBC);
        final Item equivItem = new Item("equiv", "ecurie", Publisher.YOUTUBE);
        
        lookupStore.store(lookupEntryWithEquivalents(query, LookupRef.from(queryItem), LookupRef.from(equivItem)));
        
        context.checking(new Expectations(){{
            one(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(queryItem.getCanonicalUri(), queryItem).build()));
        }});
        context.checking(new Expectations(){{
            never(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(query), MatchesNothing.asQuery());
        
        assertEquals(1, result.size());
        context.assertIsSatisfied();
    }
    
    @Test
    public void testMongoIsNotCalledIfCassandraReturnsDataForAllURIs() {
        final String uri1 = "uri1";
        final Item item1 = new Item(uri1, "qcurie1", Publisher.BBC);
        final String uri2 = "uri2";
        final Item item2 = new Item(uri2, "qcurie1", Publisher.BBC);
        
        lookupStore.store(LookupEntry.lookupEntryFrom(item1));
        lookupStore.store(LookupEntry.lookupEntryFrom(item2));

        context.checking(new Expectations(){{
            one(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(item1.getCanonicalUri(), item1).put(item2.getCanonicalUri(), item2).build()));
        }});
        context.checking(new Expectations(){{
            never(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(uri1, uri2), MatchesNothing.asQuery());
        
        assertEquals(2, result.size());
        context.assertIsSatisfied();
    }
    
    @Test
    public void testMongoIsCalledIfCassandraReturnsNothing() {
        final String uri1 = "uri1";
        final Item item1 = new Item(uri1, "qcurie1", Publisher.BBC);
        final String uri2 = "uri2";
        final Item item2 = new Item(uri2, "qcurie1", Publisher.BBC);
        
        lookupStore.store(LookupEntry.lookupEntryFrom(item1));
        lookupStore.store(LookupEntry.lookupEntryFrom(item2));

        context.checking(new Expectations(){{
            one(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(item1.getCanonicalUri(), item1).put(item2.getCanonicalUri(), item2).build()));
        }});
        context.checking(new Expectations(){{
            one(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().build()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(uri1, uri2), MatchesNothing.asQuery());
        
        assertEquals(2, result.size());
        context.assertIsSatisfied();
    }
    
    @Test
    public void testMongoIsCalledIfCassandraReturnsDataForOnlySomeURIs() {
        final String uri1 = "uri1";
        final Item item1 = new Item(uri1, "qcurie1", Publisher.BBC);
        final String uri2 = "uri2";
        final Item item2 = new Item(uri2, "qcurie1", Publisher.BBC);
        
        lookupStore.store(LookupEntry.lookupEntryFrom(item1));
        lookupStore.store(LookupEntry.lookupEntryFrom(item2));

        context.checking(new Expectations(){{
            one(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(item1.getCanonicalUri(), item1).build()));
        }});
        context.checking(new Expectations(){{
            one(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(item2.getCanonicalUri(), item2).build()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(uri1, uri2), MatchesNothing.asQuery());
        
        assertEquals(2, result.size());
        context.assertIsSatisfied();
    }
    
    @Test
    public void testCassandraIsNotCalledIfMongoReturnsNothing() {
        executor = new LookupResolvingQueryExecutor(cassandraContentResolver, mongoContentResolver, lookupStore, false);
                final String query = "query";
        final Item queryItem = new Item(query, "qcurie", Publisher.BBC);
        
        context.checking(new Expectations(){{
            never(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
        }});
        context.checking(new Expectations(){{
            never(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(queryItem.getCanonicalUri(), queryItem).build()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(query), MatchesNothing.asQuery());
        
        assertNull(result.get(query));
        
        context.assertIsSatisfied();
    }
    @Test
    public void testPublisherFilteringWithCassandra() {
        final String uri1 = "uri1";
        final Item item1 = new Item(uri1, "qcurie1", Publisher.BBC);
        final String uri2 = "uri2";
        final Item item2 = new Item(uri2, "qcurie1", Publisher.BBC);
        
        context.checking(new Expectations(){{
            never(mongoContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
        }});
        context.checking(new Expectations(){{
            one(cassandraContentResolver).findByLookupRefs(with(Expectations.<Iterable<LookupRef>>anything()));
            will(returnValue(ResolvedContent.builder().put(item1.getCanonicalUri(), item1).put(item2.getCanonicalUri(), item2).build()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(uri1, uri2), MatchesNothing.asQuery().copyWithApplicationConfiguration(ApplicationConfiguration.DEFAULT_CONFIGURATION.disable(Publisher.BBC)));
        
        assertEquals(0, result.size());

        context.assertIsSatisfied();
    }
    
    private LookupEntry lookupEntryWithEquivalents(String uri, LookupRef... equiv) {
        return new LookupEntry(uri, null, LookupRef.from(new Item("uri","curie",Publisher.BBC)), ImmutableSet.<String>of(), ImmutableSet.<Alias>of(), ImmutableSet.<LookupRef>of(), ImmutableSet.<LookupRef>of(), ImmutableSet.copyOf(equiv), null, null);
    }
}
