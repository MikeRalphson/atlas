package org.atlasapi.remotesite.wikipedia.television;

import org.atlasapi.remotesite.wikipedia.Callback;
import org.atlasapi.remotesite.wikipedia.SwebleHelper;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.lazy.parser.LazyParsedPage;
import org.sweble.wikitext.lazy.preprocessor.LazyPreprocessedPage;
import org.sweble.wikitext.lazy.preprocessor.OnlyInclude;
import org.sweble.wikitext.lazy.preprocessor.Template;
import org.sweble.wikitext.lazy.preprocessor.TemplateArgument;

import de.fau.cs.osr.ptk.common.AstVisitor;
import de.fau.cs.osr.ptk.common.ast.AstNode;

/**
 * Pulls out episode summaries from episode list articles, and also the names of included articles that contain more episode lists.
 */
public final class EpisodeListScraper extends AstVisitor {
    private static final Logger log = LoggerFactory.getLogger(EpisodeListScraper.class);
    public static class Result {
        public int numberInShow;
        public int numberInSeason;
        public SeasonSectionScraper.Result season;
        public String title;
        public String director;
        public String writer;
        public LocalDate originalAirDate;
        public String prodCode;
        public String summary;
        public String episodePageLinkTarget;  // TODO scrape link!

        @Override
        public String toString() {
            return "Result{" + "numberInShow=" + numberInShow + ", numberInSeason=" + numberInSeason + ", season=" + (season==null ? null : season.name) + ", title=" + title + ", director=" + director + ", writer=" + writer + ", originalAirDate=" + originalAirDate + ", prodCode=" + prodCode + ", summary=" + summary + '}';
        }
    }
    
    private final Callback<Result> resultCallback;
    private final Callback<String> includeCallback;
    /**
     * @param resultCallback Will be called with info from each episode row.
     * @param includeCallback Will be called with the name of each included page that should be scraped for more episodes.
     */
    public EpisodeListScraper(Callback<Result> resultCallback, Callback<String> includeCallback) {
        this.resultCallback = resultCallback;
        this.includeCallback = includeCallback;
    }
    
    // State:
    private Result currentResult = null;  // Holds the current episode row while the visitor carries on eating its TemplateArguments.
    private SeasonSectionScraper.Result currentSeason = null;
    public EpisodeListScraper withSeason(SeasonSectionScraper.Result season) {
        this.currentSeason = season;
        return this;
    }

    /** Courtesy exception in case of the wrong AST */
    public void visit(LazyParsedPage p) {
        throw new IllegalArgumentException("The EpisodeListScraper only knows how to deal with templates, so it wants a preprocessed page, not a parsed one.");
    }
    
    public void visit(LazyPreprocessedPage p) {
        iterate(p.getContent());
    }
    
    public void visit(OnlyInclude i) {
        iterate(i.getContent());
    }

    public void visit(Template t) {
        String name = SwebleHelper.flattenTextNodeList(t.getName());
        
        if(name.startsWith(":")) {  // This is an inclusion of a main space page, probably a sublist of episodes from a particular season.
            if (includeCallback != null) {
                includeCallback.have(name.substring(1));
            }
            return;
        }
        
        if (! "Episode list".equalsIgnoreCase(name)
         && ! "Episode list/sublist".equalsIgnoreCase(name)) {
            log.debug("Ignoring template " + name);
            return;
        }
        currentResult = new Result(){{season = currentSeason;}};
        iterate(t.getArgs());
        resultCallback.have(currentResult);
    }

    public void visit(TemplateArgument a) {
        String name = SwebleHelper.flattenTextNodeList(a.getName());
        // TODO: alt titles? http://en.wikipedia.org/wiki/Template:Episode_list
        if ("Title".equalsIgnoreCase(name)) {
            currentResult.title = SwebleHelper.flattenTextNodeList(a.getValue());
        } else if ("RTitle".equalsIgnoreCase(name) && currentResult.title == null) {
            currentResult.title = SwebleHelper.flattenTextNodeList(a.getValue());
        } else if ("EpisodeNumber".equalsIgnoreCase(name)) {
            currentResult.numberInShow = Integer.parseInt(SwebleHelper.flattenTextNodeList(a.getValue()));
        } else if ("EpisodeNumber2".equalsIgnoreCase(name)) {
            currentResult.numberInSeason = Integer.parseInt(SwebleHelper.flattenTextNodeList(a.getValue()));
        } else if ("DirectedBy".equalsIgnoreCase(name)) {
            currentResult.director = SwebleHelper.flattenTextNodeList(a.getValue());
        } else if ("WrittenBy".equalsIgnoreCase(name)) {
            currentResult.writer = SwebleHelper.flattenTextNodeList(a.getValue());
        } else if ("OriginalAirDate".equalsIgnoreCase(name) && currentResult.originalAirDate == null) {
            currentResult.originalAirDate = SwebleHelper.extractDate(a.getValue());
        } else if ("ProdCode".equalsIgnoreCase(name)) {
            currentResult.prodCode = SwebleHelper.flattenTextNodeList(a.getValue());
        } else if ("ShortSummary".equalsIgnoreCase(name)) {
            currentResult.summary = SwebleHelper.flattenTextNodeList(a.getValue());
        }
    }

    @Override
    protected Object visitNotFound(AstNode node) {
        log.debug("Skipping node " + node.getNodeName());
        return null;
    }
    
}
