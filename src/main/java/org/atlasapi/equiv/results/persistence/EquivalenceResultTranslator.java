package org.atlasapi.equiv.results.persistence;

import static com.google.common.collect.ImmutableSet.copyOf;
import static com.google.common.collect.Iterables.transform;
import static com.metabroadcast.common.persistence.mongo.MongoConstants.ID;
import static org.atlasapi.media.entity.Identified.TO_URI;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.atlasapi.equiv.results.EquivalenceResult;
import org.atlasapi.equiv.results.Score;
import org.atlasapi.equiv.results.ScoredEquivalent;
import org.atlasapi.equiv.results.ScoredEquivalents;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.entity.Publisher;
import org.joda.time.DateTime;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.persistence.translator.TranslatorUtils;
import com.metabroadcast.common.time.DateTimeZones;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

public class EquivalenceResultTranslator {

    private static final String TIMESTAMP = "timestamp";
    private static final String TITLE = "title";
    private static final String EQUIVS = "equivs";
    private static final String STRONG = "strong";

    private static final String COMBINED = "combined";
    private static final String PUBLISHER = "publisher";
    private static final String SCORES = "scores";
    private static final String SOURCE = "source";
    private static final String SCORE = "score";

    public <T extends Content> DBObject toDBObject(EquivalenceResult<T> result) {
        DBObject dbo = new BasicDBObject();
        
        T target = result.target();
        TranslatorUtils.from(dbo, ID, target.getCanonicalUri());
        TranslatorUtils.from(dbo, TITLE, target.getTitle());
        
        TranslatorUtils.fromSet(dbo, copyOf(transform(transform(result.strongEquivalences().values(), ScoredEquivalent.<T>toEquivalent()), TO_URI)), STRONG);
        
        BasicDBList equivList = new BasicDBList();
        
        for (Entry<Publisher, Map<T, Score>> combinedEquivBin : result.combinedEquivalences().entrySet()) {
            Publisher publisher = combinedEquivBin.getKey();
            for (Entry<T, Score> combinedEquiv : combinedEquivBin.getValue().entrySet()) {
                DBObject equivDbo = new BasicDBObject();
                
                TranslatorUtils.from(equivDbo, ID, combinedEquiv.getKey().getCanonicalUri());
                TranslatorUtils.from(equivDbo, TITLE, combinedEquiv.getKey().getTitle());
                TranslatorUtils.from(equivDbo, PUBLISHER, publisher.key());
                TranslatorUtils.from(equivDbo, COMBINED, combinedEquiv.getValue().isRealScore() ? combinedEquiv.getValue().asDouble() : null);
                
                BasicDBList scoreList = new BasicDBList();
                for (ScoredEquivalents<T> source : result.rawScores()) {
                    Map<T, Score> publisherBin = source.equivalents().get(publisher);
                    Score sourceScore = publisherBin != null ? publisherBin.get(combinedEquiv.getKey()) : null;
                    BasicDBObject scoreDbo = new BasicDBObject();
                    scoreDbo.put(SOURCE, source.source());
                    scoreDbo.put(SCORE, sourceScore != null && sourceScore.isRealScore() ? sourceScore.asDouble() : null);
                    scoreList.add(scoreDbo);
                }
                TranslatorUtils.from(equivDbo, SCORES, scoreList);
                
                equivList.add(equivDbo);
            }
        }
        TranslatorUtils.from(dbo, EQUIVS, equivList);
        
        TranslatorUtils.fromDateTime(dbo, TIMESTAMP, new DateTime(DateTimeZones.UTC));
        
        return dbo;
    }

    public RestoredEquivalenceResult fromDBObject(DBObject dbo) {
        if(dbo == null) {
            return null;
        }
        
        String targetId = TranslatorUtils.toString(dbo, ID);
        String targetTitle = TranslatorUtils.toString(dbo, TITLE);
        
        Set<String> strongs = TranslatorUtils.toSet(dbo, STRONG);
        
        Map<EquivalenceIdentifier, Double> totals = Maps.newHashMap();
        Table<String, String, Double> results = HashBasedTable.create();
        
        for (DBObject equivDbo : TranslatorUtils.toDBObjectList(dbo, EQUIVS)) {
            String id = TranslatorUtils.toString(equivDbo, ID);
            totals.put(
                    new EquivalenceIdentifier(id, TranslatorUtils.toString(equivDbo, TITLE), strongs.contains(id), publisherName(equivDbo)), 
                    TranslatorUtils.toDouble(equivDbo, COMBINED)
            );
            for (DBObject scoreDbo : TranslatorUtils.toDBObjectList(equivDbo, SCORES)) {
                Double score = TranslatorUtils.toDouble(scoreDbo, SCORE);
                results.put(id, TranslatorUtils.toString(scoreDbo, SOURCE), score == null ? Double.NaN : score);
            }
        }
        
        return new RestoredEquivalenceResult(targetId, targetTitle, results, totals, TranslatorUtils.toDateTime(dbo, TIMESTAMP));
    }
    
    private String publisherName(DBObject equivDbo) {
        Maybe<Publisher> restoredPublisher = Publisher.fromKey(TranslatorUtils.toString(equivDbo, PUBLISHER));
        String publisher = restoredPublisher.hasValue() ? restoredPublisher.requireValue().title() : "Unknown Publisher";
        return publisher;
    }
    
}
