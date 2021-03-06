package com.hevelian.olastic.core.elastic;

import com.hevelian.olastic.core.elastic.pagination.Pagination;
import com.hevelian.olastic.core.elastic.pagination.Sort;
import com.hevelian.olastic.core.elastic.queries.AggregateQuery;
import com.hevelian.olastic.core.elastic.queries.SearchQuery;
import com.hevelian.olastic.core.exceptions.SearchException;
import lombok.extern.log4j.Log4j2;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.*;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.NoNodeAvailableException;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

import java.util.List;
import java.util.Set;

/**
 * Central point to retrieve the data from Elasticsearch.
 * 
 * @author rdidyk
 */
@Log4j2
public class ESClient {

    private static ESClient INSTANCE;

    private Client client;

    private ESClient(Client client) {
        this.client = client;
    }

    /**
     * Get's instance.
     * 
     * @return created instance or if it wasn't initialized illegal state
     *         exception will be thrown
     */
    public static ESClient getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("Elasticsearch Client is not initialized.");
        }
        return INSTANCE;
    }

    /**
     * Method that initializes current client. It initializes new instance with
     * Elasticsearch Client. This method can be called only once, in other case
     * the illegal state exception will be thrown.
     * 
     * @param client
     *            Elasticsearch client instance
     */
    public static void init(Client client) {
        if (INSTANCE == null) {
            synchronized (ESClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ESClient(client);
                } else {
                    throw new IllegalStateException(
                            "Elastic to CSDL mapper is already initialized.");
                }
            }
        }
    }

    /**
     * Execute aggregate query request.
     * 
     * @param query
     *            aggregate query
     * @return ES search response
     */
    public SearchResponse executeRequest(AggregateQuery query) {
        SearchRequestBuilder requestBuilder = client.prepareSearch(query.getIndex())
                .setTypes(query.getTypes()).setQuery(query.getQueryBuilder());
        query.getAggregations().forEach(requestBuilder::addAggregation);
        query.getPipelineAggregations().forEach(requestBuilder::addAggregation);
        requestBuilder.setSize(0);
        return executeRequest(requestBuilder);
    }

    /**
     * Execute query request with filter and aggregations.
     * @param queries list of queries to execute
     * @return ES search response
     */
    public MultiSearchResponse executeRequest(List<SearchQuery> queries) {
        MultiSearchRequestBuilder multiSearchRequestBuilder = client.prepareMultiSearch();
        for (SearchQuery query : queries) {
            Pagination pagination = query.getPagination();
            SearchRequestBuilder requestBuilder = client.prepareSearch(query.getIndex())
                    .setTypes(query.getTypes()).setQuery(query.getQueryBuilder());
            if (pagination != null) {
                List<Sort> orderBy = pagination.getOrderBy();
                for (Sort sort : orderBy) {
                    FieldSortBuilder sortQuery = SortBuilders.fieldSort(sort.getProperty())
                            .order(SortOrder.valueOf(sort.getDirection().toString()));
                    requestBuilder.addSort(sortQuery);
                }
                requestBuilder.setSize(pagination.getTop()).setFrom(pagination.getSkip());
            }
            Set<String> fields = query.getFields();
            if (fields != null && !fields.isEmpty()) {
                requestBuilder.setFetchSource(fields.toArray(new String[fields.size()]), null);
            }
            multiSearchRequestBuilder.add(requestBuilder);
        }
        return executeRequest(multiSearchRequestBuilder);
    }

    /**
     * Execute query request with filter and aggregations.
     * @param query search query
     * @return ES search response
     */
    public SearchResponse executeRequest(SearchQuery query) {
        Pagination pagination = query.getPagination();
        SearchRequestBuilder requestBuilder = client.prepareSearch(query.getIndex())
                .setTypes(query.getTypes()).setQuery(query.getQueryBuilder());
        if (pagination != null) {
            List<Sort> orderBy = pagination.getOrderBy();
            for (Sort sort : orderBy) {
                FieldSortBuilder sortQuery = SortBuilders.fieldSort(sort.getProperty())
                        .order(SortOrder.valueOf(sort.getDirection().toString()));
                requestBuilder.addSort(sortQuery);
            }
            requestBuilder.setSize(pagination.getTop()).setFrom(pagination.getSkip());
        }
        Set<String> fields = query.getFields();
        if (fields != null && !fields.isEmpty()) {
            requestBuilder.setFetchSource(fields.toArray(new String[fields.size()]), null);
        }
        return executeRequest(requestBuilder);
    }

    /**
     * Method has to be used to execute any request. It has logging logic.
     *
     * @param request
     *            request to execute
     * @return request response
     */
    protected SearchResponse executeRequest(SearchRequestBuilder request) {
        SearchResponse response = null;
        ElasticsearchException searchError = null;
        try {
            response = request.execute().actionGet();
        } catch (SearchPhaseExecutionException | NoNodeAvailableException exception) {
            searchError = exception;
            throw new SearchException(searchError.getDetailedMessage());
        } finally {
            log.debug(String.format("Executing query request:%n%s", request.request()));
            if (response != null) {
                log.debug(String.format("Query execution took: %s", response.getTook()));
            } else {
                log.error("Failed to execute query: ", searchError);
            }
        }
        return response;
    }

    /**
     * Method has to be used to execute any request. It has logging logic.
     *
     * @param request
     *            request to execute
     * @return request response
     */
    protected MultiSearchResponse executeRequest(MultiSearchRequestBuilder request) {
        MultiSearchResponse response = null;
        ElasticsearchException searchError = null;
        try {
            response = request.execute().actionGet();
        } catch (SearchPhaseExecutionException | NoNodeAvailableException exception) {
            searchError = exception;
            throw new SearchException(searchError.getDetailedMessage());
        } finally {
            log.debug(String.format("Executing query requests:%n%s", request.request().requests()));
            if (response == null) {
                log.error("Failed to execute query: ", searchError);
            }
        }
        return response;
    }

    public Client getClient() {
        return client;
    }

}
