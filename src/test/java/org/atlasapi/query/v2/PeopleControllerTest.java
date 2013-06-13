package org.atlasapi.query.v2;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.atlasapi.application.ApplicationConfiguration;
import org.atlasapi.application.query.ApplicationConfigurationFetcher;
import org.atlasapi.media.entity.Person;
import org.atlasapi.media.entity.Publisher;
import org.atlasapi.output.Annotation;
import org.atlasapi.output.AtlasErrorSummary;
import org.atlasapi.output.AtlasModelWriter;
import org.atlasapi.persistence.content.PeopleResolver;
import org.atlasapi.persistence.logging.AdapterLog;
import org.atlasapi.persistence.logging.NullAdapterLog;
import org.junit.Before;
import org.junit.Test;

import com.metabroadcast.common.base.Maybe;
import com.metabroadcast.common.ids.SubstitutionTableNumberCodec;
import com.metabroadcast.common.servlet.StubHttpServletRequest;
import com.metabroadcast.common.servlet.StubHttpServletResponse;


public class PeopleControllerTest {

    private PeopleResolver resolver = mock(PeopleResolver.class);
    private final ApplicationConfigurationFetcher configFetcher = mock(ApplicationConfigurationFetcher.class);
    private final AdapterLog log = new NullAdapterLog();
    @SuppressWarnings("unchecked")
    private final AtlasModelWriter<Iterable<Person>> outputter = mock(AtlasModelWriter.class);

    private final PeopleController peopleController = new PeopleController(resolver, configFetcher, log, outputter);
    private final SubstitutionTableNumberCodec idCodec = SubstitutionTableNumberCodec.lowerCaseOnly();

    private StubHttpServletRequest request;
    private StubHttpServletResponse response;
    
    @Before
    public void setup() {
        request = new StubHttpServletRequest();
        response = new StubHttpServletResponse();
        when(configFetcher.configurationFor(request))
            .thenReturn(Maybe.<ApplicationConfiguration>nothing());
    }
    
    @Test
    public void testFailsIfBothUriAndIdAreSpecified() throws Exception {
        request.withParam("uri", "uri");
        request.withParam("id", "id");
        
        peopleController.content(request, response);
        
        verify(outputter).writeError(argThat(is(request)), argThat(is(response)), any(AtlasErrorSummary.class));
        verify(outputter, never()).writeTo(argThat(is(request)), argThat(is(response)), anyPeople(), anySetOfPublishers(), any(ApplicationConfiguration.class));

    }
    
    @Test
    public void testFailsIfApplicationDoesntHavePersonSourceEnabled() throws Exception {
        String uri = "aUri";
        request.withParam("uri", uri);
        
        Person person = new Person();
        person.setPublisher(Publisher.PA);
        when(resolver.person(uri)).thenReturn(person);
        
        peopleController.content(request, response);
        
        verify(outputter).writeError(argThat(is(request)), argThat(is(response)), any(AtlasErrorSummary.class));
        verify(outputter, never()).writeTo(argThat(is(request)), argThat(is(response)), anyPeople(), anySetOfPublishers(), any(ApplicationConfiguration.class));

    }


    @Test
    public void testResolvesPersonByIdWhenIdIsSupplied() throws Exception {
        String id = "cf2";
        request.withParam("id", id);
        
        Person person = new Person();
        person.setPublisher(Publisher.BBC);
        when(resolver.person(idCodec.decode(id).longValue())).thenReturn(person);
        
        peopleController.content(request, response);
        
        verify(outputter, never()).writeError(argThat(is(request)), argThat(is(response)), any(AtlasErrorSummary.class));
        verify(outputter).writeTo(argThat(is(request)), argThat(is(response)), argThat(hasItems(person)), anySetOfPublishers(), any(ApplicationConfiguration.class));
        
    }

    @Test
    public void testResolvesPersonByUriWhenUriIsSupplied() throws Exception {
        String uri = "aUri";
        request.withParam("uri", uri);
        
        Person person = new Person();
        person.setPublisher(Publisher.BBC);
        when(resolver.person(uri)).thenReturn(person);
        
        peopleController.content(request, response);
        
        verify(outputter, never()).writeError(argThat(is(request)), argThat(is(response)), any(AtlasErrorSummary.class));
        verify(outputter).writeTo(argThat(is(request)), argThat(is(response)), argThat(hasItems(person)), anySetOfPublishers(), any(ApplicationConfiguration.class));
        
    }

    @SuppressWarnings("unchecked")
    private Set<Annotation> anySetOfPublishers() {
        return any(Set.class);
    }

    @SuppressWarnings("unchecked")
    private Iterable<Person> anyPeople() {
        return any(Iterable.class);
    }
}
