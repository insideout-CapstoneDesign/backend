package com.insideout.backend.domain.map.entity;

/**
 * Zone(구역)의 종류를 정의하는 열거형.
 *
 * <p>DB의 zone.kind CHECK 제약과 동기화되어 있습니다.
 * 새로운 종류를 추가할 경우 반드시 DB 마이그레이션 스크립트도 함께 수정해야 합니다.
 *
 * <pre>
 *   corridor         - 복도, 보행 통로
 *   room             - 일반 방, 강의실 등
 *   restricted       - 출입 통제 구역
 *   public_space     - 로비, 광장 등 공공 영역
 *   outdoor_boundary - 실외 경계 구역
 * </pre>
 */
public enum ZoneKind {
    corridor,
    room,
    restricted,
    public_space,
    outdoor_boundary;
}
