package org.atlasapi.equiv.results.filters;

import org.atlasapi.equiv.results.description.ResultDescription;
import org.atlasapi.equiv.results.scores.ScoredCandidate;
import org.atlasapi.media.content.Content;

public class MinimumScoreFilter<T extends Content>  extends AbstractEquivalenceFilter<T> {

    private final double minimum;

    public MinimumScoreFilter(double minimum) {
        this.minimum = minimum;
    }
    
    public boolean doFilter(ScoredCandidate<T> candidate, T subject, ResultDescription desc) {
        boolean result = candidate.score().isRealScore() && candidate.score().asDouble() > minimum;
        if (!result) {
            desc.appendText("%s (%s) removed", candidate.candidate().getTitle(), candidate.candidate().getCanonicalUri());
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s minimum filter", minimum);
    }
}
