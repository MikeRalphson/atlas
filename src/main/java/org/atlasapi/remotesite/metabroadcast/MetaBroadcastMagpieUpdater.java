package org.atlasapi.remotesite.metabroadcast;

import java.util.List;
import java.util.Set;

import org.atlasapi.media.entity.KeyPhrase;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Topic.Type;
import org.atlasapi.media.entity.simple.TopicRef;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.ResolvedContent;
import org.atlasapi.persistence.topic.TopicQueryResolver;
import org.atlasapi.persistence.topic.TopicStore;
import org.atlasapi.remotesite.metabroadcast.ContentWords.WordWeighting;
import org.atlasapi.remotesite.redux.UpdateProgress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

//TODO: this does too much.
public class MetaBroadcastMagpieUpdater extends AbstractMetaBroadcastContentUpdater {

    private static final Logger log = LoggerFactory.getLogger(MetaBroadcastMagpieUpdater.class);
    private ContentResolver contentResolver;

    public MetaBroadcastMagpieUpdater(ContentResolver contentResolver, 
            TopicStore topicStore, TopicQueryResolver topicResolver, ContentWriter contentWriter, String topicNamespace, Publisher publisher) {
        super(contentResolver, topicStore, topicResolver, contentWriter, topicNamespace, publisher);
        this.contentResolver = contentResolver;
    }

    public UpdateProgress updateTopics(MagpieResults results) {
        try {
            return processScheduleItems(results.getResults());
        } catch (Exception e) {
            log.error("Exception updating topics", e);
            return UpdateProgress.FAILURE;
        }
    }
    
    private UpdateProgress processScheduleItems(List<MagpieScheduleItem> magpieItems) {
        UpdateProgress processingResults = UpdateProgress.START;
        
        Set<String> uris = getUris(magpieItems);
        Set<String> mbUris = generateMetaBroadcastUris(uris);
        
        ResolvedContent resolvedMetaBroadcastContent = contentResolver.findByCanonicalUris(mbUris);
        ResolvedContent resolvedContent = contentResolver.findByCanonicalUris(uris);

        for (MagpieScheduleItem magpieItem : magpieItems) {
            try{
                ContentWords contentWordSet = magpieItemToContentWordSet(magpieItem);
                List<KeyPhrase> transformedKeys = getFullKeyPhraseKeys(magpieItem);
                processingResults = processingResults.reduce(createOrUpdateContent(resolvedContent, resolvedMetaBroadcastContent, 
                        contentWordSet, Optional.of(transformedKeys)));
            } catch (Exception e) {
                log.error("Fails on MagpieItem " + magpieItem.getUri(), e);
                processingResults = processingResults.reduce(UpdateProgress.FAILURE);
            }
        }
        return processingResults;
    }

    private List<KeyPhrase> getFullKeyPhraseKeys(MagpieScheduleItem magpieItem) {
        List<org.atlasapi.media.entity.simple.KeyPhrase> keys = magpieItem.getKeyPhrases();
        List<org.atlasapi.media.entity.KeyPhrase> transformedKeys = Lists.newArrayList();
        for (org.atlasapi.media.entity.simple.KeyPhrase simplePhrase : keys) {
            transformedKeys.add(new KeyPhrase(simplePhrase.getPhrase(), publisher, simplePhrase.getWeighting()));
        }
        return transformedKeys;
    }

    private ContentWords magpieItemToContentWordSet(MagpieScheduleItem magpieItem) {
        ContentWords contentWordSet = new ContentWords();
        // We don't have the voila content ID so use the Atlas URI
        contentWordSet.setContentId(magpieItem.getUri());
        contentWordSet.setUri(magpieItem.getUri());
        contentWordSet.setTitle(magpieItem.getTitle());
        contentWordSet.setImage(magpieItem.getImage());
        List<WordWeighting> words = Lists.newArrayList();

        for (TopicRef topic : magpieItem.getTopics()) {
            words.add(topicRefToWordWeighting(topic));
        } 
        contentWordSet.setWords(words);

        return contentWordSet;
    }

    private WordWeighting topicRefToWordWeighting(TopicRef topic) {
        return new WordWeighting(topic.getTopic().getTitle(), StrictMath.round(topic.getWeighting() * 100), topic.getTopic().getUri(), topic.getTopic().getValue() , topic.getTopic().getType());
    }

    private Set<String> getUris(List<MagpieScheduleItem> items){
        return ImmutableSet.copyOf(Iterables.transform(items, new Function<MagpieScheduleItem, String>() {
            @Override
            public String apply(MagpieScheduleItem input) {
                return input.getUri();
            }    
        }));
    }
    
    @Override
    protected Type topicTypeFromSource(String source) {
        return Type.fromKey(source);
    }

    @Override
    protected String topicValueFromWordWeighting(WordWeighting weighting) {
        return weighting.getValue();
    }
    
    @Override
    protected org.atlasapi.media.entity.TopicRef.Relationship topicRefRelationship() {
        return org.atlasapi.media.entity.TopicRef.Relationship.ABOUT;
    }
}
