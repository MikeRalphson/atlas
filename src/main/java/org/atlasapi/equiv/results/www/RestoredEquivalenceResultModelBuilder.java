package org.atlasapi.equiv.results.www;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.atlasapi.equiv.results.persistence.EquivalenceIdentifier;
import org.atlasapi.equiv.results.persistence.RestoredEquivalenceResult;
import org.atlasapi.equiv.results.probe.EquivalenceResultProbe;
import org.eclipse.jetty.util.UrlEncoded;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.metabroadcast.common.model.SimpleModel;
import com.metabroadcast.common.model.SimpleModelList;
import com.metabroadcast.common.time.DateTimeZones;

public class RestoredEquivalenceResultModelBuilder {

    public SimpleModel build(RestoredEquivalenceResult target, EquivalenceResultProbe probe) {
        SimpleModel model = new SimpleModel();
        
        model.put("id", target.id());
        model.put("encodedId", UrlEncoded.encodeString(target.id()));
        model.put("title", target.title());
        model.put("time", target.resultTime().toDateTime(DateTimeZones.LONDON).toString("YYYY-MM-dd HH:mm:ss"));
        
        boolean hasStrong = false;
        
        Map<String, Double> totals = Maps.newHashMap();
        totals.put("combined", null);
        for (String source : target.sourceResults().columnKeySet()) {
            totals.put(source, null);
        }
        
        SimpleModelList equivalences = new SimpleModelList();
        for (Entry<EquivalenceIdentifier, Double> equivalence : target.combinedResults().entrySet()) {
            SimpleModel equivModel = new SimpleModel();
            
            EquivalenceIdentifier key = equivalence.getKey();
            
            equivModel.put("id",key.id());
            equivModel.put("encodedId",UrlEncoded.encodeString(key.id()));
            equivModel.put("title", key.title());
            equivModel.put("strong", key.strong());
            equivModel.put("publisher", key.publisher());
            equivModel.put("scores", scores(equivalence.getValue(), target.sourceResults().row(key.id()), totals));
            
            hasStrong |= key.strong();
            
            equivModel.put("expected", expected(key, probe));
            
            equivalences.add(equivModel);
        }
        model.put("totals", model(totals));
        model.put("hasStrong", hasStrong);
        model.put("equivalences", equivalences);
        model.putStrings("sources", target.sourceResults().columnKeySet());

        if (target.description() != null) {
            model.put("desc", modelDesc(target.description()));
        }
        return model;
    }

    private Collection<SimpleModel> modelDesc(List<Object> description) {
        return Lists.transform(description, new Function<Object, SimpleModel>() {
            @Override
            public SimpleModel apply(Object input) {
                boolean isList = input instanceof List;
                SimpleModel model = new SimpleModel().put("type", isList ? "list" : "string");
                if (isList) {
                    model.mergeIn(ImmutableMap.of("value", modelDesc((List<Object>) input)));
                } else {
                    model.put("value", (String) input);
                }
                return model;
            }
        });
    }

    private SimpleModel model(Map<String, Double> totals) {
        SimpleModel model = new SimpleModel();
        for (Entry<String, Double> totalScore : totals.entrySet()) {
            if(totalScore.getValue() != null) {
                model.put(totalScore.getKey(), totalScore.getValue());
            } else {
                model.put(totalScore.getKey(), false);
            }
        }
        return model;
    }

    private String expected(EquivalenceIdentifier key, EquivalenceResultProbe probe) {
        if (probe != null) {
            if (probe.expectedEquivalent().contains(key.id())) {
                return "expected";
            }
            if (probe.expectedNotEquivalent().contains(key.id())) {
                return "notexpected";
            }
        }
        return "unknown";
    }

    private SimpleModel scores(Double combined, Map<String, Double> sourceScores, Map<String, Double> totals) {
        
        SimpleModel scoreModel = new SimpleModel();
        if(combined.isNaN()) {
            scoreModel.put("combined", false);
        } else {
            scoreModel.put("combined", combined);
        }
        
        if(combined != null && !combined.isNaN() && !(combined < 0)) {
            Double runningTotal = totals.get("combined");
            totals.put("combined", runningTotal == null ? combined : combined + runningTotal);
        }
        
        for (Entry<String, Double> sourceScore : sourceScores.entrySet()) {
            String source = sourceScore.getKey();
            Double score = sourceScore.getValue();
            
            if(score == null || score.isNaN()) {
                scoreModel.put(source, false);
            } else {
                scoreModel.put(source, score);
            }
            
            if(score != null && !score.isNaN() && score > 0) {
                Double sourceTotal = totals.get(source);
                totals.put(source, sourceTotal == null ? score : score + sourceTotal);
            }
        }
        return scoreModel;
    }
    
}
