package com.hevelian.olastic.core.api.edm.provider;

import com.carrotsearch.hppc.cursors.ObjectCursor;
import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.hevelian.olastic.core.common.*;
import com.hevelian.olastic.core.elastic.ElasticConstants;
import com.hevelian.olastic.core.elastic.mappings.ElasticToCsdlMapper;
import com.hevelian.olastic.core.elastic.mappings.IElasticToCsdlMapper;
import com.hevelian.olastic.core.elastic.mappings.IMappingMetaDataProvider;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.*;
import org.apache.olingo.commons.api.ex.ODataException;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.index.mapper.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link CsdlEdmProvider} implementation that generates the service/metadata
 * documents based on the Elasticsearch mappings.
 *
 * @author yuflyud
 * @contributor rdidyk
 */
public abstract class ElasticCsdlEdmProvider extends CsdlAbstractEdmProvider {

    private static final FullQualifiedName DEFAULT_CONTAINER_NAME = new FullQualifiedName("OData",
            "ODataService");

    private final PrimitiveTypeMapper primitiveTypeMapper;
    private final NestedTypeMapper nestedTypeMapper;
    private final IMappingMetaDataProvider mappingMetaDataProvider;
    protected final IElasticToCsdlMapper csdlMapper;

    private FullQualifiedName containerName;

    /**
     * Initializes mapping metadata provider with default
     * {@link IElasticToCsdlMapper} implementation.
     *
     * @param metaDataProvider
     *            mapping meta data provider
     */
    public ElasticCsdlEdmProvider(IMappingMetaDataProvider metaDataProvider) {
        this(metaDataProvider, new ElasticToCsdlMapper());
    }

    /**
     * Initializes mapping metadata provider with custom
     * {@link IElasticToCsdlMapper} implementation.
     *
     * @param metaDataProvider
     *            mapping meta data provider
     * @param csdlMapper
     *            ES to CSDL mapper
     */
    public ElasticCsdlEdmProvider(IMappingMetaDataProvider metaDataProvider,
            IElasticToCsdlMapper csdlMapper) {
        this(metaDataProvider, csdlMapper, new NestedPerIndexMappingStrategy());
    }

    /**
     * Initializes mapping metadata provider with custom
     * {@link NestedMappingStrategy} implementation.
     *
     * @param metaDataProvider
     *            mapping meta data provider
     * @param nestedMappingStrategy
     *            nested mapping strategy
     */
    public ElasticCsdlEdmProvider(IMappingMetaDataProvider metaDataProvider,
            NestedMappingStrategy nestedMappingStrategy) {
        this(metaDataProvider, new ElasticToCsdlMapper(), nestedMappingStrategy);
    }

    /**
     * Initializes mapping metadata provider with custom
     * {@link IElasticToCsdlMapper} and {@link NestedMappingStrategy}
     * implementation.
     *
     * @param metaDataProvider
     *            mapping meta data provider
     * @param csdlMapper
     *            ES to CSDL mapper
     * @param nestedMappingStrategy
     *            nested mapping strategy
     */
    public ElasticCsdlEdmProvider(IMappingMetaDataProvider metaDataProvider,
            IElasticToCsdlMapper csdlMapper, NestedMappingStrategy nestedMappingStrategy) {
        this.mappingMetaDataProvider = metaDataProvider;
        this.csdlMapper = csdlMapper;
        this.primitiveTypeMapper = new PrimitiveTypeMapper();
        this.nestedTypeMapper = new NestedTypeMapper(nestedMappingStrategy, metaDataProvider,
                csdlMapper);
        setContainerName(DEFAULT_CONTAINER_NAME);
    }

    /**
     * Get entity type definition by fully qualified name. This method calls the
     * mappingMetaDataProvider method to retrieve the corresponding Elastic type
     * mappings. <br>
     * The _id field is added and used as the OData key property.
     */
    @Override
    public ElasticCsdlEntityType getEntityType(FullQualifiedName entityTypeName)
            throws ODataException {
        String eIndex = namespaceToIndex(entityTypeName.getNamespace());
        // If there is no index mapping for provided namespace - return null, no
        // entity type is found.
        if (eIndex == null) {
            return null;
        }
        // TODO check type exists
        String eType = entityTypeName.getName();
        ElasticCsdlEntityType entityType = new ElasticCsdlEntityType();
        entityType.setEIndex(eIndex);
        entityType.setName(eType);
        // Retrieve type fields from Elasticsearch
        entityType.setProperties(getProperties(entityTypeName,
                mappingMetaDataProvider.getMappingForType(eIndex, eType)));

        // Add _id property
        CsdlProperty idProperty = new ElasticCsdlProperty().setName(ElasticConstants.ID_FIELD_NAME)
                .setType(EdmPrimitiveTypeKind.String.getFullQualifiedName()).setNullable(false);
        entityType.getProperties().add(idProperty);

        // Add navigation properties
        entityType.setNavigationProperties(getNavigationProperties(entityTypeName));

        // create PropertyRef for Key element
        CsdlPropertyRef propertyRef = new CsdlPropertyRef();
        propertyRef.setName(ElasticConstants.ID_FIELD_NAME);
        entityType.setKey(Arrays.asList(propertyRef));
        return entityType;
    }

    /**
     * Retrieve properties for the entity type.
     *
     * @param entityTypeName
     *            name of the entity type.
     * @return list of properties
     */
    protected List<CsdlProperty> getProperties(FullQualifiedName entityTypeName,
            MappingMetaData metaData) throws ODataException {
        try {
            ParsedMapWrapper eTypeProperties = new ParsedMapWrapper(metaData.sourceAsMap())
                    .mapValue(ElasticConstants.PROPERTIES_PROPERTY);
            List<CsdlProperty> properties = new ArrayList<>();
            String eIndex = namespaceToIndex(entityTypeName.getNamespace());
            String eType = entityTypeName.getName();
            for (String eFieldName : eTypeProperties.map.keySet()) {
                String name = csdlMapper.eFieldToCsdlProperty(eIndex, eType, eFieldName);
                ParsedMapWrapper fieldMap = eTypeProperties.mapValue(eFieldName);
                String eFieldType = fieldMap.stringValue(ElasticConstants.FIELD_DATATYPE_PROPERTY);
                FullQualifiedName type;
                if (ObjectMapper.NESTED_CONTENT_TYPE.equals(eFieldType)) {
                    type = getNestedTypeMapper().map(eIndex, eType, eFieldName);
                } else {
                    type = primitiveTypeMapper.map(eFieldType).getFullQualifiedName();
                }
                properties.add(new ElasticCsdlProperty().setEIndex(eIndex).setEType(eType)
                        .setEField(eFieldName).setName(name).setType(type)
                        .setCollection(csdlMapper.eFieldToCollection(eIndex, eType, eFieldName)));
            }
            return properties;
        } catch (IOException e) {
            throw new ODataException("Unable to parse the mapping response from Elastcsearch.", e);
        }
    }

    /**
     * Retrieve navigation properties for the entity type.
     *
     * @param entityTypeName
     *            name of the entity type.
     * @return list of navigation properties.
     */
    protected List<CsdlNavigationProperty> getNavigationProperties(
            FullQualifiedName entityTypeName) {
        List<CsdlNavigationProperty> navPropList = new ArrayList<>();

        String namespace = entityTypeName.getNamespace();
        String index = namespaceToIndex(namespace);
        ImmutableOpenMap<String, FieldMappingMetaData> eFieldMappings = mappingMetaDataProvider
                .getMappingsForField(index, ElasticConstants.PARENT_PROPERTY);
        String eType = entityTypeName.getName();

        for (ObjectObjectCursor<String, FieldMappingMetaData> e : eFieldMappings) {
            ParsedMapWrapper eParent = new ParsedMapWrapper(e.value.sourceAsMap())
                    .mapValue(ElasticConstants.PARENT_PROPERTY);
            if (eParent.map == null) {
                continue;
            }
            String eParentType = eParent.stringValue(ElasticConstants.FIELD_DATATYPE_PROPERTY);
            // Create Child Relations
            if (eType.equals(eParentType)) {
                CsdlNavigationProperty navProp = new CsdlNavigationProperty()
                        .setName(csdlMapper.eChildRelationToNavPropName(index, e.key,
                                entityTypeName.getName()))
                        .setType(csdlMapper.eTypeToEntityType(index, e.key)).setCollection(true)
                        .setPartner(csdlMapper.eParentRelationToNavPropName(index, eType,
                                new FullQualifiedName(namespace, e.key).getName()));
                navPropList.add(navProp);
            }
            // Create Parent Relation
            if (eType.equals(e.key)) {
                CsdlNavigationProperty navProp = new CsdlNavigationProperty()
                        .setName(csdlMapper.eParentRelationToNavPropName(index, eParentType,
                                entityTypeName.getName()))
                        .setType(csdlMapper.eTypeToEntityType(index, eParentType))
                        .setNullable(false).setPartner(csdlMapper.eChildRelationToNavPropName(index,
                                e.key, new FullQualifiedName(namespace, eParentType).getName()));
                navPropList.add(navProp);
            }
        }
        return navPropList;
    }

    @Override
    public ElasticCsdlEntitySet getEntitySet(FullQualifiedName entityContainer,
            String entitySetName) throws ODataException {
        FullQualifiedName entityTypeName = entitySetToEntityType(entityContainer, entitySetName);
        if (entityTypeName == null) {
            return null;
        }
        String eIndex = namespaceToIndex(entityTypeName.getNamespace());
        ElasticCsdlEntitySet entitySet = new ElasticCsdlEntitySet();
        entitySet.setEIndex(eIndex);
        entitySet.setEType(entitySetName);
        entitySet.setName(csdlMapper.eTypeToEntitySet(eIndex, entitySetName));
        entitySet.setType(entityTypeName);

        // define navigation property bindings
        List<CsdlNavigationProperty> navigationProperties = getNavigationProperties(entityTypeName);
        List<CsdlNavigationPropertyBinding> navPropBindingList = new ArrayList<>();
        for (CsdlNavigationProperty property : navigationProperties) {
            CsdlNavigationPropertyBinding navPropBinding = new CsdlNavigationPropertyBinding();
            navPropBinding.setTarget(csdlMapper.eTypeToEntitySet(
                    namespaceToIndex(property.getTypeFQN().getNamespace()),
                    property.getTypeFQN().getName()));
            navPropBinding.setPath(property.getName());
            navPropBindingList.add(navPropBinding);
        }
        entitySet.setNavigationPropertyBindings(navPropBindingList);
        return entitySet;
    }

    /**
     * Get a type of the EntitySet.
     *
     * @param entityContainer
     *            name of the Entity Container.
     * @param entitySetName
     *            name of the Entity Set.
     * @return Entity type of the Entity Set.
     * @throws ODataException
     *             in case no entity container was found.
     */
    protected FullQualifiedName entitySetToEntityType(FullQualifiedName entityContainer,
            String entitySetName) throws ODataException {
        FullQualifiedName entityTypeName;
        // Check whether schema entity container is used
        String namespace = entityContainer.getNamespace();
        if (getSchemaNamespaces().contains(namespace)) {
            entityTypeName = csdlMapper.eTypeToEntityType(namespaceToIndex(namespace),
                    new FullQualifiedName(namespace, entitySetName).getName());
        }
        // Check whether root entity container is used
        else if (getContainerName().getNamespace().equals(namespace)) {
            CsdlEntitySet entitySet = getEntityContainer().getEntitySet(entitySetName);
            entityTypeName = entitySet == null ? null : entitySet.getTypeFQN();
        } else {
            throw new ODataException("No entity container found for schema.");
        }
        return entityTypeName;
    }

    @Override
    public CsdlEntityContainerInfo getEntityContainerInfo(FullQualifiedName entityContainerName) {
        CsdlEntityContainerInfo entityContainerInfo = null;
        if (entityContainerName == null) {
            entityContainerInfo = new CsdlEntityContainerInfo();
            entityContainerInfo.setContainerName(getContainerName());
        }
        return entityContainerInfo;
    }

    @Override
    public List<CsdlSchema> getSchemas() throws ODataException {
        List<CsdlSchema> schemas = new ArrayList<>();
        for (String namespace : getSchemaNamespaces()) {
            // create Schema
            CsdlSchema schema = new CsdlSchema();
            schema.setNamespace(namespace);

            // add Entity Types
            String index = namespaceToIndex(namespace);
            schema.setEntityTypes(getEnityTypes(index));
            // add Complex Types
            schema.setComplexTypes(getNestedTypeMapper().getComplexTypes(index));

            schema.setEntityContainer(getEntityContainerForSchema(namespace));
            schemas.add(schema);
        }
        return schemas;
    }

    /**
     * Get a list of Entity Types for specific Elasticsearch index.
     *
     * @param index
     *            index name
     * @return list of Entity Types
     * @throws ODataException
     *             if any error occurred
     */
    protected List<CsdlEntityType> getEnityTypes(String index) throws ODataException {
        List<CsdlEntityType> entityTypes = new ArrayList<>();
        for (ObjectCursor<String> key : mappingMetaDataProvider.getAllMappings(index).keys()) {
            CsdlEntityType entityType = getEntityType(
                    csdlMapper.eTypeToEntityType(index, key.value));
            entityTypes.add(entityType);
        }
        return entityTypes;
    }

    /**
     * Get a specific entity container for a schema.
     *
     * @param namespace
     *            csdl schema namespace.
     * @return Entity Container.
     */
    protected CsdlEntityContainer getEntityContainerForSchema(String namespace)
            throws ODataException {
        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(getContainerName().getName());

        List<CsdlEntitySet> entitySets = new ArrayList<>();
        for (ObjectCursor<String> key : mappingMetaDataProvider
                .getAllMappings(namespaceToIndex(namespace)).keys()) {
            entitySets.add(getEntitySet(
                    new FullQualifiedName(namespace, getContainerName().getName()), key.value));
        }
        entityContainer.setEntitySets(entitySets);
        return entityContainer;
    }

    @Override
    public CsdlEntityContainer getEntityContainer() throws ODataException {
        // create EntityContainer
        CsdlEntityContainer entityContainer = new CsdlEntityContainer();
        entityContainer.setName(getContainerName().getName());

        List<CsdlSchema> schemas = getSchemas();
        for (CsdlSchema schema : schemas) {
            for (CsdlEntitySet entitySet : schema.getEntityContainer().getEntitySets()) {
                if (entitySet.isIncludeInServiceDocument()) {
                    entityContainer.getEntitySets().add(entitySet);
                }
            }
        }
        return entityContainer;
    }

    @Override
    public CsdlComplexType getComplexType(FullQualifiedName complexTypeName) throws ODataException {
        for (CsdlSchema schema : getSchemas()) {
            if (schema.getNamespace().equals(complexTypeName.getNamespace())) {
                return schema.getComplexType(complexTypeName.getName());
            }
        }
        return null;
    }

    /**
     * Return's list of Schema name spaces.
     *
     * @return list of name spaces
     */
    protected abstract List<String> getSchemaNamespaces();

    /**
     * Map CSDL namespace to Elasticsearch index.
     *
     * @param namespace
     *            CSDL namespace.
     * @return index that corresponds to the namespace or null if there is no
     *         mapping for this namespace.
     */
    protected abstract String namespaceToIndex(String namespace);

    public IElasticToCsdlMapper getCsdlMapper() {
        return csdlMapper;
    }

    public NestedTypeMapper getNestedTypeMapper() {
        return nestedTypeMapper;
    }

    public IMappingMetaDataProvider getMappingMetaDataProvider() {
        return mappingMetaDataProvider;
    }

    public FullQualifiedName getContainerName() {
        return containerName;
    }

    public void setContainerName(FullQualifiedName containerName) {
        this.containerName = containerName;
    }
}