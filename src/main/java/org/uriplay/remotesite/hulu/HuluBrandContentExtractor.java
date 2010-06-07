package org.uriplay.remotesite.hulu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.map.ObjectMapper;
import org.jaxen.JaxenException;
import org.jdom.Element;
import org.uriplay.media.entity.Brand;
import org.uriplay.media.entity.Episode;
import org.uriplay.query.content.PerPublisherCurieExpander;
import org.uriplay.remotesite.ContentExtractor;
import org.uriplay.remotesite.FetchException;
import org.uriplay.remotesite.html.HtmlNavigator;
import org.uriplay.remotesite.hulu.HuluBrandAdapter.HuluBrandCanonicaliser;

import com.google.soy.common.collect.Sets;

public class HuluBrandContentExtractor implements ContentExtractor<HtmlNavigator, Brand> {
    private static final String SOCIAL_FEED = "SocialFeed.facebook_template_data.subscribe = ";
    private static final ObjectMapper mapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    @Override
    public Brand extract(HtmlNavigator source) {
        try {
            Brand brand = null;
          
            for (Element element : source.allElementsMatching("//body/script")) {
                String value = element.getValue();

                if (value.startsWith(SOCIAL_FEED)) {
                    try {
                        Map<String, Object> attributes = mapper.readValue(value.replace(SOCIAL_FEED, ""), HashMap.class);
                        
                        String brandUri = new HuluBrandCanonicaliser().canonicalise((String) attributes.get("show_link"));
                        
                        brand = new Brand(brandUri, PerPublisherCurieExpander.CurieAlgorithm.HULU.compact(brandUri));

                        brand.setDescription((String) attributes.get("show_description"));
                        brand.setTitle((String) attributes.get("show_title"));
                        brand.setPublisher("hulu.com");

                        if (attributes.containsKey("images") && attributes.get("images") instanceof List) {
                            List<Map<String, Object>> images = (List<Map<String, Object>>) attributes.get("images");
                            if (!images.isEmpty()) {
                                brand.setThumbnail((String) images.get(0).get("src"));
                            }
                        }
                        
                        Element imageElement = source.firstElementOrNull("//img[@alt=\""+brand.getTitle()+"\"]");
                        if (imageElement != null) {
                            brand.setImage(imageElement.getAttributeValue("src"));
                        }

                        break;
                    } catch (Exception e) {
                        throw new FetchException("Unable to map JSON values", e);
                    }
                }
            }
            
            if (brand == null) {
            	throw new FetchException("Page did not not contain a brand, possible change of markup?");
            }
            
            Set<String> tags = Sets.newHashSet();
            for (Element element : source.allElementsMatching("//li[@class='tags-content-cell']/a")) {
                tags.add("http://www.hulu.com" + element.getAttributeValue("href"));
            }
            brand.setTags(tags);
            
            for (Element element : source.allElementsMatching("//div[@id='episode-container']/div/ul/li/a']")) {
                String episodeUri = element.getAttributeValue("href");
				Episode episode = new Episode(episodeUri, PerPublisherCurieExpander.CurieAlgorithm.HULU.compact(episodeUri));
                brand.addItem(episode);
            }

            return brand;
        } catch (JaxenException e) {
            throw new FetchException("Unable to navigate HTML document", e);
        }
    }
}
