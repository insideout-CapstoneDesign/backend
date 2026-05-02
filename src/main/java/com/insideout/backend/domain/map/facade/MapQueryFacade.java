package com.insideout.backend.domain.map.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 타 도메인(예: Navigation)에서 Map 도메인의 데이터를 안전하게 조회하기 위한 읽기 전용 서비스.
 *
 * <p>Repository를 직접 타 도메인에 노출하지 않고,
 * 이 서비스를 통해서 필요한 노드, 엣지, POI 등의 데이터를 제공합니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapQueryFacade {

    // 내부적으로 자기 도메인의 Repository는 자유롭게 주입받아 사용합니다.
    // private final NodeRepository nodeRepository;
    // private final EdgeRepository edgeRepository;
    // private final ObstacleRepository obstacleRepository;

    /*
    // 예시: Navigation 팀원이 호출할 메서드 껍데기
    public List<Node> getNodesForRouting(UUID mapVersionId) {
        return nodeRepository.findByMapVersionId(mapVersionId);
    }
    */
}
