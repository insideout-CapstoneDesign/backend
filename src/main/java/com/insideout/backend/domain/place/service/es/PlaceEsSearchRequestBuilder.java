package com.insideout.backend.domain.place.service.es;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlaceEsSearchRequestBuilder {

    private PlaceEsSearchRequestBuilder() {
    }

    static Map<String, Object> build(
            String query,
            int size,
            Double lat,
            Double lng,
            Integer radius,
            boolean includeSuggester
    ) {
        Map<String, Object> termNameKeyword = Map.of(
                "term", Map.of(
                        "name.keyword", Map.of(
                                "value", query,
                                "boost", 20
                        )
                )
        );
        Map<String, Object> matchPhraseName = Map.of(
                "match_phrase", Map.of(
                        "name", Map.of(
                                "query", query,
                                "boost", 10
                        )
                )
        );
        Map<String, Object> matchName = Map.of(
                "match", Map.of(
                        "name", Map.of(
                                "query", query,
                                "operator", "and",
                                "boost", 5
                        )
                )
        );
        Map<String, Object> fuzzyName = Map.of(
                "match", Map.of(
                        "name", Map.of(
                                "query", query,
                                "fuzziness", "AUTO",
                                "boost", 2
                        )
                )
        );
        Map<String, Object> matchAddress = Map.of(
                "match", Map.of(
                        "address", Map.of(
                                "query", query,
                                "boost", 1
                        )
                )
        );
        Map<String, Object> matchRoadAddress = Map.of(
                "match", Map.of(
                        "roadAddress", Map.of(
                                "query", query,
                                "boost", 1
                        )
                )
        );

        List<Object> should = List.of(
                termNameKeyword,
                matchPhraseName,
                matchName,
                fuzzyName,
                matchAddress,
                matchRoadAddress
        );

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("should", should);
        bool.put("minimum_should_match", 1);
        if (lat != null && lng != null && radius != null) {
            bool.put("filter", List.of(
                    Map.of("geo_distance", Map.of(
                            "distance", radius + "m",
                            "location", Map.of("lat", lat, "lon", lng)
                    ))
            ));
        }

        List<Object> sort = new ArrayList<>();
        sort.add(Map.of("_score", Map.of("order", "desc")));
        if (lat != null && lng != null) {
            sort.add(Map.of(
                    "_geo_distance", Map.of(
                            "location", Map.of("lat", lat, "lon", lng),
                            "order", "asc",
                            "unit", "m"
                    )
            ));
        }

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("size", size);
        requestBody.put("_source", List.of("name", "address", "roadAddress", "externalApiId", "location"));
        requestBody.put("query", Map.of("bool", bool));
        requestBody.put("sort", sort);
        if (includeSuggester) {
            requestBody.put("suggest", Map.of(
                    "name_suggest", Map.of(
                            "text", query,
                            "term", Map.of(
                                    "field", "name",
                                    "suggest_mode", "popular",
                                    "max_edits", 2
                            )
                    )
            ));
        }
        return requestBody;
    }
}

