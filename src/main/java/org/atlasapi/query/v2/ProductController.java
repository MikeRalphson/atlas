package org.atlasapi.query.v2;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atlasapi.application.query.ApplicationConfigurationFetcher;
import org.atlasapi.content.criteria.ContentQuery;
import org.atlasapi.media.entity.Content;
import org.atlasapi.media.product.Product;
import org.atlasapi.persistence.media.product.ProductResolver;
import org.atlasapi.output.ErrorSummary;
import org.atlasapi.output.AtlasModelWriter;
import org.atlasapi.output.QueryResult;
import org.atlasapi.persistence.content.query.KnownTypeQueryExecutor;
import org.atlasapi.persistence.logging.AdapterLog;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.metabroadcast.common.http.HttpStatusCode;
import com.metabroadcast.common.ids.NumberToShortStringCodec;
import com.metabroadcast.common.ids.SubstitutionTableNumberCodec;
import com.metabroadcast.common.query.Selection;

@Controller
public class ProductController extends BaseController<Iterable<Product>> {
    
    private static final ErrorSummary NOT_FOUND = new ErrorSummary(new NullPointerException()).withErrorCode("PRODUCT_NOT_FOUND").withStatusCode(HttpStatusCode.NOT_FOUND);
    private static final ErrorSummary FORBIDDEN = new ErrorSummary(new NullPointerException()).withErrorCode("PRODUCT_UNAVAILABLE").withStatusCode(HttpStatusCode.FORBIDDEN);

    private final ProductResolver productResolver;
    private final KnownTypeQueryExecutor queryExecutor;
    private final QueryController queryController;

    private final NumberToShortStringCodec idCodec = new SubstitutionTableNumberCodec();

    public ProductController(ProductResolver productResolver, KnownTypeQueryExecutor queryExecutor, ApplicationConfigurationFetcher configFetcher, AdapterLog log, AtlasModelWriter<? super Iterable<Product>> outputter, QueryController queryController) {
        super(configFetcher, log, outputter);
        this.productResolver = productResolver;
        this.queryExecutor = queryExecutor;
        this.queryController = queryController;
    }

    @RequestMapping(value={"3.0/products.*","/products.*"})
    public void products(HttpServletRequest req, HttpServletResponse resp) throws IOException  {
        try {
            final ContentQuery query = builder.build(req);
            modelAndViewFor(req, resp, Iterables.filter(productResolver.products(), new Predicate<Product>() {
                @Override
                public boolean apply(Product input) {
                    return query.allowsSource(input.getPublisher());
                }
            }), query.getConfiguration());
        } catch (Exception e) {
            errorViewFor(req, resp, ErrorSummary.forException(e));
        }
    }
    
    @RequestMapping(value={"3.0/products/{id}.*","/products/{id}.*"})
    public void topic(HttpServletRequest req, HttpServletResponse resp, @PathVariable("id") String id) throws IOException {
        
        ContentQuery query = builder.build(req);
        
        Optional<Product> productForId = productResolver.productForId(idCodec.decode(id).longValue());
        
        if(!productForId.isPresent()) {
            outputter.writeError(req, resp, NOT_FOUND.withMessage("Product " + id + " not found"));
            return;
        }
        
        Product product = productForId.get();
        
        if(!query.allowsSource(product.getPublisher())) {
            outputter.writeError(req, resp, FORBIDDEN.withMessage("Product " + id + " unavailable"));
            return;
        }
        
        modelAndViewFor(req, resp, ImmutableSet.<Product>of(product), query.getConfiguration());
    }
    
    @RequestMapping(value={"3.0/products/{id}/content.*", "/products/{id}/content"})
    public void topicContents(HttpServletRequest req, HttpServletResponse resp, @PathVariable("id") String id) throws IOException {
        ContentQuery query = builder.build(req);

        long decodedId = idCodec.decode(id).longValue();
        Optional<Product> productForId = productResolver.productForId(decodedId);
        
        if(!productForId.isPresent()) {
            outputter.writeError(req, resp, NOT_FOUND.withMessage("Product " + id + " not found"));
            return;
        }
        
        Product product = productForId.get();
        
        if(!query.allowsSource(product.getPublisher())) {
            outputter.writeError(req, resp, FORBIDDEN.withMessage("Product " + id + " unavailable"));
            return;
        }
        
        try {
            Selection selection = query.getSelection();
            QueryResult<Content, Product> result = QueryResult.of(Iterables.filter(Iterables.concat(queryExecutor.executeUriQuery(product.getContent(), query).values()),Content.class), product);
            queryController.modelAndViewFor(req, resp, result.withSelection(selection), query.getConfiguration());
        } catch (Exception e) {
            errorViewFor(req, resp, ErrorSummary.forException(e));
        }
    }
}
