package org.atlasapi.remotesite.lovefilm;

import static org.atlasapi.media.entity.Publisher.LOVEFILM;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.List;

import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.Series;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.remotesite.ContentExtractor;
import org.atlasapi.remotesite.lovefilm.LoveFilmData.LoveFilmDataRow;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Matchers;
import org.mockito.stubbing.OngoingStubbing;

import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class DefaultLoveFilmDataRowHandlerTest {

    private static final ResolvedContent NOTHING_RESOLVED = ResolvedContent.builder().build();
    private static final LoveFilmDataRow EMPTY_ROW = new LoveFilmDataRow(ImmutableList.<String>of(), ImmutableList.<String>of());
    
    private final ContentResolver resolver = mock(ContentResolver.class);
    private final ContentWriter writer = mock(ContentWriter.class);
    @SuppressWarnings("unchecked")
    private final ContentExtractor<LoveFilmDataRow, Optional<Content>> extractor = mock(ContentExtractor.class);
    
    private final DefaultLoveFilmDataRowHandler handler = new DefaultLoveFilmDataRowHandler(resolver, writer, extractor);
    
    @Test
    public void testHandlesWritingContentInAnyOrder() {
        
        Brand brand = new Brand("brand", "b", LOVEFILM);
        Series series = new Series("series", "s", LOVEFILM);
        series.setParent(brand);
        series.withSeriesNumber(4);
        Episode episode = new Episode("episode", "e", LOVEFILM);
        episode.setContainer(brand);
        episode.setSeries(series);
        
        for(List<Content> contentOrdering : Collections2.permutations(ImmutableList.of(brand,series,episode))) {
            
            
            OngoingStubbing<Optional<Content>> stubbing = when(extractor.extract(EMPTY_ROW));
            for (Content content : contentOrdering) {
                stubbing = stubbing.thenReturn(Optional.of(content));
            }
            when(resolver.findByCanonicalUris(Matchers.<Iterable<String>>any())).thenReturn(NOTHING_RESOLVED);
            
            handler.prepare();

            for (int i = 0; i < contentOrdering.size(); i++) {
                handler.handle(EMPTY_ROW);
            }
            
            handler.finish();
            
            InOrder inOrder = inOrder(writer);
            inOrder.verify(writer, times(1)).createOrUpdate(brand);
            inOrder.verify(writer, times(1)).createOrUpdate(series);
            inOrder.verify(writer, times(1)).createOrUpdate(episode);
            inOrder.verifyNoMoreInteractions();
            reset(writer);
            
            assertThat(episode.getSeriesNumber(), is(4));
            episode.setSeriesNumber(null);
        }
    }

}
