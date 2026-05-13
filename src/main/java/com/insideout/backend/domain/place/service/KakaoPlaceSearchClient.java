package com.insideout.backend.domain.place.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class KakaoPlaceSearchClient {

    private static final int DEFAULT_SIZE = 10;
    private final @Qualifier("kakaoLocalRestClient") RestClient kakaoLocalRestClient;

    public List<PlaceSearchItemResponse> searchByKeyword(String query) {
        try {
            KakaoKeywordSearchResponse response = kakaoLocalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/search/keyword.json")
                            .queryParam("query", query)
                            .queryParam("size", DEFAULT_SIZE)
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KakaoKeywordSearchResponse.class);

            if (response == null || response.documents() == null) {
                return Collections.emptyList();
            }

            return response.documents().stream()
                    .map(document -> new PlaceSearchItemResponse(
                            document.placeName(),
                            document.addressName(),
                            document.roadAddressName(),
                            parseDouble(document.y()),
                            parseDouble(document.x()),
                            false,
                            document.id()
                    ))
                    .toList();
        } catch (RestClientException e) {
            throw new ProjectException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record KakaoKeywordSearchResponse(
            List<KakaoKeywordSearchDocument> documents
    ) {
    }

    private record KakaoKeywordSearchDocument(
            String id,
            @JsonProperty("place_name")
            String placeName,
            @JsonProperty("address_name")
            String addressName,
            @JsonProperty("road_address_name")
            String roadAddressName,
            String x,
            String y
    ) {
    }
}
