package org.atlasapi.remotesite.space;

import java.util.Collections;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Item;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.logging.AdapterLog;
import org.junit.Ignore;
import org.junit.Test;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 */
public class TheSpaceUpdaterTest {

    public TheSpaceUpdaterTest() {
    }

    @Test
    public void testRunTask() throws Exception {
        AdapterLog log = mock(AdapterLog.class);
        ContentResolver resolver = mock(ContentResolver.class);
        ContentWriter writer = mock(ContentWriter.class);
        
        when(resolver.findByCanonicalUris(anyCollection())).thenReturn(new ResolvedContent(Collections.EMPTY_MAP));
        doAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                System.out.println(ToStringBuilder.reflectionToString(invocation.getArguments()[0], ToStringStyle.MULTI_LINE_STYLE));
                return null;
            }
        }).when(writer).createOrUpdate(any(Container.class));
        doAnswer(new Answer() {

            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                System.out.println(ToStringBuilder.reflectionToString(invocation.getArguments()[0], ToStringStyle.MULTI_LINE_STYLE));
                return null;
            }
        }).when(writer).createOrUpdate(any(Item.class));

        TheSpaceUpdater updater = new TheSpaceUpdater(resolver, writer, log, this.getClass().getClassLoader().getResource("atlas.jks").getFile(), "sergio");
        updater.runTask();
    }
}
