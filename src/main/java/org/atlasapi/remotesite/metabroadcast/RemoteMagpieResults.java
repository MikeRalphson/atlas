package org.atlasapi.remotesite.metabroadcast;

import static com.google.common.base.Preconditions.checkNotNull;

import com.metabroadcast.common.time.Timestamp;

public abstract class RemoteMagpieResults {

    public static RemoteMagpieResults retrieved(MagpieResults results, Timestamp timestamp) {
        return new RetrievedResults(results, timestamp);
    }

    public static RemoteMagpieResults missing(String context, Throwable reason) {
        return new MissingResults(context, reason);
    }

    public abstract boolean retrieved();

    public abstract Timestamp getTimestamp();

    public abstract MagpieResults getResults();

    public abstract String context();
    
    public abstract Throwable reason();

    private static class RetrievedResults extends RemoteMagpieResults {

        private final Timestamp timestamp;
        private final MagpieResults results;

        public RetrievedResults(MagpieResults results, Timestamp timestamp) {
            this.results = checkNotNull(results);
            this.timestamp = checkNotNull(timestamp);
        }

        @Override
        public Timestamp getTimestamp() {
            return timestamp;
        }

        @Override
        public MagpieResults getResults() {
            return results;
        }

        @Override
        public Throwable reason() {
            throw new IllegalStateException("Results were retrieved");
        }
        
        @Override
        public String context() {
            throw new IllegalStateException("Results were retrieved");
        }

        @Override
        public boolean retrieved() {
            return true;
        }
    }

    private static class MissingResults extends RemoteMagpieResults {

        private Throwable reason;
        private String context;

        public MissingResults(String context, Throwable reason) {
            this.context = context;
            this.reason = reason;
        }

        @Override
        public Timestamp getTimestamp() {
            throw new IllegalStateException("Results weren't retrieved");
        }

        @Override
        public MagpieResults getResults() {
            throw new IllegalStateException("Results weren't retrieved");
        }

        @Override
        public Throwable reason() {
            return reason;
        }
        
        @Override
        public String context() {
            return context;
        }

        @Override
        public boolean retrieved() {
            return false;
        }

    }

}
