package org.atlasapi.remotesite.hulu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.atlasapi.media.content.Brand;
import org.atlasapi.media.content.Publisher;
import org.atlasapi.media.content.Specialization;
import org.atlasapi.query.content.PerPublisherCurieExpander;
import org.atlasapi.remotesite.ContentExtractor;
import org.atlasapi.remotesite.FetchException;
import org.atlasapi.remotesite.html.HtmlNavigator;
import org.atlasapi.remotesite.hulu.WritingHuluBrandAdapter.HuluBrandCanonicaliser;
import org.codehaus.jackson.map.ObjectMapper;
import org.jaxen.JaxenException;
import org.jdom.Element;

public class HuluBaseBrandExtractor implements ContentExtractor<HtmlNavigator, Brand> {
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
                        
                        brand = new Brand(brandUri, PerPublisherCurieExpander.CurieAlgorithm.HULU.compact(brandUri), Publisher.HULU);

                        brand.setDescription((String) attributes.get("show_description"));
                        brand.setTitle((String) attributes.get("show_title"));

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
                        
                        brand.setSpecialization(Specialization.TV);

                        break;
                    } catch (Exception e) {
                        throw new FetchException("Unable to map JSON values", e);
                    }
                }
            }
            
            if (brand == null) {
            	throw new FetchException("Page did not not contain a brand, possible change of markup?");
            }

            brand.setTags(HuluItemContentExtractor.getTags(source));

            return brand;
        } catch (JaxenException e) {
            throw new FetchException("Unable to navigate HTML document", e);
        }
    }
}
