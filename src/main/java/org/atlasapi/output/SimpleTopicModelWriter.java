package org.atlasapi.output;

import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.media.content.ContentResolver;
import org.atlasapi.media.entity.simple.TopicQueryResult;
import org.atlasapi.media.topic.Topic;
import org.atlasapi.output.simple.ModelSimplifier;

public class SimpleTopicModelWriter extends TransformingModelWriter<Iterable<Topic>, TopicQueryResult> {

    private final ModelSimplifier<Topic, org.atlasapi.media.entity.simple.Topic> topicSimplifier;

    public SimpleTopicModelWriter(AtlasModelWriter<TopicQueryResult> delegate, ContentResolver contentResolver, ModelSimplifier<Topic, org.atlasapi.media.entity.simple.Topic> topicSimplifier) {
        super(delegate);
        this.topicSimplifier = topicSimplifier;
    }
    
    @Override
    protected TopicQueryResult transform(Iterable<Topic> fullTopics, Set<Annotation> annotations, ApplicationConfiguration config) {
        TopicQueryResult result = new TopicQueryResult();
        for (Topic fullTopic : fullTopics) {
            result.add(topicSimplifier.simplify(fullTopic, annotations, config));
        }
        return result;
    }

}
