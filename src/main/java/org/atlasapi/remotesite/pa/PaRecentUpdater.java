package org.atlasapi.remotesite.pa;

import java.io.File;

import org.atlasapi.feeds.upload.FileUploadResult;
import org.atlasapi.feeds.upload.FileUploadResult.FileUploadResultType;
import org.atlasapi.feeds.upload.persistence.FileUploadResultStore;
import org.atlasapi.remotesite.pa.data.PaProgrammeDataStore;
import org.atlasapi.remotesite.pa.persistence.PaScheduleVersionStore;
import org.joda.time.DateTime;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.time.DateTimeZones;
import org.atlasapi.persistence.media.channel.ChannelResolver;

public class PaRecentUpdater extends PaBaseProgrammeUpdater implements Runnable {
       
    private final PaProgrammeDataStore fileManager;
    private final FileUploadResultStore fileUploadResultStore;
    
    public PaRecentUpdater(PaChannelProcessor channelProcessor, PaProgrammeDataStore fileManager, ChannelResolver channelResolver, FileUploadResultStore fileUploadResultStore, PaScheduleVersionStore paScheduleVersionStore) {
        super(channelProcessor, fileManager, channelResolver, Optional.of(paScheduleVersionStore));
        this.fileManager = fileManager;
        this.fileUploadResultStore = fileUploadResultStore;
    }
    
    @Override
    public void runTask() {
        final Long since = new DateTime(DateTimeZones.UTC).minusDays(10).getMillis();
        this.processFiles(fileManager.localFiles(new Predicate<File>() {
            @Override
            public boolean apply(File input) {
                Maybe<FileUploadResult> result = fileUploadResultStore.latestResultFor(SERVICE, input.getName());
                return input.lastModified() > since &&
                        (result.isNothing() || !FileUploadResultType.SUCCESS.equals(result.requireValue().type()));
            }
        }));
    }
    
    @Override 
    protected void storeResult(FileUploadResult result) {
        fileUploadResultStore.store(result.filename(), result);
    }
}
