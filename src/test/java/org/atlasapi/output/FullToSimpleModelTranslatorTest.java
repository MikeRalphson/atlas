package org.atlasapi.output;

import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.entity.Episode;
import org.atlasapi.media.entity.simple.ContentQueryResult;
import org.atlasapi.media.entity.simple.Item;
import org.atlasapi.persistence.media.product.ProductResolver;
import org.atlasapi.persistence.media.segment.SegmentResolver;
import org.atlasapi.output.simple.ContainerModelSimplifier;
import org.atlasapi.output.simple.ItemModelSimplifier;
import org.atlasapi.output.simple.ProductModelSimplifier;
import org.atlasapi.output.simple.TopicModelSimplifier;
import org.atlasapi.persistence.content.ContentGroupResolver;
import org.atlasapi.persistence.output.AvailableChildrenResolver;
import org.atlasapi.persistence.output.RecentlyBroadcastChildrenResolver;
import org.atlasapi.persistence.output.ContainerSummaryResolver;
import org.atlasapi.persistence.output.UpcomingChildrenResolver;
import org.atlasapi.persistence.topic.TopicQueryResolver;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.metabroadcast.common.servlet.StubHttpServletRequest;
import com.metabroadcast.common.servlet.StubHttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class FullToSimpleModelTranslatorTest {
    
    private final ContentGroupResolver contentGroupResolver = mock(ContentGroupResolver.class);
    private final TopicQueryResolver topicResolver = mock(TopicQueryResolver.class);
    private final SegmentResolver segmentResolver = mock(SegmentResolver.class);
    private final AvailableChildrenResolver availableChildren = mock(AvailableChildrenResolver.class);
    private final UpcomingChildrenResolver upcomingChildren = mock(UpcomingChildrenResolver.class);
    private final RecentlyBroadcastChildrenResolver recentChildren = mock(RecentlyBroadcastChildrenResolver.class);
    private @SuppressWarnings("unchecked") final AtlasModelWriter<ContentQueryResult> xmlOutputter = mock(AtlasModelWriter.class);
    private final ContainerSummaryResolver containerSummaryResolver = mock(ContainerSummaryResolver.class);
    
    private final TopicModelSimplifier topicSimplifier = new TopicModelSimplifier("localhostName");

    private ProductModelSimplifier productSimplifier = new ProductModelSimplifier("localhostName");
    private ProductResolver productResolver = mock(ProductResolver.class); 

    private final ItemModelSimplifier itemSimplifier = new ItemModelSimplifier("localhostName", contentGroupResolver, topicResolver, productResolver , segmentResolver, containerSummaryResolver);
    private final SimpleContentModelWriter translator = new SimpleContentModelWriter(xmlOutputter, itemSimplifier, new ContainerModelSimplifier(itemSimplifier, "localhostName", contentGroupResolver, topicResolver, availableChildren, upcomingChildren, productResolver, recentChildren),topicSimplifier, productSimplifier);
    
	private StubHttpServletRequest request;
	private StubHttpServletResponse response;

	@Before
	public void setUp() throws Exception {
		this.request = new StubHttpServletRequest();
		this.response = new StubHttpServletResponse();
	}
	
	@Test
	public void testTranslatesItemsInFullModel() throws Exception {
		
		Set<Content> graph = Sets.newHashSet();
		graph.add(new Episode());
		
        translator.writeTo(request, response, QueryResult.of(graph), ImmutableSet.<Annotation>of(), ApplicationConfiguration.DEFAULT_CONFIGURATION);
        
        verify(xmlOutputter).writeTo(eq(request), eq(response), argThat(simpleGraph()), eq(ImmutableSet.<Annotation>of()), eq(ApplicationConfiguration.DEFAULT_CONFIGURATION));
	}

	protected Matcher<ContentQueryResult> simpleGraph() {
		return new TypeSafeMatcher<ContentQueryResult> () {

			@Override
			public boolean matchesSafely(ContentQueryResult result) {
				if (result.getContents().size() != 1) { return false; }
				Object bean = Iterables.getOnlyElement(result.getContents());
				if (!(bean instanceof Item)) { return false; }
				return true;
			}

			public void describeTo(Description description) {
				// TODO Auto-generated method stub
			}};
	}
	
}
