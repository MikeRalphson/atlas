package org.atlasapi.query.content;

import static org.hamcrest.Matchers.hasItems;

import java.util.List;
import java.util.Map;

import org.atlasapi.content.criteria.MatchesNothing;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.KnownTypeContentResolver;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.lookup.InMemoryLookupEntryStore;
import org.atlasapi.persistence.lookup.entry.LookupEntry;
import org.atlasapi.persistence.lookup.entry.LookupRef;
import org.jmock.Expectations;
import org.jmock.integration.junit3.MockObjectTestCase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class LookupResolvingQueryExecutorTest extends MockObjectTestCase {

    private KnownTypeContentResolver contentResolver = mock(KnownTypeContentResolver.class);
    
    private final InMemoryLookupEntryStore lookupStore = new InMemoryLookupEntryStore();
    
    private final LookupResolvingQueryExecutor executor = new LookupResolvingQueryExecutor(contentResolver, lookupStore);
    
    public void testSetsSameAs() {

        final String query = "query";
        
        final Item queryItem = new Item(query, "qcurie", Publisher.BBC);
        final Item equivItem = new Item("equiv", "ecurie", Publisher.ITV);
        
        
        lookupStore.store(lookupEntryWithEquivalents(query, LookupRef.from(queryItem), LookupRef.from(equivItem)));
        
        checking(new Expectations(){{
            one(contentResolver).findByLookupRefs(with(hasItems(LookupRef.from(queryItem), LookupRef.from(equivItem))));
                will(returnValue(ResolvedContent.builder()
                        .put(queryItem.getCanonicalUri(), queryItem)
                        .put(equivItem.getCanonicalUri(), equivItem)
               .build()));
        }});
        
        Map<String, List<Identified>> result = executor.executeUriQuery(ImmutableList.of(query), MatchesNothing.asQuery());
        
        assertEquals(2, result.get(query).size());
        for (Identified resolved : result.get(query)) {
            if(resolved.getCanonicalUri().equals(query)) {
                assertEquals(ImmutableSet.of(equivItem.getCanonicalUri()), resolved.getEquivalentTo());
            } else if(resolved.getCanonicalUri().equals("equiv")) {
                assertEquals(ImmutableSet.of(query), resolved.getEquivalentTo());
            }
        }
        
    }

    private LookupEntry lookupEntryWithEquivalents(String uri, LookupRef... equiv) {
        return new LookupEntry(uri, null, null, ImmutableSet.<String>of(), ImmutableSet.<LookupRef>of(), ImmutableSet.copyOf(equiv), null, null);
    }
}
