package org.atlasapi.remotesite.wikipedia.television;

import java.util.List;
import org.atlasapi.remotesite.wikipedia.Article;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * Struct to hold all scraped data needed for the {@link TvBrandHierarchyExtractor} to extract a TV brand's content hierarchy.
 * Can be obtained from futures of all its distinct parts by constructing its special-purpose inner class ({@link Collector}) and calling {@link Future#collect()} on that.
 */
public class ScrapedFlatHierarchy {
    /**
     * Effectively a "defuturizer" / builder for {@link ScrapedFlatHierarchy} from Future versions of all its component pieces of information.
     * @see ScrapedFlatHierarchy
     * @see #collect()
     */
    public static class Collector {
        private final ListenableFuture<Article> brandArticle;
        private final ListenableFuture<BrandInfoboxScraper.Result> brandInfo;
        private final ListenableFuture<ImmutableCollection<EpisodeListScraper.Result>> episodes;

        public Collector(ListenableFuture<Article> brandArticle, ListenableFuture<BrandInfoboxScraper.Result> brandInfo, ListenableFuture<ImmutableCollection<EpisodeListScraper.Result>> episodes) {
            this.brandArticle = brandArticle;
            this.brandInfo = brandInfo;
            this.episodes = episodes;
        }
        
        /**
         * Returns a Future of {@link ScrapedFlatHierarchy} that's done only when all component pieces of information are ready.
         */
        public ListenableFuture<ScrapedFlatHierarchy> collect() {
            return Futures.transform(Futures.successfulAsList(brandArticle, brandInfo, episodes), new Function<List<Object>, ScrapedFlatHierarchy>() {
                public ScrapedFlatHierarchy apply(List<Object> input) {
                    return new ScrapedFlatHierarchy(
                            (Article) (input.get(0)),
                            (BrandInfoboxScraper.Result) (input.get(1)),
                            (ImmutableCollection<EpisodeListScraper.Result>) (input.get(2)));
                }
            });
        }
    }
    
    private final Article brandArticle;
    private final BrandInfoboxScraper.Result brandInfo;
    private final ImmutableCollection<EpisodeListScraper.Result> episodes;

    public ScrapedFlatHierarchy(Article baseArticle, BrandInfoboxScraper.Result info, ImmutableCollection<EpisodeListScraper.Result> episodes) {
        this.brandArticle = baseArticle;
        this.brandInfo = info;
        this.episodes = episodes;
    }

    public Article getBrandArticle() {
        return brandArticle;
    }

    public BrandInfoboxScraper.Result getBrandInfo() {
        return brandInfo;
    }

    public ImmutableCollection<EpisodeListScraper.Result> getEpisodes() {
        return episodes;
    }
    
}
