/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.processor.chunker;

import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.math.NumberUtils;

import org.opensearch.index.analysis.AnalysisRegistry;
import org.opensearch.action.admin.indices.analyze.AnalyzeAction;
import static org.opensearch.action.admin.indices.analyze.TransportAnalyzeAction.analyze;
import static org.opensearch.neuralsearch.processor.chunker.ChunkerUtils.validatePositiveIntegerParameter;
import static org.opensearch.neuralsearch.processor.chunker.ChunkerUtils.validateStringParameters;

/**
 * The implementation {@link Chunker} for fixed token length algorithm.
 */
@Log4j2
public class FixedTokenLengthChunker implements Chunker {

    public static final String ANALYSIS_REGISTRY_FIELD = "analysis_registry";
    public static final String TOKEN_LIMIT_FIELD = "token_limit";
    public static final String OVERLAP_RATE_FIELD = "overlap_rate";
    public static final String MAX_TOKEN_COUNT_FIELD = "max_token_count";
    public static final String TOKENIZER_FIELD = "tokenizer";

    public static final String TOKEN_CONCATENATOR_FIELD = "token_concatenator";

    // default values for each parameter
    private static final int DEFAULT_TOKEN_LIMIT = 384;
    private static final double DEFAULT_OVERLAP_RATE = 0.0;
    private static final int DEFAULT_MAX_TOKEN_COUNT = 10000;
    private static final String DEFAULT_TOKENIZER = "standard";

    private static final String DEFAULT_TOKEN_CONCATENATOR = " ";

    private static final double OVERLAP_RATE_UPPER_BOUND = 0.5;

    private double overlapRate;

    private int tokenLimit;

    private String tokenConcatenator;

    private String tokenizer;

    private final AnalysisRegistry analysisRegistry;

    public FixedTokenLengthChunker(Map<String, Object> parameters) {
        validateParameters(parameters);
        this.analysisRegistry = (AnalysisRegistry) parameters.get(ANALYSIS_REGISTRY_FIELD);
    }

    /**
     * Validate and parse the parameters for fixed token length algorithm,
     * will throw IllegalArgumentException when parameters are invalid
     *
     * @param parameters a map containing parameters, containing the following parameters:
     * 1. tokenizer: the <a href="https://opensearch.org/docs/latest/analyzers/tokenizers/index/">analyzer tokenizer</a> in opensearch
     * 2. token_limit: the token limit for each chunked passage
     * 3. overlap_rate: the overlapping degree for each chunked passage, indicating how many token comes from the previous passage
     * 4. max_token_count: the max token limit for the tokenizer
     * Here are requirements for parameters:
     * max_token_count and token_limit should be a positive integer
     * overlap_rate should be within range [0, 0.5]
     * tokenizer should be string
     */
    @Override
    public void validateParameters(Map<String, Object> parameters) {
        this.tokenLimit = validatePositiveIntegerParameter(parameters, TOKEN_LIMIT_FIELD, DEFAULT_TOKEN_LIMIT);
        if (parameters.containsKey(OVERLAP_RATE_FIELD)) {
            String overlapRateString = parameters.get(OVERLAP_RATE_FIELD).toString();
            if (!(NumberUtils.isParsable(overlapRateString))) {
                throw new IllegalArgumentException(
                    "fixed length parameter [" + OVERLAP_RATE_FIELD + "] cannot be cast to [" + Number.class.getName() + "]"
                );
            }
            double overlapRate = NumberUtils.createDouble(overlapRateString);
            if (overlapRate < 0 || overlapRate > OVERLAP_RATE_UPPER_BOUND) {
                throw new IllegalArgumentException(
                    "fixed length parameter [" + OVERLAP_RATE_FIELD + "] must be between 0 and " + OVERLAP_RATE_UPPER_BOUND
                );
            }
            this.overlapRate = overlapRate;
        } else {
            this.overlapRate = DEFAULT_OVERLAP_RATE;
        }
        this.tokenizer = validateStringParameters(parameters, TOKENIZER_FIELD, DEFAULT_TOKENIZER, false);
        this.tokenConcatenator = validateStringParameters(parameters, TOKEN_CONCATENATOR_FIELD, DEFAULT_TOKEN_CONCATENATOR, true);
    }

    /**
     * Return the chunked passages for fixed token length algorithm
     *
     * @param content input string
     * @param runtimeParameters a map for runtime parameters, containing the following runtime parameters:
     * max_token_count the max token limit for the tokenizer
     */
    @Override
    public List<String> chunk(String content, Map<String, Object> runtimeParameters) {
        // prior to chunking, runtimeParameters have been validated
        int maxTokenCount = validatePositiveIntegerParameter(runtimeParameters, MAX_TOKEN_COUNT_FIELD, DEFAULT_MAX_TOKEN_COUNT);

        List<String> tokens = tokenize(content, tokenizer, maxTokenCount);
        List<String> passages = new ArrayList<>();

        double overlapTokenNumberDouble = overlapRate * tokenLimit;
        int overlapTokenNumber = (int) Math.round(overlapTokenNumberDouble);

        int startTokenIndex = 0;
        while (startTokenIndex < tokens.size()) {
            if (startTokenIndex + tokenLimit >= tokens.size()) {
                // break the loop when already cover the last token
                passages.add(String.join(tokenConcatenator, tokens.subList(startTokenIndex, tokens.size())));
                break;
            } else {
                passages.add(String.join(tokenConcatenator, tokens.subList(startTokenIndex, startTokenIndex + tokenLimit)));
            }
            startTokenIndex += tokenLimit - overlapTokenNumber;
        }
        return passages;
    }

    private List<String> tokenize(String content, String tokenizer, int maxTokenCount) {
        AnalyzeAction.Request analyzeRequest = new AnalyzeAction.Request();
        analyzeRequest.text(content);
        analyzeRequest.tokenizer(tokenizer);
        try {
            AnalyzeAction.Response analyzeResponse = analyze(analyzeRequest, analysisRegistry, null, maxTokenCount);
            List<String> tokenList = new ArrayList<>();
            List<AnalyzeAction.AnalyzeToken> analyzeTokenList = analyzeResponse.getTokens();
            for (AnalyzeAction.AnalyzeToken analyzeToken : analyzeTokenList) {
                tokenList.add(analyzeToken.getTerm());
            }
            return tokenList;
        } catch (IOException e) {
            throw new RuntimeException("Fixed token length algorithm meet with exception: " + e);
        }
    }
}
