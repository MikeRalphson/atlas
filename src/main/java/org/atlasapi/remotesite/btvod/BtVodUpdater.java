package org.atlasapi.remotesite.btvod;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.atlasapi.media.entity.Publisher;
import org.atlasapi.media.entity.Series;
import org.atlasapi.media.entity.Topic;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.persistence.topic.TopicCreatingTopicResolver;
import org.atlasapi.persistence.topic.TopicWriter;
import org.atlasapi.remotesite.btvod.contentgroups.BtVodContentGroupUpdater;
import org.atlasapi.remotesite.btvod.topics.BtVodStaleTopicContentRemover;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.repackaged.com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.metabroadcast.common.scheduling.ScheduledTask;


public class BtVodUpdater extends ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(BtVodUpdater.class);
    
    private final ContentResolver resolver;
    private final ContentWriter writer;
    private final String uriPrefix;
    private final Publisher publisher;
    private final BtVodData vodData;
    private final BtVodContentGroupUpdater contentGroupUpdater;
    private final BtVodOldContentDeactivator oldContentDeactivator;
    private final String baseUrl;
    private final ImageExtractor imageExtractor;
    private final BrandImageExtractor brandImageExtractor;
    private final BrandUriExtractor brandUriExtractor;
    private final TopicCreatingTopicResolver topicResolver;
    private final TopicWriter topicWriter;
    private final BtVodContentMatchingPredicate newFeedContentMatchingPredicate;
    private final Topic newTopic;
    private final BtVodStaleTopicContentRemover staleTopicContentRemover;
    private final BtVodSeriesUriExtractor seriesUriExtractor;
    
    public BtVodUpdater(
            ContentResolver resolver,
            ContentWriter contentWriter,
            BtVodData vodData,
            String uriPrefix,
            BtVodContentGroupUpdater contentGroupUpdater,
            Publisher publisher,
            BtVodOldContentDeactivator oldContentDeactivator,
            BrandImageExtractor brandImageExtractor,
            String baseUrl,
            ImageExtractor imageExtractor,
            BrandUriExtractor brandUriExtractor,
            TopicCreatingTopicResolver topicResolver,
            TopicWriter topicWriter,
            BtVodContentMatchingPredicate newFeedContentMatchingPredicate,
            Topic newTopic,
            BtVodStaleTopicContentRemover staleTopicContentRemover,
            BtVodSeriesUriExtractor seriesUriExtractor
    ) {
        this.staleTopicContentRemover = staleTopicContentRemover;
        this.topicResolver = checkNotNull(topicResolver);
        this.topicWriter = checkNotNull(topicWriter);
        this.newFeedContentMatchingPredicate = checkNotNull(newFeedContentMatchingPredicate);
        this.newTopic = checkNotNull(newTopic);
        this.resolver = checkNotNull(resolver);
        this.writer = checkNotNull(contentWriter);
        this.vodData = checkNotNull(vodData);
        this.uriPrefix = checkNotNull(uriPrefix);
        this.publisher = checkNotNull(publisher);
        this.contentGroupUpdater = checkNotNull(contentGroupUpdater);
        this.oldContentDeactivator = checkNotNull(oldContentDeactivator);
        this.brandImageExtractor = checkNotNull(brandImageExtractor);
        this.brandUriExtractor = checkNotNull(brandUriExtractor);
        this.baseUrl = checkNotNull(baseUrl);
        this.imageExtractor = checkNotNull(imageExtractor);
        this.seriesUriExtractor = checkNotNull(seriesUriExtractor);

        withName("BT VOD Catalogue Ingest");
    }

    @Override
    public void runTask() {
        
        newFeedContentMatchingPredicate.init();
        
        BtVodDescribedFieldsExtractor describedFieldsExtractor = new BtVodDescribedFieldsExtractor(topicResolver, topicWriter, publisher, newFeedContentMatchingPredicate, newTopic);
        
        brandImageExtractor.start();
        
        MultiplexingVodContentListener listeners 
            = new MultiplexingVodContentListener(
                    ImmutableList.of(oldContentDeactivator, contentGroupUpdater, staleTopicContentRemover));
        Set<String> processedRows = Sets.newHashSet();
        
        listeners.beforeContent();
        
        BtVodBrandWriter brandExtractor = new BtVodBrandWriter(
                writer,
                resolver,
                publisher,
                listeners,
                processedRows,
                describedFieldsExtractor,
                brandImageExtractor,
                brandUriExtractor
        );

        BtVodExplicitSeriesWriter explicitSeriesExtractor = new BtVodExplicitSeriesWriter(
                writer,
                resolver,
                brandExtractor,
                publisher,
                listeners,
                describedFieldsExtractor,
                processedRows,
                seriesUriExtractor
        );

        try {
            reportStatus("Extracting brand images");
            vodData.processData(brandImageExtractor);
            reportStatus("Brand extract [IN PROGRESS] Explicit series extract [TODO] Synthesized series extract [TODO]  Item extract [TODO]");
            vodData.processData(brandExtractor);
            reportStatus(
                    String.format(
                            "Brand extract [DONE: %d rows successful %d rows failed] Explicit series extract [IN PROGRESS] Synthesized series extract [TODO]  Item extract [TODO]",
                            brandExtractor.getResult().getProcessed(),
                            brandExtractor.getResult().getFailures()
                    )
            );

            vodData.processData(explicitSeriesExtractor);
            reportStatus(
                    String.format(
                            "Brand extract [DONE: %d rows successful %d rows failed] Explicit series extract [DONE: %d rows successful %d rows failed] Synthesized series extract [IN PROGRESS]  Item extract [TODO]",
                            brandExtractor.getResult().getProcessed(),
                            brandExtractor.getResult().getFailures(),
                            explicitSeriesExtractor.getResult().getProcessed(),
                            explicitSeriesExtractor.getResult().getFailures()
                    )
            );

            Map<String, Series> explicitSeries = explicitSeriesExtractor.getExplicitSeries();

            BtVodSynthesizedSeriesWriter synthesizedSeriesExtractor = new BtVodSynthesizedSeriesWriter(
                    writer,
                    resolver,
                    brandExtractor,
                    publisher,
                    listeners,
                    describedFieldsExtractor,
                    processedRows,
                    seriesUriExtractor,
                    explicitSeries.keySet()
            );
            vodData.processData(synthesizedSeriesExtractor);
            reportStatus(
                    String.format(
                            "Brand extract [DONE: %d rows successful %d rows failed] Explicit series extract [DONE: %d rows successful %d rows failed] Synthesized series extract [DONE: %d rows successful %d rows failed]  Item extract [IN PROGRESS]",
                            brandExtractor.getResult().getProcessed(),
                            brandExtractor.getResult().getFailures(),
                            explicitSeriesExtractor.getResult().getProcessed(),
                            explicitSeriesExtractor.getResult().getFailures(),
                            synthesizedSeriesExtractor.getResult().getProcessed(),
                            synthesizedSeriesExtractor.getResult().getFailures()
                    )
            );

            Map<String, Series> synthesizedSeries = synthesizedSeriesExtractor.getSynthesizedSeries();

            BtVodSeriesProvider seriesProvider = new BtVodSeriesProvider(explicitSeries, synthesizedSeries, seriesUriExtractor);

            BtVodItemWriter itemExtractor = new BtVodItemWriter(
                    writer,
                    resolver,
                    brandExtractor,
                    seriesProvider,
                    publisher,
                    uriPrefix,
                    listeners,
                    describedFieldsExtractor,
                    processedRows,
                    new BtVodPricingAvailabilityGrouper(),
                    new TitleSanitiser(),
                    imageExtractor
            );

            vodData.processData(itemExtractor);
            reportStatus(
                    String.format(
                            "Brand extract [DONE: %d rows successful %d rows failed] Explicit series extract [DONE: %d rows successful %d rows failed] Synthesized series extract [DONE: %d rows successful %d rows failed]  Item extract [DONE: %d rows successful %d rows failed] Content group update [IN PROGRESS]",
                            brandExtractor.getResult().getProcessed(),
                            brandExtractor.getResult().getFailures(),
                            explicitSeriesExtractor.getResult().getProcessed(),
                            explicitSeriesExtractor.getResult().getFailures(),
                            synthesizedSeriesExtractor.getResult().getProcessed(),
                            synthesizedSeriesExtractor.getResult().getFailures(),
                            itemExtractor.getResult().getProcessed(),
                            itemExtractor.getResult().getFailures()
                    )
            );
            
            
            listeners.afterContent();

            reportStatus(
                    String.format(
                            "Brand extract [DONE: %d rows successful %d rows failed] Explicit series extract [DONE: %d rows successful %d rows failed] Synthesized series extract [DONE: %d rows successful %d rows failed]  Item extract [DONE: %d rows successful %d rows failed] Content group update [DONE]",
                            brandExtractor.getResult().getProcessed(),
                            brandExtractor.getResult().getFailures(),
                            explicitSeriesExtractor.getResult().getProcessed(),
                            explicitSeriesExtractor.getResult().getFailures(),
                            synthesizedSeriesExtractor.getResult().getProcessed(),
                            synthesizedSeriesExtractor.getResult().getFailures(),
                            itemExtractor.getResult().getProcessed(),
                            itemExtractor.getResult().getFailures()
                    )
            );
            
            if (brandExtractor.getResult().getFailures() > 0
                    || explicitSeriesExtractor.getResult().getFailures() > 0
                    || synthesizedSeriesExtractor.getResult().getFailures() > 0
                    || itemExtractor.getResult().getFailures() > 0) {
                throw new RuntimeException("Failed to extract some rows");
            }
        } catch (IOException e) {
            log.error("Extraction failed", e);
            Throwables.propagate(e);
        }
        
    }
    
    
}
