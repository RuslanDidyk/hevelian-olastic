package com.hevelian.olastic.core.edm.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.provider.CsdlComplexType;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainer;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityContainerInfo;
import org.apache.olingo.commons.api.edm.provider.CsdlEntitySet;
import org.apache.olingo.commons.api.edm.provider.CsdlEntityType;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlNavigationPropertyBinding;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlPropertyRef;
import org.apache.olingo.commons.api.edm.provider.CsdlSchema;
import org.apache.olingo.commons.api.ex.ODataException;
import org.elasticsearch.action.admin.indices.mapping.get.GetFieldMappingsResponse.FieldMappingMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.collect.ImmutableOpenMap.Builder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import com.hevelian.olastic.core.common.NestedMappingStrategy;
import com.hevelian.olastic.core.common.NestedTypeMapper;
import com.hevelian.olastic.core.edm.utils.MetaDataUtils;
import com.hevelian.olastic.core.elastic.ElasticConstants;
import com.hevelian.olastic.core.elastic.mappings.ElasticToCsdlMapper;
import com.hevelian.olastic.core.elastic.mappings.IElasticToCsdlMapper;
import com.hevelian.olastic.core.elastic.mappings.IMappingMetaDataProvider;

/**
 * JUnit tests for {@link MultyElasticIndexCsdlEdmProvider} class.
 * 
 * @author rdidyk
 */
@RunWith(MockitoJUnitRunner.class)
public class MultyElasticIndexCsdlEdmProviderTest {

    private static final String AUTHOR_TYPE = "author";
    private static final String AUTHORS_INDEX = "authors";
    private static final String WRITERS_INDEX = "writers";
    private static final String BOOK_TYPE = "book";
    private static final FullQualifiedName AUTHORS_FQN = new FullQualifiedName(
            addNamespace(AUTHORS_INDEX));
    private static final FullQualifiedName AUTHOR_FQN = new FullQualifiedName(
            addNamespace(AUTHORS_INDEX), AUTHOR_TYPE);
    private static final FullQualifiedName WRITERS_FQN = new FullQualifiedName(
            addNamespace(WRITERS_INDEX));
    private static final FullQualifiedName BOOK_FQN = new FullQualifiedName(
            addNamespace(AUTHORS_INDEX), BOOK_TYPE);
    private static final String AUTHORS_FQN_STRING = AUTHORS_FQN.getFullQualifiedNameAsString();
    private static final String WRITERS_FQN_STRING = WRITERS_FQN.getFullQualifiedNameAsString();

    private static Set<String> indices;
    @Mock
    private IMappingMetaDataProvider metaDataProvider;
    @Mock
    private NestedMappingStrategy nestedMappingStrategy;

    @BeforeClass
    public static void setUpBeforeClass() {
        indices = new HashSet<String>();
        indices.add(AUTHORS_INDEX);
        indices.add(WRITERS_INDEX);
    }

    private static String addNamespace(String... path) {
        StringBuffer result = new StringBuffer(ElasticToCsdlMapper.DEFAULT_NAMESPACE);
        for (int i = 0; i < path.length; i++) {
            if (i == 0 || i != path.length - 1) {
                result.append(MetaDataUtils.NEMESPACE_SEPARATOR);
            }
            result.append(path[i]);
        }
        return result.toString();
    }

    @Before
    public void setUp() {
        when(nestedMappingStrategy.getComplexTypeName(anyString(), anyString()))
                .thenAnswer(new Answer<String>() {
                    @Override
                    public String answer(InvocationOnMock invocation) throws Throwable {
                        return (String) invocation.getArguments()[1];
                    }
                });
    }

    @Test
    public void constructor_MappingMetadataProvider_Setted() {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertEquals(metaDataProvider, edmProvider.getMappingMetaDataProvider());
        assertNotNull(edmProvider.getCsdlMapper());
        assertNotNull(edmProvider.getNestedTypeMapper());
    }

    @Test
    public void constructor_MappingMetadataProviderAndCsdlMapper_Setted() {
        IElasticToCsdlMapper csdlMapper = mock(IElasticToCsdlMapper.class);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices, csdlMapper);
        assertEquals(metaDataProvider, edmProvider.getMappingMetaDataProvider());
        assertEquals(csdlMapper, edmProvider.getCsdlMapper());
        assertNotNull(edmProvider.getNestedTypeMapper());
    }

    @Test
    public void constructor_MappingMetadataProviderAndCsdlMapperAndNestedMappingStrategy_Setted() {
        IElasticToCsdlMapper csdlMapper = mock(IElasticToCsdlMapper.class);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices, csdlMapper, nestedMappingStrategy);
        assertEquals(metaDataProvider, edmProvider.getMappingMetaDataProvider());
        assertEquals(csdlMapper, edmProvider.getCsdlMapper());
        assertNotNull(edmProvider.getNestedTypeMapper());
        assertEquals(nestedMappingStrategy,
                edmProvider.getNestedTypeMapper().getNestedMappingStrategy());
    }

    @Test
    public void getSchemaNamespaces_SetOfIndices_ShemaNamespacesRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        List<String> schemaNamespaces = edmProvider.getSchemaNamespaces();
        assertEquals(2, schemaNamespaces.size());
        assertTrue(schemaNamespaces.contains(AUTHORS_FQN_STRING));
        assertTrue(schemaNamespaces.contains(WRITERS_FQN_STRING));
    }

    @Test
    public void getSchemaNamespaces_EmptyIndices_EmptyShemaNamespacesRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, new HashSet<String>());
        List<String> schemaNamespaces = edmProvider.getSchemaNamespaces();
        assertTrue(schemaNamespaces.isEmpty());
    }

    @Test
    public void namespaceToIndex_DifferentNamespaces_ExpectedValuesRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertEquals(AUTHORS_INDEX, edmProvider.namespaceToIndex(AUTHORS_FQN_STRING));
        assertEquals(WRITERS_INDEX, edmProvider.namespaceToIndex(WRITERS_FQN_STRING));
        assertNull(edmProvider.namespaceToIndex("Olingo.Test.authors"));
    }

    @Test
    public void getProperties_EntityTypeNameAndCorrectMetaData_ListOfCsdlPropertiesRetrieved()
            throws IOException, ODataException {
        Map<String, Object> dimension = new HashMap<>();
        dimension.put("type", "nested");
        HashMap<Object, Object> dimensionProperties = new HashMap<>();
        dimensionProperties.put("name", "string");
        dimensionProperties.put("state", "boolean");
        dimension.put("properties", dimensionProperties);
        Map<String, Object> properties = new HashMap<>();
        properties.put("dimension", dimension);
        HashMap<Object, Object> currentProperties = new HashMap<>();
        currentProperties.put("type", "boolean");
        properties.put("current", currentProperties);
        HashMap<Object, Object> idProperties = new HashMap<>();
        idProperties.put("type", "string");
        properties.put("id", idProperties);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("properties", properties);
        MappingMetaData mappingMetaData = mock(MappingMetaData.class);
        when(mappingMetaData.sourceAsMap()).thenReturn(metadata);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices, nestedMappingStrategy);
        List<CsdlProperty> csdlProperties = edmProvider.getProperties(AUTHOR_FQN, mappingMetaData);
        assertEquals(3, csdlProperties.size());
        for (CsdlProperty property : csdlProperties) {
            assertTrue(property instanceof ElasticCsdlProperty);
            assertEquals(AUTHORS_INDEX, ((ElasticCsdlProperty) property).getEIndex());
            assertEquals(AUTHOR_TYPE, ((ElasticCsdlProperty) property).getEType());
            assertEquals(property.getName(), ((ElasticCsdlProperty) property).getEField());
            assertNotNull(property.getTypeAsFQNObject());
        }
    }

    @Test(expected = ODataException.class)
    public void getProperties_MetaDataThrowsIOException_ODataExceptionRetrieved()
            throws IOException, ODataException {
        MappingMetaData mappingMetaData = mock(MappingMetaData.class);
        when(mappingMetaData.sourceAsMap()).thenThrow(new IOException("test cause"));
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        edmProvider.getProperties(AUTHOR_FQN, mappingMetaData);
    }

    @Test
    public void getNavigationProperties_EntityTypeNameAndEmptyMappings_EmptyListRetrieved() {
        Builder<String, FieldMappingMetaData> builder = ImmutableOpenMap.builder();
        ImmutableOpenMap<String, FieldMappingMetaData> map = builder.build();
        when(metaDataProvider.getMappingsForField(AUTHORS_INDEX, ElasticConstants.PARENT_PROPERTY))
                .thenReturn(map);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertTrue(edmProvider.getNavigationProperties(AUTHOR_FQN).isEmpty());
    }

    @Test
    public void getNavigationProperties_EntityTypeNameAndMappingsEmptyValueMap_EmptyListRetrieved() {
        Builder<String, FieldMappingMetaData> builder = ImmutableOpenMap.builder();
        FieldMappingMetaData mappingMetaData = mock(FieldMappingMetaData.class);
        when(mappingMetaData.sourceAsMap()).thenReturn(new HashMap<String, Object>());
        builder.put(BOOK_TYPE, mappingMetaData);
        ImmutableOpenMap<String, FieldMappingMetaData> map = builder.build();
        when(metaDataProvider.getMappingsForField(AUTHORS_INDEX, ElasticConstants.PARENT_PROPERTY))
                .thenReturn(map);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertTrue(edmProvider.getNavigationProperties(AUTHOR_FQN).isEmpty());
    }

    @Test
    public void getNavigationProperties_EntityTypeNameAndMappings_OneChildPropertyRetrieved() {
        ImmutableOpenMap<String, FieldMappingMetaData> map = getMappingsForNestedProperties();
        when(metaDataProvider.getMappingsForField(AUTHORS_INDEX, ElasticConstants.PARENT_PROPERTY))
                .thenReturn(map);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        List<CsdlNavigationProperty> navigationProperties = edmProvider
                .getNavigationProperties(AUTHOR_FQN);
        assertEquals(1, navigationProperties.size());
        CsdlNavigationProperty navigationProperty = navigationProperties.get(0);
        assertEquals(BOOK_TYPE, navigationProperty.getName());
        assertTrue(navigationProperty.isCollection());
        assertEquals(AUTHOR_TYPE, navigationProperty.getPartner());
    }

    @Test
    public void getNavigationProperties_EntityTypeNameAndMappings_OneParentPropertyRetrieved() {
        ImmutableOpenMap<String, FieldMappingMetaData> map = getMappingsForNestedProperties();
        when(metaDataProvider.getMappingsForField(AUTHORS_INDEX, ElasticConstants.PARENT_PROPERTY))
                .thenReturn(map);
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        List<CsdlNavigationProperty> navigationProperties = edmProvider
                .getNavigationProperties(BOOK_FQN);
        assertEquals(1, navigationProperties.size());
        CsdlNavigationProperty navigationProperty = navigationProperties.get(0);
        assertEquals(AUTHOR_TYPE, navigationProperty.getName());
        assertFalse(navigationProperty.isCollection());
        assertEquals(BOOK_TYPE, navigationProperty.getPartner());
    }

    private static ImmutableOpenMap<String, FieldMappingMetaData> getMappingsForNestedProperties() {
        Builder<String, FieldMappingMetaData> mappingsBuilder = ImmutableOpenMap.builder();
        FieldMappingMetaData mappingMetaData = mock(FieldMappingMetaData.class);
        HashMap<Object, Object> parentProperties = new HashMap<>();
        parentProperties.put("type", AUTHOR_TYPE);
        mappingsBuilder.put(BOOK_TYPE, mappingMetaData);
        HashMap<String, Object> parent = new HashMap<String, Object>();
        parent.put(ElasticConstants.PARENT_PROPERTY, parentProperties);
        when(mappingMetaData.sourceAsMap()).thenReturn(parent);
        return mappingsBuilder.build();
    }

    @Test
    public void entitySetToEntityType_NamespaceContains_FQNRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        FullQualifiedName entityType = edmProvider.entitySetToEntityType(
                new FullQualifiedName(AUTHORS_FQN_STRING + MetaDataUtils.NEMESPACE_SEPARATOR
                        + edmProvider.getContainerName().getName()),
                AUTHOR_TYPE);
        assertEquals(AUTHOR_FQN, entityType);
    }

    @Test
    public void entitySetToEntityType_NamespaceDoesntContains_FQNRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        CsdlEntitySet entitySet = mock(CsdlEntitySet.class);
        FullQualifiedName expectedResult = new FullQualifiedName("expected.result");
        when(entitySet.getTypeFQN()).thenReturn(expectedResult);
        CsdlEntityContainer entityContainer = mock(CsdlEntityContainer.class);
        when(entityContainer.getEntitySet(AUTHOR_TYPE)).thenReturn(entitySet);
        doReturn(entityContainer).when(edmProvider).getEntityContainer();
        FullQualifiedName actualResult = edmProvider
                .entitySetToEntityType(edmProvider.getContainerName(), AUTHOR_TYPE);
        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void entitySetToEntityType_NamespaceDoesntContainsAndEntitySetNull_NullRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        CsdlEntityContainer entityContainer = mock(CsdlEntityContainer.class);
        when(entityContainer.getEntitySet(AUTHOR_TYPE)).thenReturn(null);
        doReturn(entityContainer).when(edmProvider).getEntityContainer();
        FullQualifiedName actualResult = edmProvider
                .entitySetToEntityType(edmProvider.getContainerName(), AUTHOR_TYPE);
        assertNull(actualResult);
    }

    @Test(expected = ODataException.class)
    public void entitySetToEntityType_InvalidNamespace_ODataExceptionRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        edmProvider.entitySetToEntityType(new FullQualifiedName("Test.IllegalNamespace"),
                AUTHOR_TYPE);
    }

    @Test
    public void getEntityType_IndexDoesntExist_NullRetrived() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertNull(
                edmProvider.getEntityType(new FullQualifiedName("Test.IllegalNamespace.entity")));
    }

    @Test
    public void getEntityType_IndexExist_EntityTypeRetrived() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        MappingMetaData metadata = mock(MappingMetaData.class);
        when(metaDataProvider.getMappingForType(AUTHORS_INDEX, AUTHOR_TYPE)).thenReturn(metadata);
        doReturn(new ArrayList<CsdlProperty>()).when(edmProvider).getProperties(AUTHOR_FQN,
                metadata);
        doReturn(new ArrayList<CsdlNavigationProperty>()).when(edmProvider)
                .getNavigationProperties(AUTHOR_FQN);
        CsdlEntityType entityType = edmProvider.getEntityType(AUTHOR_FQN);
        assertTrue(entityType instanceof ElasticCsdlEntityType);
        assertEquals(AUTHORS_INDEX, ((ElasticCsdlEntityType) entityType).getEIndex());
        assertEquals(AUTHOR_TYPE, ((ElasticCsdlEntityType) entityType).getEType());
        List<CsdlProperty> properties = entityType.getProperties();
        assertEquals(1, properties.size());
        CsdlProperty idProperty = properties.get(0);
        assertEquals(ElasticConstants.ID_FIELD_NAME, idProperty.getName());
        List<CsdlPropertyRef> keys = entityType.getKey();
        assertEquals(1, keys.size());
        CsdlPropertyRef idRef = keys.get(0);
        assertEquals(ElasticConstants.ID_FIELD_NAME, idRef.getName());
        assertTrue(entityType.getNavigationProperties().isEmpty());
    }

    @Test
    public void getEntitySet_EntityTypeNameNull_NullRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        FullQualifiedName containerName = edmProvider.getContainerName();
        doReturn(null).when(edmProvider).entitySetToEntityType(containerName, AUTHOR_TYPE);
        assertNull(edmProvider.getEntitySet(containerName, AUTHOR_TYPE));
    }

    @Test
    public void getEntitySet_EntityTypeName_EntitySetRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        FullQualifiedName containerName = new FullQualifiedName(AUTHORS_FQN_STRING
                + MetaDataUtils.NEMESPACE_SEPARATOR + edmProvider.getContainerName().getName());
        doReturn(AUTHOR_FQN).when(edmProvider).entitySetToEntityType(containerName, AUTHOR_TYPE);

        List<CsdlNavigationProperty> navigationProperties = new ArrayList<>();
        CsdlNavigationProperty navProperty = new CsdlNavigationProperty();
        String book = "book";
        navProperty.setName(book);
        navProperty.setType(new FullQualifiedName(addNamespace(book)));
        navigationProperties.add(navProperty);
        doReturn(navigationProperties).when(edmProvider).getNavigationProperties(AUTHOR_FQN);

        CsdlEntitySet entitySet = edmProvider.getEntitySet(containerName, AUTHOR_TYPE);
        assertTrue(entitySet instanceof ElasticCsdlEntitySet);
        assertEquals(AUTHOR_TYPE, entitySet.getName());
        assertEquals(AUTHORS_INDEX, ((ElasticCsdlEntitySet) entitySet).getEIndex());
        assertEquals(AUTHOR_TYPE, ((ElasticCsdlEntitySet) entitySet).getEType());
        assertEquals(AUTHOR_FQN, entitySet.getTypeFQN());
        List<CsdlNavigationPropertyBinding> navigationPropertyBindings = entitySet
                .getNavigationPropertyBindings();
        assertEquals(1, navigationProperties.size());
        CsdlNavigationPropertyBinding propertyBinding = navigationPropertyBindings.get(0);
        assertEquals(book, propertyBinding.getPath());
        assertEquals(book, propertyBinding.getTarget());
    }

    @Test
    public void getEntityContainerInfo_ContainerNameNull_EntityContainerRetieved() {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        CsdlEntityContainerInfo entityContainerInfo = edmProvider.getEntityContainerInfo(null);
        assertNotNull(entityContainerInfo);
        assertEquals(edmProvider.getContainerName(), entityContainerInfo.getContainerName());
    }

    @Test
    public void getEntityContainerInfo_ContainerName_NullRetieved() {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        assertNull(edmProvider.getEntityContainerInfo(new FullQualifiedName("Test.ContainerName")));
    }

    @Test
    public void getSchemas_EmptyNamespaces_EmptySchemaListRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, new HashSet<String>());
        assertTrue(edmProvider.getSchemas().isEmpty());
    }

    @Test
    public void getSchemas_Namespaces_SchemaListRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        NestedTypeMapper nestedTypeMapper = mock(NestedTypeMapper.class);
        doReturn(new ArrayList<CsdlEntityType>()).when(edmProvider).getEnityTypes(anyString());
        doReturn(new ArrayList<CsdlComplexType>()).when(nestedTypeMapper)
                .getComplexTypes(anyString());
        doReturn(nestedTypeMapper).when(edmProvider).getNestedTypeMapper();
        doReturn(mock(CsdlEntityContainer.class)).when(edmProvider)
                .getEntityContainerForSchema(anyString());
        List<CsdlSchema> schemas = edmProvider.getSchemas();
        assertEquals(2, schemas.size());
    }

    @Test
    public void getEnityTypes_IndexWithEmptyMappings_EmptyListRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = new MultyElasticIndexCsdlEdmProvider(
                metaDataProvider, indices);
        Builder<String, MappingMetaData> metadataBuilder = ImmutableOpenMap.builder();
        when(metaDataProvider.getAllMappings(WRITERS_INDEX)).thenReturn(metadataBuilder.build());
        assertTrue(edmProvider.getEnityTypes(WRITERS_INDEX).isEmpty());
    }

    @Test
    public void getEnityTypes_IndexWithMappings_ListEntityTypesRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        doReturn(mock(CsdlEntityType.class)).when(edmProvider)
                .getEntityType(any(FullQualifiedName.class));
        Builder<String, MappingMetaData> mappingsBuilder = ImmutableOpenMap.builder();
        mappingsBuilder.put(AUTHOR_TYPE, null);
        mappingsBuilder.put(BOOK_TYPE, null);
        when(metaDataProvider.getAllMappings(WRITERS_INDEX)).thenReturn(mappingsBuilder.build());
        List<CsdlEntityType> enityTypes = edmProvider.getEnityTypes(WRITERS_INDEX);
        assertEquals(2, enityTypes.size());
    }

    @Test
    public void getEntityContainerForSchema_Namespace_EntityContainerWithEntitySetsRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        doReturn(mock(CsdlEntitySet.class)).when(edmProvider)
                .getEntitySet(any(FullQualifiedName.class), anyString());
        Builder<String, MappingMetaData> mappingsBuilder = ImmutableOpenMap.builder();
        mappingsBuilder.put(AUTHOR_TYPE, null);
        mappingsBuilder.put(BOOK_TYPE, null);
        when(metaDataProvider.getAllMappings(AUTHORS_INDEX)).thenReturn(mappingsBuilder.build());
        CsdlEntityContainer entityContainer = edmProvider
                .getEntityContainerForSchema(AUTHORS_FQN_STRING);
        assertEquals(edmProvider.getContainerName().getName(), entityContainer.getName());
        assertEquals(2, entityContainer.getEntitySets().size());
    }

    @Test
    public void getEntityContainerForSchema_NamespaceAndEmptyMetadata_EntityContainerWithEmptyEntitySetsRetrieved()
            throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        doReturn(mock(CsdlEntitySet.class)).when(edmProvider)
                .getEntitySet(any(FullQualifiedName.class), anyString());
        Builder<String, MappingMetaData> mappingsBuilder = ImmutableOpenMap.builder();
        when(metaDataProvider.getAllMappings(AUTHORS_INDEX)).thenReturn(mappingsBuilder.build());
        CsdlEntityContainer entityContainer = edmProvider
                .getEntityContainerForSchema(AUTHORS_FQN_STRING);
        assertEquals(edmProvider.getContainerName().getName(), entityContainer.getName());
        assertTrue(entityContainer.getEntitySets().isEmpty());
    }

    @Test
    public void getEntityContainer_ContainerWithEntitySetsRetrieved() throws ODataException {
        MultyElasticIndexCsdlEdmProvider edmProvider = spy(
                new MultyElasticIndexCsdlEdmProvider(metaDataProvider, indices));
        List<CsdlSchema> schemas = new ArrayList<>();
        CsdlSchema schema1 = mock(CsdlSchema.class);
        CsdlEntityContainer container = mock(CsdlEntityContainer.class);
        List<CsdlEntitySet> entitySets = new ArrayList<>();
        CsdlEntitySet set1 = mock(CsdlEntitySet.class);
        when(set1.isIncludeInServiceDocument()).thenReturn(true);
        entitySets.add(set1);
        entitySets.add(mock(CsdlEntitySet.class));
        when(container.getEntitySets()).thenReturn(entitySets);
        when(schema1.getEntityContainer()).thenReturn(container);
        schemas.add(schema1);
        CsdlSchema schema2 = mock(CsdlSchema.class);
        when(schema2.getEntityContainer()).thenReturn(mock(CsdlEntityContainer.class));
        schemas.add(schema2);
        doReturn(schemas).when(edmProvider).getSchemas();
        CsdlEntityContainer entityContainer = edmProvider.getEntityContainer();
        assertEquals(edmProvider.getContainerName().getName(), entityContainer.getName());
        assertEquals(1, entityContainer.getEntitySets().size());
    }

}