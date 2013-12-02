package org.atlasapi.remotesite.wikipedia;

import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Item;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.remotesite.wikipedia.television.TvBrandHierarchyExtractor;
import org.atlasapi.remotesite.wikipedia.testutils.LocallyCachingArticleFetcher;

public class RunTvExtraction {
    public static void main(String... args) {
        EnglishWikipediaClient ewc = new EnglishWikipediaClient();
        new TvBrandHierarchyUpdater(
                ewc,
                new FetchMeister(new LocallyCachingArticleFetcher(ewc, System.getProperty("user.home") + "/atlasTestCaches/wikipedia/tv")),
                new TvBrandHierarchyExtractor(),
                new ContentWriter() {
                    @Override
                    public void createOrUpdate(Item item) {
                        System.out.println(item.toString());
                    }

                    @Override
                    public void createOrUpdate(Container container) {
                        System.out.println(container.toSummary());
                    }
                }
        ).run();
    }
}
