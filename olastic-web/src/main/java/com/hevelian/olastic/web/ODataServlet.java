package com.hevelian.olastic.web;

import com.hevelian.olastic.config.ESConfig;
import com.hevelian.olastic.core.ElasticOData;
import com.hevelian.olastic.core.api.edm.provider.ElasticCsdlEdmProvider;
import com.hevelian.olastic.core.api.edm.provider.MultyElasticIndexCsdlEdmProvider;
import com.hevelian.olastic.core.elastic.mappings.DefaultMetaDataProvider;
import com.hevelian.olastic.core.elastic.mappings.MappingMetaDataProvider;
import com.hevelian.olastic.core.processors.impl.EntityCollectionProcessorHandler;
import com.hevelian.olastic.core.processors.impl.EntityProcessorHandler;
import com.hevelian.olastic.core.processors.impl.PrimitiveProcessorImpl;
import org.apache.olingo.commons.api.edm.provider.CsdlEdmProvider;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.elasticsearch.client.Client;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

/**
 * OData servlet that currently connects to the local instance of the
 * Elasticsearch and exposes its mappings and data through OData interface.
 *
 * @author yuflyud
 * @author rdidyk
 */
public class ODataServlet extends HttpServlet {

    private static final long serialVersionUID = -7048611704658443045L;

    private Client client;
    private Set<String> indices;

    @Override
    public void init() throws ServletException {
        ESConfig config = (ESConfig) getServletContext().getAttribute(ESConfig.getName());
        client = config.getClient();
        indices = config.getIndices();
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        OData odata = ElasticOData.newInstance();
        ServiceMetadata matadata = createServiceMetadata(req, odata, createEdmProvider());
        ODataHttpHandler handler = odata.createHandler(matadata);
        registerProcessors(handler);
        handler.process(req, resp);
    }

    /**
     * Create's {@link ServiceMetadata} metadata.
     *
     * @param req
     *            http request
     * @param odata
     *            OData instance
     * @param provider
     *            CSDL provider
     * @return metadata
     */
    protected ServiceMetadata createServiceMetadata(HttpServletRequest req, OData odata,
            ElasticCsdlEdmProvider provider) {
        return odata.createServiceMetadata(provider, new ArrayList<>());
    }

    /**
     * Create's {@link CsdlEdmProvider} provider.
     *
     * @return provider instance
     */
    protected ElasticCsdlEdmProvider createEdmProvider() {
        return new MultyElasticIndexCsdlEdmProvider(createMetaDataProvider(), indices);
    }

    /**
     * Create's {@link MappingMetaDataProvider} provider.
     *
     * @return provider instance
     */
    protected MappingMetaDataProvider createMetaDataProvider() {
        return new DefaultMetaDataProvider(client);
    }

    /**
     * Registers additional custom processor implementations for handling OData
     * requests
     *
     * @param handler
     *            OData handler
     */
    protected void registerProcessors(ODataHttpHandler handler) {
        handler.register(new PrimitiveProcessorImpl());
        handler.register(new EntityProcessorHandler());
        handler.register(new EntityCollectionProcessorHandler());
    }

    public Client getClient() {
        return client;
    }

    public Set<String> getIndices() {
        return indices;
    }
}
