package org.atlasapi.remotesite.wikipedia;

import de.fau.cs.osr.ptk.common.AstVisitor;
import de.fau.cs.osr.ptk.common.ast.AstNode;
import java.io.IOException;
import java.util.LinkedList;
import org.sweble.wikitext.lazy.LazyParser;
import org.sweble.wikitext.lazy.ParserConfigInterface;
import org.sweble.wikitext.lazy.parser.InternalLink;
import org.sweble.wikitext.lazy.parser.Itemization;
import org.sweble.wikitext.lazy.parser.ItemizationItem;
import org.sweble.wikitext.lazy.parser.LazyParsedPage;
import org.sweble.wikitext.lazy.parser.Section;
import org.sweble.wikitext.lazy.utils.SimpleParserConfig;
import xtc.parser.ParseException;

/**
 * This class returns a list of article titles that correspond to film pages, given one of the alphabetical index articles.
 */
public class FilmIndexScraper {
    
    private static final ParserConfigInterface cfg = new SimpleParserConfig();
    private static final LazyParser parser = new LazyParser(cfg);

    /**
     * Returns a list of film article names (link targets) from the given Mediawiki source code of an alphabetical film index page.
     */
    public static Iterable<String> extractNames(String indexText) throws IOException, ParseException {
        AstNode indexAST = parser.parseArticle(indexText, "");
        
        Visitor v = new Visitor();
        v.go(indexAST);
        return v.list;
    }
    
    protected static final class Visitor extends AstVisitor {
        LinkedList<String> list = new LinkedList<String>();
        
        public void visit(LazyParsedPage p) {
            iterate(p.getContent());
        }
        
        public void visit(Section s) {
            if ("See also".equalsIgnoreCase(SwebleHelper.flattenTextNodeList(s.getTitle()))) {
                return;  // skip the 'see also' section
            }
            iterate(s.getBody());
        }
        
        public void visit(Itemization i) {
            iterate(i.getContent());
        }
        
        public void visit(ItemizationItem i) {
            iterate(i.getContent());
        }
        
        public void visit(InternalLink l) {
            String target = l.getTarget();
            if (target.startsWith("List of films:")) {  // then it's another list! we skip it!
                return;
            }
            if (target.contains("#")) {  // then it's not a proper page! we skip it!
                return;
            }
            list.add(l.getTarget());
        }

        @Override
        protected Object visitNotFound(AstNode node) { return null; }
    }
}
