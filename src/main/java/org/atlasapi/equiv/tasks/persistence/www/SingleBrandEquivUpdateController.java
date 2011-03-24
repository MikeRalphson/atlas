package org.atlasapi.equiv.tasks.persistence.www;

import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.atlasapi.equiv.tasks.ContainerEquivResult;
import org.atlasapi.equiv.tasks.EquivResult;
import org.atlasapi.equiv.tasks.ItemBasedBrandEquivUpdater;
import org.atlasapi.equiv.tasks.persistence.ContainerEquivResultTransformer;
import org.atlasapi.media.entity.Brand;
import org.atlasapi.media.entity.Container;
import org.atlasapi.media.entity.Described;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.media.entity.Item;
import org.atlasapi.persistence.content.ContentResolver;
import org.atlasapi.persistence.content.ScheduleResolver;
import org.atlasapi.persistence.content.mongo.MongoDbBackedContentStore;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import com.metabroadcast.common.http.HttpStatusCode;
import com.metabroadcast.common.model.ModelBuilder;

@Controller
public class SingleBrandEquivUpdateController {

    private final ItemBasedBrandEquivUpdater brandUpdater;
    private final ContentResolver contentStore;
    private ModelBuilder<EquivResult<String>> resultModelBuilder = new EquivResultModelBuilder();
    
    private ContainerEquivResultTransformer<Described, String> transformer = ContainerEquivResultTransformer.defaultTransformer();

    public SingleBrandEquivUpdateController(ScheduleResolver scheduleResolver, MongoDbBackedContentStore contentStore) {
        this.contentStore = contentStore;
        this.brandUpdater = new ItemBasedBrandEquivUpdater(scheduleResolver, contentStore, contentStore).writesResults(true);
    }
    
    @RequestMapping(value = "/system/equivalence/run", method = RequestMethod.GET)
    public String updateBrand(Map<String,Object> model, @RequestParam(value = "uri", required = true) String brandUri, HttpServletResponse response) {
        
        Identified identified = contentStore.findByCanonicalUri(brandUri);
        if(identified instanceof Brand) {
            ContainerEquivResult<Container<?>, Item> equivResult = brandUpdater.updateEquivalence((Brand)identified);
            model.put("result", resultModelBuilder.build(transformer.transform(equivResult)));
            return "system/equivalence/result";
        } else {
            response.setStatus(HttpStatusCode.NOT_FOUND.code());
            response.setContentLength(0);
            return null;
        }
        
    }
}
