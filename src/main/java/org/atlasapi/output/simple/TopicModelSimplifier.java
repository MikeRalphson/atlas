package org.atlasapi.output.simple;

import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.media.entity.Topic;
import org.atlasapi.output.Annotation;

public class TopicModelSimplifier extends IdentifiedModelSimplifier<Topic, org.atlasapi.media.entity.simple.Topic> {

    private final static String EXTENSION = ".json";
    //
    private String topicUriBase;
    
    public TopicModelSimplifier(String localHostName) {
        this.topicUriBase = String.format("http://%s/topics/", localHostName);
    }
    
    @Override
    public org.atlasapi.media.entity.simple.Topic simplify(Topic fullTopic, Set<Annotation> annotations, final ApplicationConfiguration config) {
        org.atlasapi.media.entity.simple.Topic topic = new org.atlasapi.media.entity.simple.Topic();
        copyIdentifiedAttributesTo(fullTopic, topic, annotations);
        topic.setUri(topicUriBase + topic.getId() + EXTENSION);
        topic.setTitle(fullTopic.getTitle());
        topic.setDescription(fullTopic.getDescription());
        topic.setImage(fullTopic.getImage());
        topic.setThumbnail(fullTopic.getThumbnail());
        topic.setPublisher(toPublisherDetails(fullTopic.getPublisher()));
        if (fullTopic.getType() != null) {
            topic.setType(fullTopic.getType().toString());
        }
        topic.setValue(fullTopic.getValue());
        topic.setNamespace(fullTopic.getNamespace());
        return topic;
    }

}