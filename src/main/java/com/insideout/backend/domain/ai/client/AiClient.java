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
            log.error("AI server returned an error: status={}, body={}",
                    e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new AiException(AiErrorCode.AI_SERVER_ERROR);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to call AI server: floorplanId={}", floorplanId, e);
            throw new AiException(AiErrorCode.AI_SERVER_UNREACHABLE);
        }
    }
}
