package org.atlasapi.equiv.results.extractors;

import org.atlasapi.equiv.results.description.ResultDescription;
import org.atlasapi.equiv.results.scores.ScoredEquivalent;
import org.atlasapi.media.content.Content;

public class MinimumScoreEquivalenceExtractor<T extends Content> extends FilteringEquivalenceExtractor<T> {
    
    private final double minimum;

    @Override
    protected String name() {
        return String.format("%s minimum filter", minimum);
    }
    
    public MinimumScoreEquivalenceExtractor(EquivalenceExtractor<T> link, final double minimum) {
        super(link, 
        new EquivalenceFilter<T>() {
            @Override
            public boolean apply(ScoredEquivalent<T> input, T target, ResultDescription desc) {
                boolean result = input.score().isRealScore() && input.score().asDouble() > minimum;
                if(!result) {
                    desc.appendText("%s (%s) removed", input.equivalent().getTitle(), input.equivalent().getCanonicalUri());
                }
                return result;
            }
        });
        this.minimum = minimum;
    }

}
