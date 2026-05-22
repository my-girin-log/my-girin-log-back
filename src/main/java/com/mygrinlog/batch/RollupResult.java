package com.mygrinlog.batch;

import java.util.List;

/**
 * 롤업 배치의 결과 요약. 데모/로그/수동 트리거 응답용.
 */
public record RollupResult(int processedSessions, int diariesCreated, int emptySessionsClosed, List<String> failures) {}
