/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.neuralsearch.processor.chunker;

import java.io.IOException;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

import lombok.extern.log4j.Log4j2;
import org.opensearch.action.admin.indices.analyze.AnalyzeAction;
import org.opensearch.index.analysis.AnalysisRegistry;
import static org.opensearch.action.admin.indices.analyze.TransportAnalyzeAction.analyze;

@Log4j2
public class FixedTokenLengthChunker implements FieldChunker {

    public static final String TOKEN_LIMIT_FIELD = "token_limit";
    public static final String OVERLAP_RATE_FIELD = "overlap_rate";
    public static final String MAX_TOKEN_COUNT_FIELD = "max_token_count";
    public static String MAX_CHUNK_LIMIT_FIELD = "max_chunk_limit";
    public static final String TOKENIZER_FIELD = "tokenizer";

    // default values for each parameter
    private static final int DEFAULT_TOKEN_LIMIT = 500;
    private static final double DEFAULT_OVERLAP_RATE = 0.2;
    private static final int DEFAULT_MAX_TOKEN_COUNT = 10000;
    private static final int DEFAULT_MAX_CHUNK_LIMIT = 100;
    private static final String DEFAULT_TOKENIZER = "standard";

    private final AnalysisRegistry analysisRegistry;

    public FixedTokenLengthChunker(AnalysisRegistry analysisRegistry) {
        this.analysisRegistry = analysisRegistry;
    }

    private List<String> tokenize(String content, String tokenizer, int maxTokenCount) {
        AnalyzeAction.Request analyzeRequest = new AnalyzeAction.Request();
        analyzeRequest.text(content);
        analyzeRequest.tokenizer(tokenizer);
        try {
            AnalyzeAction.Response analyzeResponse = analyze(analyzeRequest, analysisRegistry, null, maxTokenCount);
            List<AnalyzeAction.AnalyzeToken> analyzeTokenList = analyzeResponse.getTokens();
            List<String> tokenList = new ArrayList<>();
            for (AnalyzeAction.AnalyzeToken analyzeToken : analyzeTokenList) {
                tokenList.add(analyzeToken.getTerm());
            }
            return tokenList;
        } catch (IOException e) {
            throw new RuntimeException("Fixed token length algorithm meet with exception: " + e);
        }
    };

    @Override
    public List<String> chunk(String content, Map<String, Object> parameters) {
        // prior to chunking, parameters have been validated
        int tokenLimit = DEFAULT_TOKEN_LIMIT;
        BigDecimal overlap_rate = new BigDecimal(String.valueOf(DEFAULT_OVERLAP_RATE));
        int maxTokenCount = DEFAULT_MAX_TOKEN_COUNT;
        int maxChunkLimit = DEFAULT_MAX_CHUNK_LIMIT;

        String tokenizer = DEFAULT_TOKENIZER;

        if (parameters.containsKey(TOKEN_LIMIT_FIELD)) {
            tokenLimit = ((Number) parameters.get(TOKEN_LIMIT_FIELD)).intValue();
        }
        if (parameters.containsKey(OVERLAP_RATE_FIELD)) {
            overlap_rate = new BigDecimal(String.valueOf(parameters.get(OVERLAP_RATE_FIELD)));
        }
        if (parameters.containsKey(MAX_TOKEN_COUNT_FIELD)) {
            maxTokenCount = ((Number) parameters.get(MAX_TOKEN_COUNT_FIELD)).intValue();
        }
        if (parameters.containsKey(TOKENIZER_FIELD)) {
            tokenizer = (String) parameters.get(TOKENIZER_FIELD);
        }
        if (parameters.containsKey(MAX_CHUNK_LIMIT_FIELD)) {
            maxChunkLimit = ((Number) parameters.get(MAX_CHUNK_LIMIT_FIELD)).intValue();
        }

        List<String> tokens = tokenize(content, tokenizer, maxTokenCount);
        List<String> passages = new ArrayList<>();

        String passage;
        int startToken = 0;
        BigDecimal overlapTokenNumberBigDecimal = overlap_rate.multiply(new BigDecimal(String.valueOf(tokenLimit))).setScale(0, RoundingMode.DOWN);
        int overlapTokenNumber = overlapTokenNumberBigDecimal.intValue();;
        // overlapTokenNumber must be smaller than the token limit
        overlapTokenNumber = Math.min(overlapTokenNumber, tokenLimit - 1);

        while (startToken < tokens.size()) {
            if (startToken + tokenLimit >= tokens.size()) {
                // break the loop when already cover the last token
                passage = String.join(" ", tokens.subList(startToken, tokens.size()));
                addPassageToList(passages, passage, maxChunkLimit);
                break;
            } else {
                passage = String.join(" ", tokens.subList(startToken, startToken + tokenLimit));
                addPassageToList(passages, passage, maxChunkLimit);
            }
            startToken += tokenLimit - overlapTokenNumber;
        }
        return passages;
    }

    private void addPassageToList(List<String> passages, String passage, int maxChunkLimit) {
        if (passages.size() >= maxChunkLimit) {
            throw new IllegalStateException("Exceed max chunk number in fixed token length algorithm");
        }
        passages.add(passage);
    }

    private void validatePositiveIntegerParameter(Map<String, Object> parameters, String fieldName, boolean requirePositive) {
        // this method validate that parameter is a positive integer
        if (!parameters.containsKey(fieldName)) {
            // all parameters are optional
            return;
        }
        if (!(parameters.get(fieldName) instanceof Number)) {
            throw new IllegalArgumentException(
                "fixed length parameter [" + fieldName + "] cannot be cast to [" + Number.class.getName() + "]"
            );
        }
        if (requirePositive) {
            if (((Number) parameters.get(fieldName)).intValue() <= 0) {
                throw new IllegalArgumentException("fixed length parameter [" + fieldName + "] must be positive");
            }
        } else {
            if (((Number) parameters.get(fieldName)).intValue() < 0) {
                throw new IllegalArgumentException("fixed length parameter [" + fieldName + "] cannot be negative");
            }
        }
    }

    @Override
    public void validateParameters(Map<String, Object> parameters) {
        validatePositiveIntegerParameter(parameters, TOKEN_LIMIT_FIELD, true);
        validatePositiveIntegerParameter(parameters, MAX_CHUNK_LIMIT_FIELD, false);
        validatePositiveIntegerParameter(parameters, MAX_TOKEN_COUNT_FIELD, true);

        if (parameters.containsKey(OVERLAP_RATE_FIELD)) {
            if (!(parameters.get(OVERLAP_RATE_FIELD) instanceof Number)) {
                throw new IllegalArgumentException(
                    "fixed length parameter [" + OVERLAP_RATE_FIELD + "] cannot be cast to [" + Number.class.getName() + "]"
                );
            }
            BigDecimal overlap_rate = new BigDecimal(String.valueOf(parameters.get(OVERLAP_RATE_FIELD)));
            if (overlap_rate.compareTo(BigDecimal.ZERO) < 0 || overlap_rate.compareTo(BigDecimal.ONE) >= 0) {
                throw new IllegalArgumentException(
                    "fixed length parameter [" + OVERLAP_RATE_FIELD + "] must be between 0 and 1, 1 is not included."
                );
            }
        }

        if (parameters.containsKey(TOKENIZER_FIELD) && !(parameters.get(TOKENIZER_FIELD) instanceof String)) {
            throw new IllegalArgumentException(
                "fixed length parameter [" + TOKENIZER_FIELD + "] cannot be cast to [" + String.class.getName() + "]"
            );
        }
    }
}
