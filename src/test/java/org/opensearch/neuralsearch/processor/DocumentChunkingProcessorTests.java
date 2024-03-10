/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.processor;

import lombok.SneakyThrows;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.junit.Before;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

import org.opensearch.OpenSearchParseException;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.env.TestEnvironment;
import org.opensearch.index.analysis.AnalysisRegistry;
import org.opensearch.index.analysis.TokenizerFactory;
import org.opensearch.index.mapper.IndexFieldMapper;
import org.opensearch.indices.IndicesService;
import org.opensearch.indices.analysis.AnalysisModule;
import org.opensearch.ingest.IngestDocument;
import org.opensearch.ingest.Processor;
import org.opensearch.neuralsearch.processor.chunker.ChunkerFactory;
import org.opensearch.neuralsearch.processor.chunker.DelimiterChunker;
import org.opensearch.neuralsearch.processor.chunker.FixedTokenLengthChunker;
import org.opensearch.neuralsearch.processor.factory.DocumentChunkingProcessorFactory;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.test.OpenSearchTestCase;
import static org.opensearch.neuralsearch.processor.DocumentChunkingProcessor.FIELD_MAP_FIELD;
import static org.opensearch.neuralsearch.processor.DocumentChunkingProcessor.ALGORITHM_FIELD;
import static org.opensearch.neuralsearch.processor.DocumentChunkingProcessor.MAX_CHUNK_LIMIT_FIELD;

public class DocumentChunkingProcessorTests extends OpenSearchTestCase {

    private DocumentChunkingProcessorFactory documentChunkingProcessorFactory;

    private static final String PROCESSOR_TAG = "mockTag";
    private static final String DESCRIPTION = "mockDescription";
    private static final String INPUT_FIELD = "body";
    private static final String INPUT_NESTED_FIELD_KEY = "nested";
    private static final String OUTPUT_FIELD = "body_chunk";
    private static final String INDEX_NAME = "_index";

    @SneakyThrows
    private AnalysisRegistry getAnalysisRegistry() {
        Settings settings = Settings.builder().put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString()).build();
        Environment environment = TestEnvironment.newEnvironment(settings);
        AnalysisPlugin plugin = new AnalysisPlugin() {

            @Override
            public Map<String, AnalysisModule.AnalysisProvider<TokenizerFactory>> getTokenizers() {
                return singletonMap(
                    "keyword",
                    (indexSettings, environment, name, settings) -> TokenizerFactory.newFactory(
                        name,
                        () -> new MockTokenizer(MockTokenizer.KEYWORD, false)
                    )
                );
            }
        };
        return new AnalysisModule(environment, singletonList(plugin)).getAnalysisRegistry();
    }

    @Before
    public void setup() {
        Metadata metadata = mock(Metadata.class);
        Environment environment = mock(Environment.class);
        Settings settings = Settings.builder().put("index.mapping.depth.limit", 20).build();
        when(environment.settings()).thenReturn(settings);
        ClusterState clusterState = mock(ClusterState.class);
        ClusterService clusterService = mock(ClusterService.class);
        IndicesService indicesService = mock(IndicesService.class);
        when(metadata.index(anyString())).thenReturn(null);
        when(clusterState.metadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);
        documentChunkingProcessorFactory = new DocumentChunkingProcessorFactory(
            environment,
            clusterService,
            indicesService,
            getAnalysisRegistry()
        );
    }

    private Map<String, Object> createFixedTokenLengthParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(FixedTokenLengthChunker.TOKEN_LIMIT_FIELD, 10);
        return parameters;
    }

    private List<Map<String, Object>> createSourceDataListNestedMap() {
        Map<String, Object> documents = new HashMap<>();
        documents.put(INPUT_FIELD, createSourceDataString());
        return List.of(documents, documents);
    }

    private Map<String, Object> createFixedTokenLengthParametersWithMaxChunk(int maxChunkNum) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(FixedTokenLengthChunker.TOKEN_LIMIT_FIELD, 10);
        parameters.put(MAX_CHUNK_LIMIT_FIELD, maxChunkNum);
        return parameters;
    }

    private Map<String, Object> createDelimiterParameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(DelimiterChunker.DELIMITER_FIELD, ".");
        return parameters;
    }

    private Map<String, Object> createStringFieldMap() {
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        return fieldMap;
    }

    private Map<String, Object> createNestedFieldMap() {
        Map<String, Object> fieldMap = new HashMap<>();
        fieldMap.put(INPUT_NESTED_FIELD_KEY, Map.of(INPUT_FIELD, OUTPUT_FIELD));
        return fieldMap;
    }

    @SneakyThrows
    private DocumentChunkingProcessor createFixedTokenLengthInstance(Map<String, Object> fieldMap) {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        algorithmMap.put(ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM, createFixedTokenLengthParameters());
        config.put(FIELD_MAP_FIELD, fieldMap);
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        return documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config);
    }

    @SneakyThrows
    private DocumentChunkingProcessor createFixedTokenLengthInstanceWithMaxChunkNum(Map<String, Object> fieldMap, int maxChunkNum) {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        algorithmMap.put(ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM, createFixedTokenLengthParametersWithMaxChunk(maxChunkNum));
        config.put(FIELD_MAP_FIELD, fieldMap);
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        return documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config);
    }

    @SneakyThrows
    private DocumentChunkingProcessor createDelimiterInstance() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        algorithmMap.put(ChunkerFactory.DELIMITER_ALGORITHM, createDelimiterParameters());
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        config.put(FIELD_MAP_FIELD, fieldMap);
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        return documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config);
    }

    public void testCreate_whenAlgorithmFieldMissing_thenFail() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        config.put(FIELD_MAP_FIELD, fieldMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        OpenSearchParseException openSearchParseException = assertThrows(
            OpenSearchParseException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assertEquals("[" + ALGORITHM_FIELD + "] required property is missing", openSearchParseException.getMessage());
    }

    @SneakyThrows
    public void testCreate_whenMaxChunkNumInvalidValue_thenFail() {
        Map<String, Processor.Factory> registry = new HashMap<>();
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        algorithmMap.put(ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM, createFixedTokenLengthParametersWithMaxChunk(-2));
        config.put(FIELD_MAP_FIELD, fieldMap);
        config.put(ALGORITHM_FIELD, algorithmMap);
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assertEquals("Parameter [" + MAX_CHUNK_LIMIT_FIELD + "] must be a positive integer", illegalArgumentException.getMessage());

    }

    public void testCreate_whenAlgorithmFieldNoAlgorithm_thenFail() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        config.put(DocumentChunkingProcessor.FIELD_MAP_FIELD, fieldMap);
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assertEquals(
            "Unable to create the processor as [" + ALGORITHM_FIELD + "] must contain and only contain 1 algorithm",
            illegalArgumentException.getMessage()
        );
    }

    public void testCreate_whenAlgorithmFieldMultipleAlgorithm_thenFail() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        config.put(DocumentChunkingProcessor.FIELD_MAP_FIELD, fieldMap);
        algorithmMap.put(ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM, createFixedTokenLengthParameters());
        algorithmMap.put(ChunkerFactory.DELIMITER_ALGORITHM, createDelimiterParameters());
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assertEquals(
            "Unable to create the processor as [" + ALGORITHM_FIELD + "] must contain and only contain 1 algorithm",
            illegalArgumentException.getMessage()
        );
    }

    public void testCreate_whenAlgorithmFieldInvalidAlgorithmName_thenFail() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        String invalid_algorithm_type = "invalid algorithm";
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        config.put(DocumentChunkingProcessor.FIELD_MAP_FIELD, fieldMap);
        algorithmMap.put(invalid_algorithm_type, createFixedTokenLengthParameters());
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assert (illegalArgumentException.getMessage()
            .contains("Unable to create the processor as chunker algorithm [" + invalid_algorithm_type + "] is not supported"));
    }

    public void testCreate_whenAlgorithmFieldInvalidAlgorithmContent_thenFail() {
        Map<String, Object> config = new HashMap<>();
        Map<String, Object> fieldMap = new HashMap<>();
        Map<String, Object> algorithmMap = new HashMap<>();
        fieldMap.put(INPUT_FIELD, OUTPUT_FIELD);
        config.put(DocumentChunkingProcessor.FIELD_MAP_FIELD, fieldMap);
        algorithmMap.put(ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM, 1);
        config.put(ALGORITHM_FIELD, algorithmMap);
        Map<String, Processor.Factory> registry = new HashMap<>();
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> documentChunkingProcessorFactory.create(registry, PROCESSOR_TAG, DESCRIPTION, config)
        );
        assertEquals(
            "Unable to create the processor as ["
                + ChunkerFactory.FIXED_TOKEN_LENGTH_ALGORITHM
                + "] parameters cannot be cast to ["
                + Map.class.getName()
                + "]",
            illegalArgumentException.getMessage()
        );
    }

    @SneakyThrows
    public void testGetType() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        String type = processor.getType();
        assertEquals(DocumentChunkingProcessor.TYPE, type);
    }

    private String createSourceDataString() {
        return "This is an example document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch.";
    }

    private List<String> createSourceDataListStrings() {
        List<String> documents = new ArrayList<>();
        documents.add(
            "This is the first document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch."
        );
        documents.add(
            "This is the second document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch."
        );
        return documents;
    }

    private List<Object> createSourceDataListHybridType() {
        List<Object> documents = new ArrayList<>();
        documents.add(
            "This is the first document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch."
        );
        documents.add(1);
        return documents;
    }

    private List<Object> createSourceDataListWithNull() {
        List<Object> documents = new ArrayList<>();
        documents.add(
            "This is the first document to be chunked. The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch."
        );
        documents.add(null);
        return documents;
    }

    private Map<String, Object> createSourceDataNestedMap() {
        Map<String, Object> documents = new HashMap<>();
        documents.put(INPUT_FIELD, createSourceDataString());
        return documents;
    }

    private Map<String, Object> createSourceDataInvalidNestedMap() {
        Map<String, Object> documents = new HashMap<>();
        documents.put(INPUT_FIELD, Map.of(INPUT_NESTED_FIELD_KEY, 1));
        return documents;
    }

    private Map<String, Object> createMaxDepthLimitExceedMap(int maxDepth) {
        if (maxDepth > 21) {
            return null;
        }
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> innerMap = createMaxDepthLimitExceedMap(maxDepth + 1);
        if (innerMap != null) {
            resultMap.put(INPUT_FIELD, innerMap);
        }
        return resultMap;
    }

    private IngestDocument createIngestDocumentWithNestedSourceData(Object sourceData) {
        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put(INPUT_NESTED_FIELD_KEY, sourceData);
        sourceAndMetadata.put(IndexFieldMapper.NAME, INDEX_NAME);
        return new IngestDocument(sourceAndMetadata, new HashMap<>());
    }

    private IngestDocument createIngestDocumentWithSourceData(Object sourceData) {
        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put(INPUT_FIELD, sourceData);
        sourceAndMetadata.put(IndexFieldMapper.NAME, INDEX_NAME);
        return new IngestDocument(sourceAndMetadata, new HashMap<>());
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataStringWithMaxChunkNum_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstanceWithMaxChunkNum(createStringFieldMap(), 5);
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataString());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(OUTPUT_FIELD);
        Object passages = document.getSourceAndMetadata().get(OUTPUT_FIELD);
        assert (passages instanceof List<?>);
        List<String> expectedPassages = new ArrayList<>();
        expectedPassages.add("This is an example document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        assertEquals(expectedPassages, passages);
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataStringWithMaxChunkNumTwice_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstanceWithMaxChunkNum(createStringFieldMap(), 5);
        for (int i = 0; i < 2; i++) {
            IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataString());
            IngestDocument document = processor.execute(ingestDocument);
            assert document.getSourceAndMetadata().containsKey(OUTPUT_FIELD);
            Object passages = document.getSourceAndMetadata().get(OUTPUT_FIELD);
            assert (passages instanceof List<?>);
            List<String> expectedPassages = new ArrayList<>();
            expectedPassages.add("This is an example document to be chunked. The document");
            expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
            expectedPassages.add("standard tokenizer in OpenSearch");
            assertEquals(expectedPassages, passages);
        }
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataStringWithMaxChunkNumExceed_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstanceWithMaxChunkNum(createStringFieldMap(), 1);
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataString());
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals(
            illegalArgumentException.getMessage(),
            "Unable to create the processor as the number of chunks [" + "3" + "] exceeds the maximum chunk limit [" + "1" + "]"
        );

    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataString_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataString());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(OUTPUT_FIELD);
        Object passages = document.getSourceAndMetadata().get(OUTPUT_FIELD);
        assert (passages instanceof List<?>);
        List<String> expectedPassages = new ArrayList<>();
        expectedPassages.add("This is an example document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        assertEquals(expectedPassages, passages);
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataInvalidType_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        Map<String, Object> sourceAndMetadata = new HashMap<>();
        sourceAndMetadata.put(INPUT_FIELD, 1);
        sourceAndMetadata.put(IndexFieldMapper.NAME, INDEX_NAME);
        IngestDocument ingestDocument = new IngestDocument(sourceAndMetadata, new HashMap<>());
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals(
            "field [" + INPUT_FIELD + "] is neither string nor nested type, cannot process it",
            illegalArgumentException.getMessage()
        );
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataListStrings_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataListStrings());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(OUTPUT_FIELD);
        Object passages = document.getSourceAndMetadata().get(OUTPUT_FIELD);
        assert (passages instanceof List<?>);

        List<String> expectedPassages = new ArrayList<>();
        expectedPassages.add("This is the first document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        expectedPassages.add("This is the second document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        assertEquals(expectedPassages, passages);
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataListHybridType_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataListHybridType());
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals(
            "list type field [" + INPUT_FIELD + "] has non string value, cannot process it",
            illegalArgumentException.getMessage()
        );
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andSourceDataListWithNull_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createStringFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataListWithNull());
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals("list type field [" + INPUT_FIELD + "] has null, cannot process it", illegalArgumentException.getMessage());
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public void testExecute_withFixedTokenLength_andFieldMapNestedMap_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createNestedFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithNestedSourceData(createSourceDataNestedMap());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(INPUT_NESTED_FIELD_KEY);
        Object nestedResult = document.getSourceAndMetadata().get(INPUT_NESTED_FIELD_KEY);
        assert (nestedResult instanceof Map<?, ?>);
        assert ((Map<String, Object>) nestedResult).containsKey(OUTPUT_FIELD);
        Object passages = ((Map<String, Object>) nestedResult).get(OUTPUT_FIELD);
        assert (passages instanceof List);

        List<String> expectedPassages = new ArrayList<>();
        expectedPassages.add("This is an example document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        assertEquals(expectedPassages, passages);
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andMaxDepthLimitExceedFieldMap_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createNestedFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithNestedSourceData(createMaxDepthLimitExceedMap(0));
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals(
            "map type field [" + INPUT_NESTED_FIELD_KEY + "] reached max depth limit, cannot process it",
            illegalArgumentException.getMessage()
        );
    }

    @SneakyThrows
    public void testExecute_withFixedTokenLength_andFieldMapNestedMap_thenFail() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createNestedFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithNestedSourceData(createSourceDataInvalidNestedMap());
        IllegalArgumentException illegalArgumentException = assertThrows(
            IllegalArgumentException.class,
            () -> processor.execute(ingestDocument)
        );
        assertEquals(
            "map type field [" + INPUT_NESTED_FIELD_KEY + "] has non-string type, cannot process it",
            illegalArgumentException.getMessage()
        );
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    public void testExecute_withFixedTokenLength_andFieldMapNestedMap_sourceList_thenSucceed() {
        DocumentChunkingProcessor processor = createFixedTokenLengthInstance(createNestedFieldMap());
        IngestDocument ingestDocument = createIngestDocumentWithNestedSourceData(createSourceDataListNestedMap());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(INPUT_NESTED_FIELD_KEY);
        Object nestedResult = document.getSourceAndMetadata().get(INPUT_NESTED_FIELD_KEY);
        List<String> expectedPassages = new ArrayList<>();

        expectedPassages.add("This is an example document to be chunked. The document");
        expectedPassages.add("contains a single paragraph, two sentences and 24 tokens by");
        expectedPassages.add("standard tokenizer in OpenSearch");
        assert (nestedResult instanceof List);
        assertEquals(((List<?>) nestedResult).size(), 2);
        for (Object result : (List<Object>) nestedResult) {
            assert (result instanceof Map);
            assert ((Map<String, Object>) result).containsKey(OUTPUT_FIELD);
            Object passages = ((Map<?, ?>) result).get(OUTPUT_FIELD);
            assert (passages instanceof List);
            assertEquals(expectedPassages, passages);
        }
    }

    @SneakyThrows
    public void testExecute_withDelimiter_andSourceDataString_thenSucceed() {
        DocumentChunkingProcessor processor = createDelimiterInstance();
        IngestDocument ingestDocument = createIngestDocumentWithSourceData(createSourceDataString());
        IngestDocument document = processor.execute(ingestDocument);
        assert document.getSourceAndMetadata().containsKey(OUTPUT_FIELD);
        Object passages = document.getSourceAndMetadata().get(OUTPUT_FIELD);
        assert (passages instanceof List<?>);
        List<String> expectedPassages = new ArrayList<>();
        expectedPassages.add("This is an example document to be chunked.");
        expectedPassages.add(" The document contains a single paragraph, two sentences and 24 tokens by standard tokenizer in OpenSearch.");
        assertEquals(expectedPassages, passages);
    }
}
