package com.insideout.backend.domain.map.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * POI 카테고리 트리 테이블.
 *
 * 계층형 분류 체계를 ltree 타입으로 저장:
 * <pre>
 *   facility
 *   ├── facility.restroom
 *   ├── facility.elevator
 *   ├── facility.stair
 *   └── facility.parking
 *
 *   store
 *   ├── store.cafe
 *   ├── store.food
 *   └── store.retail
 *
 *   academic
 *   ├── academic.classroom
 *   ├── academic.lab
 *   └── academic.office
 * </pre>
 *
 * <p>POI 엔티티가 이 테이블의 id를 외래키로 참조해서 자기 카테고리를 가짐.
 *
 * <p>참고: 현재는 ltree 타입을 String으로만 저장. 트리 검색 쿼리는
 * 필요 시 native query로 작성.
 */
@Entity
@Table(name = "poi_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PoiCategory {

    /**
     * 카테고리 ID (Primary Key, 자동 증가).
     *
     * <p>bigserial 타입 — node_kind/edge_kind와 달리 카테고리는 동적으로 추가될 가능성이 있어서
     * 의미 있는 PK 대신 숫자 PK 사용.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 카테고리 코드 (UNIQUE).
     *
     * <p>예시: "facility.restroom", "store.cafe".
     * path와 거의 같은 값이지만 별도 컬럼으로 둬서 코드 검색 시 인덱스 활용.
     */
    @Column(unique = true, nullable = false, length = 100)
    private String code;

    /**
     * 트리 경로 (PostgreSQL ltree 타입).
     *
     * <p>점(.)으로 구분된 계층 경로: "store.cafe", "academic.classroom".
     *
     * <p>ltree는 Hibernate가 직접 지원 안 해서 String으로 매핑.
     * 트리 검색이 필요하면 native query로 ltree 연산자(`<@`, `@>`) 사용.
     */
    @Column(nullable = false, columnDefinition = "ltree")
    private String path;

    /**
     * 카테고리 한글 이름.
     * <p>예시: "화장실", "카페", "강의실".
     */
    @Column(name = "name_ko", nullable = false)
    private String nameKo;

    /**
     * 카테고리 영문 이름 (선택).
     * <p>예시: "Restroom", "Cafe", "Classroom".
     */
    @Column(name = "name_en")
    private String nameEn;

    /**
     * 아이콘 식별자 (선택).
     * <p>프론트엔드가 이 값을 보고 적절한 아이콘 표시.
     * <p>예시: "icon_restroom", "icon_cafe".
     */
    @Column(name = "icon_key")
    private String iconKey;

    /**
     * 시스템 기본 카테고리 여부.
     *
     * <p>true: 시스템이 기본 제공하는 카테고리 (시드 데이터)
     * <br>false: 사용자(테넌트 관리자)가 추가한 커스텀 카테고리
     *
     * <p>true인 카테고리는 삭제·수정 제한.
     */
    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @Builder
    public PoiCategory(String code, String path, String nameKo, String nameEn,
                       String iconKey, boolean isSystem) {
        this.code = code;
        this.path = path;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.iconKey = iconKey;
        this.isSystem = isSystem;
    }
}