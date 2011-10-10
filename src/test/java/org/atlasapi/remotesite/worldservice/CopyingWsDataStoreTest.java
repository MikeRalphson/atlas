package org.atlasapi.remotesite.worldservice;

import java.io.IOException;

import org.atlasapi.persistence.logging.NullAdapterLog;
import org.jmock.Expectations;
import org.jmock.integration.junit3.MockObjectTestCase;
import org.joda.time.DateTime;

import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.time.DateTimeZones;

public class CopyingWsDataStoreTest extends MockObjectTestCase {

    private final WsDataStore remote = mock(WsDataStore.class);
    private final WritableWsDataStore local = mock(WritableWsDataStore.class);

    private final CopyingWsDataStore store = new CopyingWsDataStore(remote, local, new NullAdapterLog());
    
    public void testGettingLatestDataIsNothingWhenLocalAndRemoteReturnNothing() {
        
        checking(new Expectations(){{
            one(remote).latestData();will(returnValue(Maybe.nothing()));
            one(local).latestData();will(returnValue(Maybe.nothing()));
        }});
        
        assertTrue(store.latestData().isNothing());
        
    }
    
    public void testGettingLatestDataIsLocalWhenRemoteReturnsNothing() {
        
        final WsDataSet testSet = new WsDataSet() {
            
            @Override
            public DateTime getVersion() {
                return null;
            }
            
            @Override
            public WsDataSource getDataForFile(WsDataFile file) {
                return null;
            }
        };
        
        checking(new Expectations(){{
            one(remote).latestData();will(returnValue(Maybe.nothing()));
            one(local).latestData();will(returnValue(Maybe.just(testSet)));
        }});
        
        Maybe<WsDataSet> latestData = store.latestData();
        assertTrue(latestData.requireValue() == testSet);
        
    }
    
    public void testGettingLatestDataIsLocalWhenRemoteReturnsSameVersion() {
        DateTime day = new DateTime(DateTimeZones.UTC);
        final WsDataSet localSet = setFor(day);
        final WsDataSet remoteSet = setFor(day);
        
        checking(new Expectations(){{
            one(remote).latestData();will(returnValue(Maybe.just(remoteSet)));
            one(local).latestData();will(returnValue(Maybe.just(localSet)));
        }});
        
        Maybe<WsDataSet> latestData = store.latestData();
        assertTrue(latestData.requireValue() == localSet);
        
    }
    
    public void testGettingLatestDataIsWrittenLocallyWhenLocalReturnsNothing() throws IOException {
        DateTime day = new DateTime(DateTimeZones.UTC);
        final WsDataSet localSet = setFor(day);
        final WsDataSet remoteSet = setFor(day);
        
        checking(new Expectations(){{
            one(remote).latestData();will(returnValue(Maybe.just(remoteSet)));
            one(local).latestData();will(returnValue(Maybe.nothing()));
            one(local).write(remoteSet); will(returnValue(localSet));
        }});
        
        Maybe<WsDataSet> latestData = store.latestData();
        assertTrue(latestData.requireValue() == localSet);
        
    }

    public void testGettingLatestDataIsWrittenLocallyWhenRemoteVersionIsMoreRecent() throws IOException {
        DateTime day = new DateTime(DateTimeZones.UTC);
        final WsDataSet localSet = setFor(day);
        final WsDataSet remoteSet = setFor(day.plusDays(1));
        final WsDataSet writtenSet = setFor(day.plusDays(1));
        
        checking(new Expectations(){{
            one(remote).latestData();will(returnValue(Maybe.just(remoteSet)));
            one(local).latestData();will(returnValue(Maybe.just(localSet)));
            one(local).write(remoteSet); will(returnValue(writtenSet));
        }});
        
        Maybe<WsDataSet> latestData = store.latestData();
        assertTrue(latestData.requireValue() == writtenSet);
        
    }
    
    public void testGettingDateForDayReturnsOnlyLocalWhenAvailable() throws IOException {
        final DateTime day = new DateTime(DateTimeZones.UTC);
        final WsDataSet localSet = setFor(day);
        
        checking(new Expectations(){{
            one(local).dataForDay(day);will(returnValue(Maybe.just(localSet)));
            never(remote).dataForDay(day);
            never(local).write(with(any(WsDataSet.class)));
        }});
        
        Maybe<WsDataSet> dayData = store.dataForDay(day);
        assertTrue(dayData.requireValue() == localSet);
        
    }
    
    public void testGettingDateForDayGetsRemoteAndDoesntWriteIfNothing() throws IOException {
        final DateTime day = new DateTime(DateTimeZones.UTC);
        
        checking(new Expectations(){{
            one(local).dataForDay(day);will(returnValue(Maybe.nothing()));
            one(remote).dataForDay(day);will(returnValue(Maybe.nothing()));
            never(local).write(with(any(WsDataSet.class)));
        }});
        
        Maybe<WsDataSet> dayData = store.dataForDay(day);
        assertTrue(dayData.isNothing());
        
    }

    public void testGettingDateForDayWritesRemoteDataLocally() throws IOException {
        final DateTime day = new DateTime(DateTimeZones.UTC);
        final WsDataSet localSet = setFor(day);
        final WsDataSet remoteSet = setFor(day);
        
        checking(new Expectations(){{
            one(local).dataForDay(day);will(returnValue(Maybe.nothing()));
            one(remote).dataForDay(day);will(returnValue(Maybe.just(remoteSet)));
            one(local).write(remoteSet);will(returnValue(localSet));
        }});
        
        Maybe<WsDataSet> dayData = store.dataForDay(day);
        assertTrue(dayData.requireValue() == localSet);
        
    }
    
    private WsDataSet setFor(final DateTime day) {
        return new WsDataSet() {
            
            @Override
            public DateTime getVersion() {
                return day;
            }
            
            @Override
            public WsDataSource getDataForFile(WsDataFile file) {
                return null;
            }
        };
    }
    
    
}
