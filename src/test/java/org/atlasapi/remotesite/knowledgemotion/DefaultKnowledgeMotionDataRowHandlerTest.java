package org.atlasapi.remotesite.knowledgemotion;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Item;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.topic.TopicQueryResolver;
import org.atlasapi.remotesite.ContentExtractor;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.stubbing.OngoingStubbing;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class DefaultKnowledgeMotionDataRowHandlerTest {

    private static final ResolvedContent NOTHING_RESOLVED = ResolvedContent.builder().build();
    private static final KnowledgeMotionDataRow EMPTY_ROW = emptyRow();
    
    private final ContentResolver resolver = mock(ContentResolver.class);
    private final ContentWriter writer = mock(ContentWriter.class);

    @SuppressWarnings("unchecked")
    private final ContentExtractor<KnowledgeMotionDataRow, Optional<? extends Content>> extractor = mock(ContentExtractor.class);
    private final TopicQueryResolver topicStore = mock(TopicQueryResolver.class);
    
    private final DefaultKnowledgeMotionDataRowHandler handler = new DefaultKnowledgeMotionDataRowHandler(resolver, writer, extractor, topicStore);
    
    private static KnowledgeMotionDataRow emptyRow() {
        return KnowledgeMotionDataRow.builder()
                .withDate("date")
                .withDescription("description")
                .withDuration("duration")
                .withId("id")
                .withSource("source")
                .withTitle("title")
                .build();
    }
    
    @Test
    public void testWritingContent() {
        Item item1 = new Item("item1", "i", Publisher.KM_GLOBALIMAGEWORKS);
        item1.setTitle("title1");
        Item item2 = new Item("item2", "i", Publisher.KM_GLOBALIMAGEWORKS);
        item2.setTitle("title2");
        
        for(Content content : ImmutableList.<Content>of(item1, item2)) {
            OngoingStubbing<Optional<? extends Content>> stubbing = Mockito.<Optional<? extends Content>>when(extractor.extract(EMPTY_ROW));
            stubbing = stubbing.thenReturn(Optional.of(content));
            
            when(resolver.findByCanonicalUris(Matchers.<Iterable<String>>any())).thenReturn(NOTHING_RESOLVED);
            handler.handle(EMPTY_ROW);
        }

        verify(writer, times(1)).createOrUpdate(item1);
        verify(writer, times(1)).createOrUpdate(item2);
    }
    
}
