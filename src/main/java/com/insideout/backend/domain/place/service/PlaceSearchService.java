package com.insideout.backend.domain.place.service;

import com.insideout.backend.domain.place.dto.response.PlaceSearchItemResponse;
import com.insideout.backend.global.apiPayload.code.GeneralErrorCode;
import com.insideout.backend.global.apiPayload.exception.ProjectException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlaceSearchService {

    private final KakaoPlaceSearchClient kakaoPlaceSearchClient;

    public List<PlaceSearchItemResponse> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }
        return kakaoPlaceSearchClient.searchByKeyword(query.trim());
    }
}
