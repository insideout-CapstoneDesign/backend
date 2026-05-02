package com.insideout.backend.domain.navigation.service;

import com.insideout.backend.domain.map.facade.MapQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실내외 길찾기(Navigation) 기능을 담당하는 핵심 서비스.
 * 
 * <p>Repository를 직접 참조하지 않고, MapQueryFacade 등
 * 다른 도메인의 Service를 주입받아 필요한 데이터를 가져옵니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NavigationService {

    // 타 도메인의 Repository를 직접 쓰지 않고 Service를 주입받아 사용합니다.
    private final MapQueryFacade mapQueryFacade;

    // TODO: A* 알고리즘 등 경로 탐색 로직 작성
    
}
