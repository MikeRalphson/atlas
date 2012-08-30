package org.atlasapi.remotesite.facebook;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.atlasapi.media.entity.Brand;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ContentWriter;
import org.atlasapi.remotesite.SiteSpecificAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.metabroadcast.common.social.auth.credentials.AuthToken;
import com.metabroadcast.common.social.facebook.FacebookInteracter;

public class FacebookAdapter implements SiteSpecificAdapter<Brand> {
    
    private static final Logger log = LoggerFactory.getLogger(FacebookAdapter.class);

    private final Pattern graphUriPattern = Pattern.compile("https?://graph.facebook.com/(.+)");
    private final FacebookBrandExtractor extractor = new FacebookBrandExtractor();
    
    private final FacebookInteracter facebookInteracter;
    private final AuthToken token;
    
    public FacebookAdapter(FacebookInteracter facebookInteracter, @Nullable AuthToken token) {
        this.facebookInteracter = facebookInteracter;
        this.token = token;
    }
    
    @Override
    public Brand fetch(String uri) {
        Matcher matcher = graphUriPattern.matcher(uri);
        checkArgument(matcher.matches(), "Invalid URI: %s", uri);
        
        String entityId = matcher.group(1);
        
        Map<String, Object> entity = facebookInteracter.get(token, entityId);
        
        return extractor.extract(facebookPage(entity));
    }

    private FacebookPage facebookPage(Map<String, Object> entity) {
        FacebookPage page = new FacebookPage();
        
        page.setId((String)entity.get("id"));
        page.setName((String)entity.get("name"));
        page.setPlotOutline((String)entity.get("plot_outline"));
        page.setLink((String)entity.get("link"));
        page.setWebsite((String)entity.get("website"));
        page.setStarring((String)entity.get("starring"));
        page.setDirectedBy((String)entity.get("directed_by"));
        page.setWrittenBy((String)entity.get("written_by"));
        
        return page;
    }

    @Override
    public boolean canFetch(String uri) {
        return graphUriPattern.matcher(uri).matches();
    }

}
