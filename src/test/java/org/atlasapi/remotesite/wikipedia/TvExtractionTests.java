package org.atlasapi.remotesite.wikipedia;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Item;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.remotesite.wikipedia.television.ScrapedFlatHierarchy;
import org.atlasapi.remotesite.wikipedia.television.TvBrandArticleTitleSource;
import org.atlasapi.remotesite.wikipedia.television.TvBrandHierarchy;
import org.atlasapi.remotesite.wikipedia.television.TvBrandHierarchyExtractor;
import org.atlasapi.remotesite.wikipedia.testutils.FilesystemArticlesSource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;

public class TvExtractionTests {

    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testBuffy() throws URISyntaxException {
        new TvBrandHierarchyUpdater(
            new TvBrandArticleTitleSource() {
                @Override
                public Iterable<String> getAllTvBrandArticleTitles() {
                    return ImmutableList.of("Buffy the Vampire Slayer");
                }
            },
            new FetchMeister(new FilesystemArticlesSource(Paths.get(Resources.getResource(getClass(), "tv").toURI()))),
            new TvBrandHierarchyExtractor() {
                @Override
                public TvBrandHierarchy extract(ScrapedFlatHierarchy source) {
                    Assert.assertEquals(145, source.getEpisodes().size()); // TODO should the pilot count?
                    // TODO be more assertive
                    Assert.assertEquals("Buffy the Vampire Slayer", source.getBrandInfo().title);
                    return super.extract(source);
                }
            },
            new ContentWriter() {
                @Override
                public void createOrUpdate(Container container) {
                    // TODO mock
                }
                
                @Override
                public void createOrUpdate(Item item) {
                    // TODO mock
                }
            }
        ).run();
    }

}
