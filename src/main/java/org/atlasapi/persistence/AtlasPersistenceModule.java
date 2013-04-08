package org.atlasapi.persistence;

import java.net.UnknownHostException;
import java.util.List;

import javax.annotation.PreDestroy;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.atlasapi.equiv.CassandraEquivalenceSummaryStore;
import org.atlasapi.media.CassandraPersistenceModule;
import org.atlasapi.media.content.ContentStore;
import org.atlasapi.media.content.util.MessageQueueingContentWriter;
import org.atlasapi.messaging.MessageQueueingContentStore;
import org.atlasapi.media.topic.TopicStore;
import org.atlasapi.messaging.MessageQueueingTopicStore;
import org.atlasapi.media.topic.TopicStore;
import org.atlasapi.persistence.bootstrap.ContentBootstrapper;
import org.atlasapi.persistence.content.ContentResolverBackedIdSettingContentWriter;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.content.IdSettingContentWriter;
import org.atlasapi.persistence.content.KnownTypeContentResolver;
import org.atlasapi.persistence.content.LookupResolvingContentResolver;
import org.atlasapi.persistence.content.LookupStoreBackedIdSettingContentWriter;
import org.atlasapi.persistence.content.SimpleKnownTypeContentResolver;
import org.atlasapi.persistence.content.cassandra.CassandraContentGroupStore;
import org.atlasapi.persistence.content.cassandra.CassandraContentStore;
import org.atlasapi.persistence.content.cassandra.CassandraProductStore;
import org.atlasapi.persistence.content.listing.ContentLister;
import org.atlasapi.persistence.content.mongo.MongoContentGroupResolver;
import org.atlasapi.persistence.content.mongo.MongoContentGroupWriter;
import org.atlasapi.persistence.content.mongo.MongoContentLister;
import org.atlasapi.persistence.content.mongo.MongoContentResolver;
import org.atlasapi.persistence.content.mongo.MongoPersonStore;
import org.atlasapi.persistence.content.mongo.MongoProductStore;
import org.atlasapi.persistence.content.people.QueuingItemsPeopleWriter;
import org.atlasapi.persistence.content.people.QueuingPersonWriter;
import org.atlasapi.persistence.content.people.cassandra.CassandraPersonStore;
import org.atlasapi.persistence.content.schedule.mongo.MongoScheduleStore;
import org.atlasapi.persistence.ids.MongoSequentialIdGenerator;
import org.atlasapi.persistence.logging.SystemOutAdapterLog;
import org.atlasapi.persistence.lookup.cassandra.CassandraLookupEntryStore;
import org.atlasapi.persistence.lookup.mongo.MongoLookupEntryStore;
import org.atlasapi.persistence.media.TranslatorContentHasher;
import org.atlasapi.persistence.media.channel.MongoChannelGroupStore;
import org.atlasapi.persistence.media.channel.MongoChannelStore;
import org.atlasapi.persistence.media.channel.cassandra.CassandraChannelGroupStore;
import org.atlasapi.persistence.media.channel.cassandra.CassandraChannelStore;
import org.atlasapi.persistence.media.segment.IdSettingSegmentWriter;
import org.atlasapi.persistence.media.segment.MongoSegmentResolver;
import org.atlasapi.persistence.media.segment.cassandra.CassandraSegmentStore;
import org.atlasapi.persistence.messaging.mongo.MongoMessageStore;
import org.atlasapi.persistence.shorturls.MongoShortUrlSaver;
import org.atlasapi.persistence.topic.TopicCreatingTopicResolver;
import org.atlasapi.persistence.topic.cassandra.CassandraTopicStore;
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
import com.metabroadcast.common.ids.UUIDGenerator;
import com.metabroadcast.common.persistence.mongo.DatabasedMongo;
import com.metabroadcast.common.persistence.mongo.health.MongoIOProbe;
import com.metabroadcast.common.properties.Configurer;
import com.metabroadcast.common.properties.Parameter;
import com.metabroadcast.common.proxy.DelegateProxy;
import com.mongodb.Mongo;
import com.mongodb.ReadPreference;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

@Configuration
public class AtlasPersistenceModule {

    private final String mongoHost = Configurer.get("mongo.host").get();
    private final String mongoDbName = Configurer.get("mongo.dbName").get();
    
    private final String cassandraEnv = Configurer.get("cassandra.env").get();
    private final String cassandraCluster = Configurer.get("cassandra.cluster").get();
    private final String cassandraKeyspace = Configurer.get("cassandra.keyspace").get();
    private final String cassandraSeeds = Configurer.get("cassandra.seeds").get();
    private final String cassandraPort = Configurer.get("cassandra.port").get();
    private final String cassandraConnectionTimeout = Configurer.get("cassandra.connectionTimeout").get();
    private final String cassandraRequestTimeout = Configurer.get("cassandra.requestTimeout").get();
    private final String cassandraClientThreads = Configurer.get("cassandra.clientThreads").get();
 
    private final String esSeeds = Configurer.get("elasticsearch.seeds").get();
    private final String esRequestTimeout = Configurer.get("elasticsearch.requestTimeout").get();
    private final Parameter processingConfig = Configurer.get("processing.config");
    private final String generateIds = Configurer.get("ids.generate").get();
    
    @Resource(name = "contentChanges") private JmsTemplate contentChanges;
    @Resource(name = "topicChanges") private JmsTemplate topicChanges;

    @PostConstruct
    public void init() {
        persistenceModule().start();
    }
    
    @Bean
    public IdGenerator idGenerator() {
        return new UUIDGenerator();
    }
    
    @Bean
    public CassandraPersistenceModule persistenceModule() {
        return new CassandraPersistenceModule(Splitter.on(",").split(cassandraSeeds), 
            Integer.parseInt(cassandraPort), cassandraCluster, cassandraKeyspace, 
            Integer.parseInt(cassandraClientThreads), Integer.parseInt(cassandraConnectionTimeout), 
            idGeneratorBuilder());
    }
    
    @Bean
    public ContentStore contentStore() {
        return new MessageQueueingContentStore(contentChanges, 
            persistenceModule().contentStore());
    }
    
    @Bean TopicStore topicStore() {
        return new MessageQueueingTopicStore(topicChanges,
            persistenceModule().topicStore());
    }

    @Bean
    public ElasticSearchContentIndexModule esContentIndexModule() {
        ElasticSearchContentIndexModule elasticSearchContentIndexModule = new ElasticSearchContentIndexModule(esSeeds, Long.parseLong(esRequestTimeout));
        elasticSearchContentIndexModule.init();
        return elasticSearchContentIndexModule;
    }

    @Bean
    public MongoContentPersistenceModule mongoContentPersistenceModule() {
        return new MongoContentPersistenceModule(databasedMongo());
    }

    @Bean
    public CassandraContentPersistenceModule cassandraContentPersistenceModule() {
        CassandraContentPersistenceModule cassandraContentPersistenceModule = new CassandraContentPersistenceModule(cassandraEnv, cassandraSeeds, Integer.parseInt(cassandraPort), Integer.parseInt(cassandraConnectionTimeout), Integer.parseInt(cassandraRequestTimeout), Integer.parseInt(cassandraClientThreads), idGenerator());
        cassandraContentPersistenceModule.init();
        return cassandraContentPersistenceModule;
    }

    @Bean
    public ContentBootstrapperModule contentBootstrapperModule() {
        return new ContentBootstrapperModule(cassandraContentPersistenceModule().cassandraContentStore());
    }

    @Bean
    public DatabasedMongo databasedMongo() {
        return new DatabasedMongo(mongo(), mongoDbName);
    }

    @Bean @Primary
    public Mongo mongo() {
        Mongo mongo = new Mongo(mongoHosts());
        if (processingConfig == null || !processingConfig.toBoolean()) {
            mongo.setReadPreference(ReadPreference.secondaryPreferred());
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
    public MongoContentLister contentLister() {
        return mongoContentPersistenceModule().contentLister();
    }

    @Bean
    public MongoContentGroupWriter contentGroupWriter() {
        return mongoContentPersistenceModule().contentGroupWriter();
    }

    @Bean
    public MongoContentGroupResolver contentGroupResolver() {
        return mongoContentPersistenceModule().contentGroupResolver();
    }

    @Bean
    public ContentWriter contentWriter() {
        ContentWriter contentWriter = mongoContentPersistenceModule().contentWriter();
        contentWriter = new EquivalenceWritingContentWriter(contentWriter, lookupStore());
        if (Boolean.valueOf(generateIds)) {
            contentWriter = new LookupStoreBackedIdSettingContentWriter(lookupStore(), idGeneratorBuilder().generator("content"), contentWriter);
        }
        contentWriter = new MessageQueueingContentWriter(contentChanges, contentWriter, new TranslatorContentHasher());
        return contentWriter;
    }

    @Bean
    public LookupResolvingContentResolver contentResolver() {
        return mongoContentPersistenceModule().contentResolver();
    }

    @Bean
    public QueuingItemsPeopleWriter itemsPeopleWriter() {
        return mongoContentPersistenceModule().itemsPeopleWriter();
    }

    @Bean
    public MongoContentResolver knownTypeContentResolver() {
        return mongoContentPersistenceModule().knownTypeContentResolver();
    }

    @Bean
    public TopicCreatingTopicResolver oldTopicStore() {
        return mongoContentPersistenceModule().topicStore();
    }
    
    @Bean
    public MongoShortUrlSaver shortUrlSaver() {
        return mongoContentPersistenceModule().shortUrlSaver();
    }

    @Bean
    public IdSettingSegmentWriter segmentWriter() {
        return new IdSettingSegmentWriter(mongoContentPersistenceModule().segmentWriter(), segmentResolver(), idGeneratorBuilder().generator("segment"));
    }

    @Bean
    public MongoSegmentResolver segmentResolver() {
        return mongoContentPersistenceModule().segmentResolver();
    }

    @Bean
    public MongoProductStore productStore() {
        return mongoContentPersistenceModule().productStore();
    }

    @Bean
    public MongoLookupEntryStore lookupStore() {
        return mongoContentPersistenceModule().lookupStore();
    }

    @Bean
    @Primary
    public MongoChannelStore channelStore() {
        return mongoContentPersistenceModule().channelStore();
    }

    @Bean
    public MongoChannelGroupStore channelGroupStore() {
        return mongoContentPersistenceModule().channelGroupStore();
    }

    @Bean
    public MongoPersonStore personStore() {
        return mongoContentPersistenceModule().personStore();
    }

    @Bean
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
    public EsContentIndexer contentIndexer() {
        return esContentIndexModule().contentIndexer();
    }

    @Bean
    @Primary
    public EsScheduleIndex scheduleIndex() {
        return esContentIndexModule().scheduleIndex();
    }

    @Bean
    @Primary
    public EsTopicSearcher topicSearcher() {
        return esContentIndexModule().topicSearcher();
    }

    @Bean
    @Primary
    public EsContentSearcher contentSearcher() {
        return esContentIndexModule().contentSearcher();
    }

    @Bean
    @Qualifier(value = "cassandra")
    public CassandraContentStore cassandraContentStore() {
        return cassandraContentPersistenceModule().cassandraContentStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public ContentWriter cassandraContentWriter() {
        ContentWriter contentWriter = cassandraContentPersistenceModule().cassandraContentStore();
        if (Boolean.valueOf(generateIds)) {
            contentWriter = new ContentResolverBackedIdSettingContentWriter(cassandraContentStore(), idGeneratorBuilder().generator("content"), contentWriter);
        }
        contentWriter = new EquivalenceWritingContentWriter(contentWriter, cassandraContentPersistenceModule().cassandraLookupEntryStore());
        contentWriter = new MessageQueueingContentWriter(contentChanges, contentWriter, new TranslatorContentHasher());
        return contentWriter;
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public LookupResolvingContentResolver cassandraContentResolver() {
        return new LookupResolvingContentResolver(
                new SimpleKnownTypeContentResolver(cassandraContentPersistenceModule().cassandraContentStore()),
                cassandraContentPersistenceModule().cassandraLookupEntryStore());
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public ContentLister cassandraContentLister() {
        return DelegateProxy.build(cassandraContentPersistenceModule().cassandraContentStore(), ContentLister.class);
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public KnownTypeContentResolver cassandraKnownTypeContentResolver() {
        return new SimpleKnownTypeContentResolver(cassandraContentPersistenceModule().cassandraContentStore());
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraChannelGroupStore cassandraChannelGroupStore() {
        return cassandraContentPersistenceModule().cassandraChannelGroupStore();
    }

    @Bean
//    @Primary
    @Qualifier(value = "cassandra")
    public CassandraChannelStore cassandraChannelStore() {
        return cassandraContentPersistenceModule().cassandraChannelStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraContentGroupStore cassandraContentGroupStore() {
        return cassandraContentPersistenceModule().cassandraContentGroupStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraPersonStore cassandraPersonStore() {
        return cassandraContentPersistenceModule().cassandraPersonStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public QueuingItemsPeopleWriter cassandraItemsPeopleWriter() {
        SystemOutAdapterLog log = new SystemOutAdapterLog();
        return new QueuingItemsPeopleWriter(new QueuingPersonWriter(cassandraContentPersistenceModule().cassandraPersonStore(), log), log);
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraProductStore cassandraProductStore() {
        return cassandraContentPersistenceModule().cassandraProductStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraSegmentStore cassandraSegmentStore() {
        return cassandraContentPersistenceModule().cassandraSegmentStore();
    }

    @Bean
    @Qualifier(value = "cassandra")
    public CassandraTopicStore cassandraTopicStore() {
        return cassandraContentPersistenceModule().cassandraTopicStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public TopicCreatingTopicResolver cassandraTopicCreatingTopicResolver() {
        return new TopicCreatingTopicResolver(cassandraContentPersistenceModule().cassandraTopicStore(), idGenerator());
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraLookupEntryStore cassandraLookupEntryStore() {
        return cassandraContentPersistenceModule().cassandraLookupEntryStore();
    }

    @Bean
    @Primary
    @Qualifier(value = "cassandra")
    public CassandraEquivalenceSummaryStore cassandraEquivalenceSummaryStore() {
        return cassandraContentPersistenceModule().cassandraEquivalenceSummaryStore();
    }

    @Bean
    @Qualifier("cassandraContentBootstrapper")
    public ContentBootstrapper cassandraContentBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withContentListers(contentLister());
        bootstrapper.withLookupEntryListers(lookupStore());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraContentGroupBootstrapper")
    public ContentBootstrapper cassandraContentGroupBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withContentGroupListers(contentGroupResolver());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraChannelBootstrapper")
    public ContentBootstrapper cassandraChannelBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withChannelGroupListers(channelGroupStore());
        bootstrapper.withChannelListers(channelStore());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraPeopleBootstrapper")
    public ContentBootstrapper cassandraPeopleBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withPeopleListers(personStore());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraProductBootstrapper")
    public ContentBootstrapper cassandraProductBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withProductListers(productStore());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraSegmentBootstrapper")
    public ContentBootstrapper cassandraSegmentBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withSegmentListers(segmentResolver());
        return bootstrapper;
    }
    
    @Bean
    @Qualifier("cassandraTopicBootstrapper")
    public ContentBootstrapper cassandraTopicBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withTopicListers(oldTopicStore());
        return bootstrapper;
    }

    @Bean
    @Qualifier("esContentBootstrapper")
    public ContentBootstrapper esContentBootstrapper() {
        ContentBootstrapper bootstrapper = new ContentBootstrapper();
        bootstrapper.withContentListers(contentLister());
        return bootstrapper;
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
