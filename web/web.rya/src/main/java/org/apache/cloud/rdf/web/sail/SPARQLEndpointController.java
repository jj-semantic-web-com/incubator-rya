package org.apache.cloud.rdf.web.sail;

import java.util.logging.Level;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.log4j.Logger;
import org.apache.rya.api.RdfCloudTripleStoreConfiguration;
import org.apache.rya.api.security.SecurityProvider;
import org.openrdf.query.MalformedQueryException;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.parser.ParsedBooleanQuery;
import org.openrdf.query.parser.ParsedGraphQuery;
import org.openrdf.query.parser.ParsedOperation;
import org.openrdf.query.parser.ParsedQuery;
import org.openrdf.query.parser.ParsedTupleQuery;
import org.openrdf.query.parser.ParsedUpdate;
import org.openrdf.query.parser.QueryParserUtil;
import org.openrdf.query.resultio.TupleQueryResultFormat;
import org.openrdf.query.resultio.TupleQueryResultWriterFactory;
import org.openrdf.query.resultio.TupleQueryResultWriterRegistry;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.RDFFormat;
import org.openrdf.rio.RDFWriterFactory;
import org.openrdf.rio.RDFWriterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author http://www.turnguard.com/turnguard
 * 
 */
@Controller
public class SPARQLEndpointController {
    
    private static final Logger LOGGER = Logger.getLogger(SPARQLEndpointController.class);  
    private static final int QUERY_TIME_OUT_SECONDS = 120;
    
    @Autowired
    SailRepository repository;

    @Autowired
    SecurityProvider securityProvider;
 
    @RequestMapping(value = "/sparql", method = {RequestMethod.GET, RequestMethod.POST})
    public void sparql(            
            @RequestParam("query") final String query, 
            final HttpServletRequest request, 
            final HttpServletResponse response
    ) throws MalformedQueryException{
        ParsedOperation parsedQuery;
        try {
            
            Assert.hasLength(query);
            
            parsedQuery = QueryParserUtil.parseOperation(QueryLanguage.SPARQL, query, "<urn:base:>");
            
            if(parsedQuery instanceof ParsedGraphQuery){
                System.out.println("parsedGraphQuery");
            } else
            if(parsedQuery instanceof ParsedTupleQuery){
                System.out.println("parsedTupleQuery");
            } else
            if(parsedQuery instanceof ParsedBooleanQuery){
                System.out.println("parsedBooleanQuery");
            } else
            if(parsedQuery instanceof ParsedUpdate){
                System.out.println("parsedUpdate");
            }
        } finally {
        
        }    
    }   
    
    /**
     * Get an appropriate TupleQueryResultWriterFactory for the given acceptHeader
     * @param acceptHeader
     * @return TupleQueryResultWriterFactory
     */
    private TupleQueryResultWriterFactory getTupleQueryResultWriterFactory(String acceptHeader){
        return TupleQueryResultWriterRegistry.getInstance().getAll().stream().filter(tqrw->{            
            return tqrw.getTupleQueryResultFormat().getMIMETypes().stream().anyMatch(mimeType->{
                return acceptHeader.contains(mimeType);
            });
        })
        .findFirst()
        .orElseGet(()->{ return TupleQueryResultWriterRegistry.getInstance().get(TupleQueryResultFormat.SPARQL); });
    }
    
    /**
     * Get an appropriate RDFWriterFactory for the given acceptHeader
     * @param acceptHeader
     * @return RDFWriterFactory
     */
    private RDFWriterFactory getRDFWriterFactory(String acceptHeader){
        return RDFWriterRegistry.getInstance().getAll().stream().filter(rdfwf->{                 
            return rdfwf.getRDFFormat().getMIMETypes().stream().anyMatch(mimeType->{
                return acceptHeader.contains(mimeType);
            });
        })
        .findFirst()
        .orElseGet(()->{ return RDFWriterRegistry.getInstance().get(RDFFormat.TURTLE); });    
    }
}
