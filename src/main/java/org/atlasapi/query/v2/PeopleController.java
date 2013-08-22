package org.atlasapi.query.v2;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.application.query.ApplicationConfigurationFetcher;
import org.atlasapi.media.entity.Person;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.output.AtlasErrorSummary;
import org.atlasapi.output.AtlasModelWriter;
import org.atlasapi.persistence.content.PeopleQueryResolver;
import org.atlasapi.persistence.logging.AdapterLog;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.http.HttpStatusCode;
import com.metabroadcast.common.ids.SubstitutionTableNumberCodec;
import com.metabroadcast.common.query.Selection;
import com.metabroadcast.common.query.Selection.SelectionBuilder;

@Controller
public class PeopleController extends BaseController<Iterable<Person>> {

    private static final SelectionBuilder selectionBuilder = Selection.builder().withDefaultLimit(25).withMaxLimit(50);
    
    private static final AtlasErrorSummary NOT_FOUND = new AtlasErrorSummary(new NullPointerException())
        .withErrorCode("Person not found")
        .withStatusCode(HttpStatusCode.NOT_FOUND);
    private static final AtlasErrorSummary FORBIDDEN = new AtlasErrorSummary(new NullPointerException())
        .withStatusCode(HttpStatusCode.FORBIDDEN);

    private final PeopleQueryResolver resolver;

    public PeopleController(PeopleQueryResolver resolver, ApplicationConfigurationFetcher configFetcher,
                    AdapterLog log, AtlasModelWriter<Iterable<Person>> outputter) {
        super(configFetcher, log, outputter, SubstitutionTableNumberCodec.lowerCaseOnly());
        this.resolver = resolver;
    }

    @RequestMapping("/3.0/people.*")
    public void content(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            ApplicationConfiguration config = possibleAppConfig(request)
                    .valueOrDefault(ApplicationConfiguration.defaultConfiguration());

            String uri = request.getParameter("uri");
            String id = request.getParameter("id");
            String publisher = request.getParameter("publisher");
            if (Strings.isNullOrEmpty(uri) ^ Strings.isNullOrEmpty(id) ^ Strings.isNullOrEmpty(publisher) ) {
                throw new IllegalArgumentException("specify exactly one of 'uri', 'id' or 'publisher'");
            }
            
            Iterable<Person> people;
            if (uri != null || id != null) {
                Optional<Person> person;
                if (uri != null) {
                    person = resolver.person(uri, config);
                } else {
                    person = resolver.person(idCodec.decode(id).longValue(), config);
                }
                if(!config.isEnabled(person.get().getPublisher())) {
                    errorViewFor(request, response, FORBIDDEN);
                    return;
                }
                people = person.asSet();
            } else {
                List<Publisher> publishers = Publisher.fromCsv(publisher);
                for (Publisher pub : publishers) {
                    if (!config.isEnabled(pub)) {
                        errorViewFor(request, response, FORBIDDEN);
                        return;
                    }
                }
                people = resolver.people(publishers, config, selectionBuilder.build(request));
            }
            
            if(Iterables.size(people) == 0) {
                errorViewFor(request, response, NOT_FOUND);
                return;
            }         
            
            modelAndViewFor(request, response, people, config);
        } catch (Exception e) {
            errorViewFor(request, response, AtlasErrorSummary.forException(e));
        }
    }
    
    
}
