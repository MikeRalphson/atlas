package org.atlasapi.query.v4.topic;

import static com.google.common.base.Preconditions.checkNotNull;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.output.Annotation;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableSet;
import com.metabroadcast.common.query.Selection;

public class QueryContext {

    private final ApplicationConfiguration appConfig;
    private final ImmutableSet<Annotation> annotations;
    private final Optional<Selection> selection;

    public QueryContext(ApplicationConfiguration appConfig, Iterable<Annotation> annotations,
        Selection selection) {
        this.appConfig = checkNotNull(appConfig);
        this.annotations = ImmutableSet.copyOf(annotations);
        this.selection = Optional.fromNullable(selection);
    }

    public ApplicationConfiguration getAppConfig() {
        return this.appConfig;
    }

    public ImmutableSet<Annotation> getAnnotations() {
        return this.annotations;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }
        if (that instanceof QueryContext) {
            QueryContext other = (QueryContext) that;
            return appConfig.equals(other.appConfig)
                && annotations.equals(other.annotations)
                && selection.equals(other.selection);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return appConfig.hashCode() ^ annotations.hashCode() ^ selection.hashCode();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
            .add("config", appConfig)
            .add("annotations", annotations)
            .add("selection", selection)
            .toString();
    }
    
}
