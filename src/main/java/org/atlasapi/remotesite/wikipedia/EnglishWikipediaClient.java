package org.atlasapi.remotesite.wikipedia;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnglishWikipediaClient implements ArticleFetcher, FilmArticleTitleSource {
    private static final Logger log = LoggerFactory.getLogger(EnglishWikipediaClient.class);
    
    private static final MediaWikiBot bot = new MediaWikiBot("http://en.wikipedia.org/w/");
    
    private static Iterable<String> filmIndexPageTitles() {
        return Lists.transform(ImmutableList.of(
            "numbers", "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J–K", "L", "M", "N–O", "P", "Q–R", "S", "T", "U–W", "X–Z"
        ), new Function<String, String>() {
            @Override public String apply(String suffix) {
                return "List of films: " + suffix;
            }
        });
    }
    
    @Override
    public Article fetchArticle(String title) {
        return new JwbfArticle(bot.getArticle(title));
    }

    @Override
    public ImmutableList<String> getAllFilmArticleTitles() {
        log.info("Loading film article titles");
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        for(String indexTitle : filmIndexPageTitles()) {
            try {
                builder.addAll(FilmIndexScraper.extractNames(fetchArticle(indexTitle).getMediaWikiSource()));
            } catch (Exception ex) {
                log.error("Failed to load some of the film article names ("+ indexTitle +") – they'll be skipped!", ex);
            }
        }
        return builder.build();
    }

}