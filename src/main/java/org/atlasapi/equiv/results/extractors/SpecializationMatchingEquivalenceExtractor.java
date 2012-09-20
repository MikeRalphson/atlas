package org.atlasapi.equiv.results.extractors;

import org.atlasapi.equiv.results.description.ResultDescription;
import org.atlasapi.equiv.results.scores.ScoredCandidate;
import org.atlasapi.media.entity.Content;

import com.google.common.base.Objects;

public class SpecializationMatchingEquivalenceExtractor<T extends Content> extends FilteringEquivalenceExtractor<T> {

    @Override
    protected String name() {
        return "Specialization matching filter";
    }
    
    public SpecializationMatchingEquivalenceExtractor(EquivalenceExtractor<T> delegate) {
        super(delegate, new EquivalenceFilter<T>() {
            @Override
            public boolean apply(ScoredCandidate<T> input, T target, ResultDescription desc) {
                T equivalent = input.candidate();
                boolean passes = (equivalent.getSpecialization() == null || target.getSpecialization() == null || Objects.equal(equivalent.getSpecialization(), target.getSpecialization()));
                if(!passes) {
                    desc.appendText("%s removed. %s != %s", input.candidate().getCanonicalUri(), equivalent.getSpecialization(), target.getSpecialization());
                }
                return passes; 
            }
        });
    }

}
