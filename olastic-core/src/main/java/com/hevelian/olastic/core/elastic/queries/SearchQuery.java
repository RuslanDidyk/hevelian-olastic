package com.hevelian.olastic.core.elastic.queries;

import java.util.Set;

import org.elasticsearch.index.query.QueryBuilder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.FieldDefaults;

/**
 * Search query with fields.
 * 
 * @author rdidyk
 */
@Getter
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SearchQuery extends Query {

    @NonNull
    Set<String> fields;

    /**
     * Constructor to initialize parameters.
     * 
     * @param index
     *            index name
     * @param type
     *            type name
     * @param queryBuilder
     *            main query builder
     * @param fields
     *            fields to search
     */
    public SearchQuery(String index, String type, QueryBuilder queryBuilder, Set<String> fields) {
        super(index, type, queryBuilder);
        this.fields = fields;
    }

}