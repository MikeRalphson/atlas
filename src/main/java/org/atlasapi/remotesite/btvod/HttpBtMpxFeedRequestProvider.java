package org.atlasapi.remotesite.btvod;

import com.google.api.client.repackaged.com.google.common.base.Strings;
import com.google.api.client.repackaged.com.google.common.base.Throwables;
import com.google.common.base.Optional;
import com.metabroadcast.common.http.SimpleHttpRequest;
import org.atlasapi.remotesite.btvod.model.BtVodResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static com.google.common.base.Preconditions.checkNotNull;

public class HttpBtMpxFeedRequestProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpBtMpxFeedRequestProvider.class);
    private final String urlBase;
    private final Optional<String> query;

    public HttpBtMpxFeedRequestProvider(String urlBase, String query) {
        this.urlBase = checkNotNull(urlBase);
        this.query = Optional.fromNullable(Strings.emptyToNull(query));
    }


    public SimpleHttpRequest<BtVodResponse> buildRequest(String endpoint, Integer startIndex) {
        String url = String.format(
                "%s%s?startIndex=%s%s",
                urlBase,
                endpoint,
                startIndex,
                additionalQueryParams()
        );
        log.debug("Calling BT VoD MPX feed url {}", url);
        return new SimpleHttpRequest<>(
                url,
                new BtVodResponseTransformer()
        );
    }

    private String additionalQueryParams() {
        if (!query.isPresent()) {
            return "";
        }
        return String.format(
                "&q=%s",
                query.get()
        );
    }
}
