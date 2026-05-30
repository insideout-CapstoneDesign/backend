package com.insideout.backend.domain.place.service.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.insideout.backend.domain.place.dto.response.PlaceNearestResponse;
import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.domain.place.exception.PlaceErrorCode;
import com.insideout.backend.domain.place.exception.PlaceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class KakaoPlaceSearchClient {

    private static final int DEFAULT_SIZE = 15;
    private static final int MAX_PAGE = 45;
    private final @Qualifier("kakaoLocalRestClient") RestClient kakaoLocalRestClient;

    public List<PlaceSearchItemResponse> searchByKeyword(String query) {
        return searchByKeyword(query, null, null, null);
    }

    public List<PlaceSearchItemResponse> searchByKeyword(String query, Double lat, Double lng) {
        return searchByKeyword(query, lat, lng, null);
    }

    public List<PlaceSearchItemResponse> searchByKeyword(String query, Double lat, Double lng, Integer radius) {
        try {
            List<KakaoKeywordSearchDocument> documents = fetchAllKeywordSearchDocuments(query, lat, lng, radius);
            if (documents.isEmpty()) {
                return Collections.emptyList();
            }

            return documents.stream()
                    .map(document -> new PlaceSearchItemResponse(
                            document.placeName(),
                            document.addressName(),
                            document.roadAddressName(),
                            parseDouble(document.y()),
                            parseDouble(document.x()),
                            false,
                            document.id(),
                            null
                    ))
                    .toList();
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.KAKAO_LOCAL_API_UNAVAILABLE);
        }
    }

    private List<KakaoKeywordSearchDocument> fetchAllKeywordSearchDocuments(
            String query,
            Double lat,
            Double lng,
            Integer radius
    ) {
        List<KakaoKeywordSearchDocument> allDocuments = new java.util.ArrayList<>();
        int page = 1;

        while (page <= MAX_PAGE) {
            KakaoKeywordSearchResponse response = requestKeywordSearchPage(query, lat, lng, radius, page);
            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                break;
            }

            allDocuments.addAll(response.documents());

            KakaoKeywordSearchMeta meta = response.meta();
            if (meta == null) {
                break;
            }

            if (Boolean.TRUE.equals(meta.isEnd())) {
                break;
            }

            Integer pageableCount = meta.pageableCount();
            if (pageableCount != null && pageableCount > 0) {
                int maxPageByCount = Math.min(MAX_PAGE, (int) Math.ceil((double) pageableCount / DEFAULT_SIZE));
                if (page >= maxPageByCount) {
                    break;
                }
            }

            page++;
        }

        return allDocuments;
    }

    private KakaoKeywordSearchResponse requestKeywordSearchPage(
            String query,
            Double lat,
            Double lng,
            Integer radius,
            int page
    ) {
        return kakaoLocalRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam("query", query)
                        .queryParam("size", DEFAULT_SIZE)
                        .queryParam("page", page)
                        .queryParamIfPresent("x", Optional.ofNullable(lng))
                        .queryParamIfPresent("y", Optional.ofNullable(lat))
                        .queryParamIfPresent("radius", Optional.ofNullable(radius))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KakaoKeywordSearchResponse.class);
    }

    public Optional<PlaceNearestResponse> findNearestByCoordinate(double lat, double lng, int radius) {
        try {
            KakaoCoordToAddressResponse response = kakaoLocalRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v2/local/geo/coord2address.json")
                            .queryParam("x", lng)
                            .queryParam("y", lat)
                            .queryParam("input_coord", "WGS84")
                            .build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(KakaoCoordToAddressResponse.class);

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                return Optional.empty();
            }

            KakaoCoordToAddressDocument document = response.documents().get(0);
            String address = resolveAddress(document);
            String roadAddress = resolveRoadAddress(document);
            String name = resolveName(document, address);
            Double resolvedLat = resolveCoordinate(
                    document.roadAddress() != null ? document.roadAddress().y() : null,
                    document.address() != null ? document.address().y() : null,
                    lat
            );
            Double resolvedLng = resolveCoordinate(
                    document.roadAddress() != null ? document.roadAddress().x() : null,
                    document.address() != null ? document.address().x() : null,
                    lng
            );

            if (resolvedLat == null || resolvedLng == null) {
                return Optional.empty();
            }

            if (distanceInMeter(lat, lng, resolvedLat, resolvedLng) > radius) {
                return Optional.empty();
            }

            if (!StringUtils.hasText(name) || !StringUtils.hasText(address)) {
                return Optional.empty();
            }

            return Optional.of(new PlaceNearestResponse(
                    name,
                    address,
                    roadAddress,
                    resolvedLat,
                    resolvedLng,
                    false,
                    null
            ));
        } catch (RestClientException e) {
            throw new PlaceException(PlaceErrorCode.KAKAO_LOCAL_API_UNAVAILABLE);
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private String resolveAddress(KakaoCoordToAddressDocument document) {
        if (document.roadAddress() != null && StringUtils.hasText(document.roadAddress().addressName())) {
            return document.roadAddress().addressName();
        }
        if (document.address() != null && StringUtils.hasText(document.address().addressName())) {
            return document.address().addressName();
        }
        return null;
    }

    private String resolveRoadAddress(KakaoCoordToAddressDocument document) {
        if (document.roadAddress() != null && StringUtils.hasText(document.roadAddress().addressName())) {
            return document.roadAddress().addressName();
        }
        return null;
    }

    private String resolveName(KakaoCoordToAddressDocument document, String fallbackAddress) {
        if (document.roadAddress() != null && StringUtils.hasText(document.roadAddress().buildingName())) {
            return document.roadAddress().buildingName();
        }
        if (document.address() != null && StringUtils.hasText(document.address().buildingName())) {
            return document.address().buildingName();
        }
        return fallbackAddress;
    }

    private Double resolveCoordinate(String firstCandidate, String secondCandidate, double fallbackValue) {
        Double first = parseDouble(firstCandidate);
        if (first != null) {
            return first;
        }
        Double second = parseDouble(secondCandidate);
        if (second != null) {
            return second;
        }
        return fallbackValue;
    }

    private double distanceInMeter(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private record KakaoKeywordSearchResponse(
            KakaoKeywordSearchMeta meta,
            List<KakaoKeywordSearchDocument> documents
    ) {
    }

    private record KakaoKeywordSearchMeta(
            @JsonProperty("is_end")
            Boolean isEnd,
            @JsonProperty("pageable_count")
            Integer pageableCount
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

    private record KakaoCoordToAddressResponse(
            List<KakaoCoordToAddressDocument> documents
    ) {
    }

    private record KakaoCoordToAddressDocument(
            KakaoAddress address,
            @JsonProperty("road_address")
            KakaoRoadAddress roadAddress
    ) {
    }

    private record KakaoAddress(
            @JsonProperty("address_name")
            String addressName,
            @JsonProperty("building_name")
            String buildingName,
            String x,
            String y
    ) {
    }

    private record KakaoRoadAddress(
            @JsonProperty("address_name")
            String addressName,
            @JsonProperty("building_name")
            String buildingName,
            String x,
            String y
    ) {
    }
}
