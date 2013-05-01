package org.atlasapi.persistence;

import java.net.UnknownHostException;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.atlasapi.equiv.EquivalenceSummaryStore;
import org.atlasapi.media.content.util.MessageQueueingContentWriter;
import org.atlasapi.persistence.bootstrap.ContentBootstrapper;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.EquivalenceWritingContentWriter;
import org.atlasapi.persistence.content.IdSettingContentWriter;
import org.atlasapi.persistence.content.LookupResolvingContentResolver;
import org.atlasapi.persistence.content.cassandra.CassandraContentStore;
import org.atlasapi.persistence.content.elasticsearch.ESContentIndexer;
import org.atlasapi.persistence.content.elasticsearch.ESContentSearcher;
import org.atlasapi.persistence.content.elasticsearch.EsScheduleIndex;
import org.atlasapi.persistence.content.mongo.MongoContentGroupResolver;
import org.atlasapi.persistence.content.mongo.MongoContentGroupWriter;
import org.atlasapi.persistence.content.mongo.MongoContentLister;
import org.atlasapi.persistence.content.mongo.MongoContentResolver;
import org.atlasapi.persistence.content.mongo.MongoPersonStore;
import org.atlasapi.persistence.content.mongo.MongoProductStore;
import org.atlasapi.persistence.content.people.QueuingItemsPeopleWriter;
import org.atlasapi.persistence.content.schedule.mongo.MongoScheduleStore;
import org.atlasapi.persistence.ids.MongoSequentialIdGenerator;
import org.atlasapi.persistence.lookup.mongo.MongoLookupEntryStore;
import org.atlasapi.persistence.media.channel.CachingChannelStore;
import org.atlasapi.persistence.media.channel.ChannelStore;
import org.atlasapi.persistence.media.channel.MongoChannelGroupStore;
import org.atlasapi.persistence.media.segment.IdSettingSegmentWriter;
import org.atlasapi.persistence.media.segment.MongoSegmentResolver;
import org.atlasapi.persistence.messaging.mongo.MongoMessageStore;
import org.atlasapi.persistence.shorturls.MongoShortUrlSaver;
import org.atlasapi.persistence.topic.TopicCreatingTopicResolver;
import org.atlasapi.persistence.topic.elasticsearch.ESTopicSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.core.JmsTemplate;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.ids.IdGenerator;
import com.metabroadcast.common.ids.IdGeneratorBuilder;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.health.MongoIOProbe;
import com.metabroadcast.common.properties.Configurer;
import com.metabroadcast.common.properties.Parameter;
import com.mongodb.Mongo;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

@Configuration
public class AtlasPersistenceModule {

    private final Logger log = LoggerFactory.getLogger(getClass()); 
    
    private final String mongoHost = Configurer.get("mongo.host").get();
    private final String mongoDbName = Configurer.get("mongo.dbName").get();
    private final String cassandraEnv = Configurer.get("cassandra.env").get();
    private final String cassandraSeeds = Configurer.get("cassandra.seeds").get();
    private final String cassandraPort = Configurer.get("cassandra.port").get();
    private final String cassandraConnectionTimeout = Configurer.get("cassandra.connectionTimeout").get();
    private final String cassandraRequestTimeout = Configurer.get("cassandra.requestTimeout").get();
    private final String esSeeds = Configurer.get("elasticsearch.seeds").get();
    private final String esRequestTimeout = Configurer.get("elasticsearch.requestTimeout").get();
    private final Parameter processingConfig = Configurer.get("processing.config");
    private final String generateIds = Configurer.get("ids.generate").get();
    //
    @Resource(name = "changesProducer")
    private JmsTemplate changesProducer;
    
    @PostConstruct
    public void init() {
        cassandraContentPersistenceModule().init();
        esContentIndexModule().init();
    }

    @Bean
    public ElasticSearchContentIndexModule esContentIndexModule() {
        return new ElasticSearchContentIndexModule(esSeeds, Long.parseLong(esRequestTimeout));
    }

    @Bean
    public MongoContentPersistenceModule mongoContentPersistenceModule() {
        return new MongoContentPersistenceModule(databasedMongo());
    }

    @Bean
    public CassandraContentPersistenceModule cassandraContentPersistenceModule() {
        return new CassandraContentPersistenceModule(cassandraEnv, cassandraSeeds, Integer.parseInt(cassandraPort), Integer.parseInt(cassandraConnectionTimeout), Integer.parseInt(cassandraRequestTimeout));
    }
    
    @Bean
    public ContentBootstrapperModule contentBootstrapperModule() {
        return new ContentBootstrapperModule(contentLister(), cassandraContentPersistenceModule().cassandraContentStore());
    }

    @Bean
    public DatabasedMongo databasedMongo() {
        return new DatabasedMongo(mongo(), mongoDbName);
    }

    @Bean
    public Mongo mongo() {
        Mongo mongo = new Mongo(mongoHosts());
        if (processingConfig == null || !processingConfig.toBoolean()) {
            mongo.slaveOk();
        }
        return mongo;
    }

    @Bean
    public IdGeneratorBuilder idGeneratorBuilder() {
        return new IdGeneratorBuilder() {

            @Override
            public IdGenerator generator(String sequenceIdentifier) {
                return new MongoSequentialIdGenerator(databasedMongo(), sequenceIdentifier);
            }
        };
    }

    @Bean
    @Primary
    public MongoContentLister contentLister() {
        return mongoContentPersistenceModule().contentLister();
    }

    @Bean
    @Primary
    public MongoContentGroupWriter contentGroupWriter() {
        return mongoContentPersistenceModule().contentGroupWriter();
    }

    @Bean
    @Primary
    public MongoContentGroupResolver contentGroupResolver() {
        return mongoContentPersistenceModule().contentGroupResolver();
    }

    @Bean
    @Primary
    public ContentWriter contentWriter() {
        ContentWriter contentWriter = mongoContentPersistenceModule().contentWriter();
        contentWriter = new EquivalenceWritingContentWriter(contentWriter, lookupStore());
        if (Boolean.valueOf(generateIds)) {
            log.info("ID generation enabled");
            contentWriter = new IdSettingContentWriter(lookupStore(), idGeneratorBuilder().generator("content"), contentWriter);
        }
        contentWriter = new MessageQueueingContentWriter(changesProducer, contentWriter);
        return contentWriter;
    }

    @Bean
    @Primary
    public LookupResolvingContentResolver contentResolver() {
        return mongoContentPersistenceModule().contentResolver();
    }

    @Bean
    @Primary
    public QueuingItemsPeopleWriter itemsPeopleWriter() {
        return mongoContentPersistenceModule().itemsPeopleWriter();
    }

    @Bean
    @Primary
    public MongoContentResolver knownTypeContentResolver() {
        return mongoContentPersistenceModule().knownTypeContentResolver();
    }

    @Bean
    @Primary
    public TopicCreatingTopicResolver topicStore() {
        return mongoContentPersistenceModule().topicStore();
    }

    @Bean
    @Primary
    public MongoShortUrlSaver shortUrlSaver() {
        return mongoContentPersistenceModule().shortUrlSaver();
    }

    @Bean
    @Primary
    public IdSettingSegmentWriter segmentWriter() {
        return new IdSettingSegmentWriter(mongoContentPersistenceModule().segmentWriter(), segmentResolver(), idGeneratorBuilder().generator("segment"));
    }

    @Bean
    @Primary
    public MongoSegmentResolver segmentResolver() {
        return mongoContentPersistenceModule().segmentResolver();
    }

    @Bean
    @Primary
    public MongoProductStore productStore() {
        return mongoContentPersistenceModule().productStore();
    }

    @Bean
    @Primary
    public MongoLookupEntryStore lookupStore() {
        return mongoContentPersistenceModule().lookupStore();
    }

    @Bean
    @Primary
    public ChannelStore channelStore() {
        if (processingConfig == null || !processingConfig.toBoolean()) {
            return new CachingChannelStore(mongoContentPersistenceModule().channelStore());
        }
        return mongoContentPersistenceModule().channelStore();
    }

    @Bean
    @Primary
    public MongoChannelGroupStore channelGroupStore() {
        return mongoContentPersistenceModule().channelGroupStore();
    }

    @Bean
    @Primary
    public MongoPersonStore personStore() {
        return mongoContentPersistenceModule().personStore();
    }

    @Bean
    @Primary
    public MongoScheduleStore scheduleStore() {
        return mongoContentPersistenceModule().scheduleStore();
    }

    @Bean
    @Primary
    public MongoMessageStore messageStore() {
        return mongoContentPersistenceModule().messageStore();
    }

    @Bean
    @Primary
    public ESContentIndexer contentIndexer() {
        return esContentIndexModule().contentIndexer();
    }

    @Bean
    @Primary
    public EsScheduleIndex scheduleIndex() {
        return esContentIndexModule().scheduleIndex();
    }

    @Bean
    @Primary
    public ESTopicSearcher topicSearcher() {
        return esContentIndexModule().topicSearcher();
    }
    
    @Bean
    @Primary
    public ESContentSearcher contentSearcher() {
        return esContentIndexModule().contentSearcher();
    }

    @Bean
    @Qualifier(value = "cassandra")
    public CassandraContentStore cassandraContentStore() {
        return cassandraContentPersistenceModule().cassandraContentStore();
    }
    
    @Bean
    public ContentBootstrapper contentBootstrapper() {
        return contentBootstrapperModule().contentBootstrapper();
    }
    
    @Bean
    public EquivalenceSummaryStore equivalenceSummaryStore() {
        return cassandraContentPersistenceModule().cassandraEquivalenceSummaryStore();
    }

    @Bean
    MongoIOProbe mongoIoSetProbe() {
        return new MongoIOProbe(mongo()).withWriteConcern(WriteConcern.REPLICAS_SAFE);
    }

    private List<ServerAddress> mongoHosts() {
        Splitter splitter = Splitter.on(",").omitEmptyStrings().trimResults();
        return ImmutableList.copyOf(Iterables.filter(Iterables.transform(splitter.split(mongoHost), new Function<String, ServerAddress>() {

            @Override
            public ServerAddress apply(String input) {
                try {
                    return new ServerAddress(input, 27017);
                } catch (UnknownHostException e) {
                    return null;
                }
            }
        }), Predicates.notNull()));
    }
}