/* Copyright 2009 Meta Broadcast Ltd

Licensed under the Apache License, Version 2.0 (the "License"); you
may not use this file except in compliance with the License. You may
obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing
permissions and limitations under the License. */

package org.atlasapi.query.v2;

import java.io.IOException;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atlasapi.application.query.ApplicationConfigurationFetcher;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.media.content.Content;
import org.atlasapi.media.entity.Identified;
import org.atlasapi.output.ErrorSummary;
import org.atlasapi.output.AtlasModelWriter;
import org.atlasapi.output.QueryResult;
import org.atlasapi.persistence.content.query.KnownTypeQueryExecutor;
import org.atlasapi.persistence.logging.AdapterLog;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.metabroadcast.common.http.HttpStatusCode;

@Controller
public class QueryController extends BaseController<QueryResult<Content, ? extends Identified>> {

	private final KnownTypeQueryExecutor executor;
	
    public QueryController(KnownTypeQueryExecutor executor, ApplicationConfigurationFetcher configFetcher, AdapterLog log, AtlasModelWriter<QueryResult<Content, ? extends Identified>> outputter) {
	    super(configFetcher, log, outputter);
        this.executor = executor;
	}
    
    @RequestMapping("/")
    public String redirect() {
        return "redirect:http://docs.atlasapi.org";
    }
    
    @RequestMapping(value = {"/2.0/*.*"})
    public void onePointZero(HttpServletRequest request, HttpServletResponse response) throws IOException {
        outputter.writeError(request, response, new ErrorSummary(new UnsupportedOperationException(), "UNSUPPORTED_VERSION", HttpStatusCode.BAD_REQUEST, "The requested version is no longer supported by this instance"));
    }
	
	@RequestMapping("/3.0/discover.*")
	public void discover(HttpServletRequest request, HttpServletResponse response) throws IOException {
	    outputter.writeError(request, response, new ErrorSummary(new UnsupportedOperationException(), "UNSUPPORTED_VERSION", HttpStatusCode.BAD_REQUEST, "The requested version is no longer supported by this instance"));
	}
	
	@RequestMapping("/3.0/content.*")
	public void content(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			ContentQuery filter = builder.build(request);
			
			List<String> uris = getUriList(request);
			if(!uris.isEmpty()) {
			    modelAndViewFor(request, response, QueryResult.of(Iterables.filter(Iterables.concat(executor.executeUriQuery(uris, filter).values()),Content.class)),filter.getConfiguration());
			} else {
			    List<String> ids = getIdList(request);
			    if(!ids.isEmpty()) {
			        modelAndViewFor(request, response, QueryResult.of(Iterables.filter(Iterables.concat(executor.executeIdQuery(decode(ids), filter).values()),Content.class)),filter.getConfiguration());
			    } else {
			        throw new IllegalArgumentException("Must specify content uri or id");
			    }
			}
		} catch (Exception e) {
			errorViewFor(request, response, ErrorSummary.forException(e));
		}
	}

    private Iterable<Long> decode(List<String> ids) {
        return Lists.transform(ids, new Function<String, Long>() {
            @Override
            public Long apply(String input) {
                return idCodec.decode(input).longValue();
            }
        });
    }

    private List<String> getUriList(HttpServletRequest request) {
        return split(request.getParameter("uri"));
    }

    private List<String> getIdList(HttpServletRequest request) {
        return split(request.getParameter("id"));
    }

    private ImmutableList<String> split(String parameter) {
        if(parameter == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(URI_SPLITTER.split(parameter));
    }
}