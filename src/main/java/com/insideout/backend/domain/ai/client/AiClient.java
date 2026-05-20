package com.insideout.backend.domain.ai.client;

import com.insideout.backend.domain.ai.dto.client.AiAnalyzeRequest;
import com.insideout.backend.domain.ai.dto.client.AiAnalyzeResponse;
import com.insideout.backend.domain.ai.exception.AiErrorCode;
import com.insideout.backend.domain.ai.exception.AiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    private static final String ANALYZE_URI = "/api/v1/analyze";
    private static final int MAX_ERROR_BODY_PREVIEW_LENGTH = 120;

    private final WebClient aiWebClient;

    public AiAnalyzeResponse analyze(UUID floorplanId, String imageUrl, String modelVersion) {
        AiAnalyzeRequest request = AiAnalyzeRequest.of(floorplanId, imageUrl, modelVersion);

        try {
            AiAnalyzeResponse response = aiWebClient.post()
                    .uri(ANALYZE_URI)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(AiAnalyzeResponse.class)
                    .block();

            if (response == null) {
                throw new AiException(AiErrorCode.AI_EMPTY_RESPONSE);
            }

            log.info("AI analysis response received: floorplanId={}, detections={}",
                    floorplanId,
                    response.detections() != null ? response.detections().size() : 0);
            return response;
        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("AI server returned an error: floorplanId={}, status={}, bodyLength={}, bodyPreview={}",
                    floorplanId,
                    e.getStatusCode(),
                    responseBody != null ? responseBody.length() : 0,
                    toSafeBodyPreview(responseBody));
            throw new AiException(AiErrorCode.AI_SERVER_ERROR);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call AI server: floorplanId={}", floorplanId, e);
            throw new AiException(AiErrorCode.AI_SERVER_UNREACHABLE);
        }
    }

    private String toSafeBodyPreview(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "<empty>";
        }

        String normalized = responseBody.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= MAX_ERROR_BODY_PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_ERROR_BODY_PREVIEW_LENGTH) + "...";
    }
}
