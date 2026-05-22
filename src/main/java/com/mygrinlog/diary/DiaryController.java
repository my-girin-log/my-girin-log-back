package com.mygrinlog.diary;

import com.mygrinlog.auth.CurrentUser;
import com.mygrinlog.common.web.ApiPaths;
import com.mygrinlog.common.web.NotFoundException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * 워넬 자료 §2.3 (Diary 관리).
 *  - GET    /diaries?yearMonth=YYYY-MM   잔디용 경량 응답. 빈 날은 응답에 없음.
 *  - GET    /diaries/{dateKey}           상세. 없으면 404 → 프론트가 "기록 없음" 렌더.
 *  - PUT    /diaries/{dateKey}           수정 (title/markdown/emotionEmoji/tags).
 *  - DELETE /diaries/{dateKey}           삭제.
 */
@RestController
@RequestMapping(ApiPaths.BASE + "/diaries")
public class DiaryController {

    private final DiaryRepository diaryRepository;

    public DiaryController(DiaryRepository diaryRepository) {
        this.diaryRepository = diaryRepository;
    }

    /**
     * yearMonth 가 있으면 해당 월만, 없으면 모든 다이어리 반환 (프론트 mockApi 와 호환).
     * 프론트는 yearMonth 없이 한 번 호출해 전체 잔디를 받아 캘린더에 뿌린다.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public DiaryListResponse list(@CurrentUser Long userId,
                                  @RequestParam(required = false) String yearMonth) {
        List<Diary> diaries;
        if (yearMonth == null || yearMonth.isBlank()) {
            // 전체 — 잔디 캘린더가 무제한 스크롤할 때.
            diaries = diaryRepository.findAllByUserIdAndDateKeyBetweenOrderByDateKeyAsc(
                    userId, LocalDate.of(2000, 1, 1), LocalDate.of(2999, 12, 31));
        } else {
            YearMonth ym = parseYearMonth(yearMonth);
            diaries = diaryRepository.findAllByUserIdAndDateKeyBetweenOrderByDateKeyAsc(
                    userId, ym.atDay(1), ym.atEndOfMonth());
        }
        List<DiaryListItem> items = diaries.stream()
                .map(d -> new DiaryListItem(d.getDateKey(), d.getEmotionEmoji()))
                .toList();
        return new DiaryListResponse(items);
    }

    @GetMapping("/{dateKey}")
    @Transactional(readOnly = true)
    public DiaryDetail detail(@CurrentUser Long userId, @PathVariable LocalDate dateKey) {
        Diary diary = diaryRepository.findByUserIdAndDateKey(userId, dateKey)
                .orElseThrow(() -> new NotFoundException("diary not found: " + dateKey));
        return DiaryDetail.from(diary);
    }

    @PutMapping("/{dateKey}")
    @Transactional
    public DiaryDetail update(@CurrentUser Long userId,
                              @PathVariable LocalDate dateKey,
                              @RequestBody DiaryUpdateRequest request) {
        Diary diary = diaryRepository.findByUserIdAndDateKey(userId, dateKey)
                .orElseThrow(() -> new NotFoundException("diary not found: " + dateKey));
        diary.update(
                request.title() == null ? diary.getTitle() : request.title(),
                request.markdown() == null ? diary.getMarkdown() : request.markdown(),
                request.emotionEmoji() == null ? diary.getEmotionEmoji() : request.emotionEmoji(),
                request.tags() == null ? diary.getTags() : request.tags()
        );
        return DiaryDetail.from(diary);
    }

    @DeleteMapping("/{dateKey}")
    @Transactional
    public void delete(@CurrentUser Long userId, @PathVariable LocalDate dateKey) {
        Diary diary = diaryRepository.findByUserIdAndDateKey(userId, dateKey)
                .orElseThrow(() -> new NotFoundException("diary not found: " + dateKey));
        diaryRepository.delete(diary);
    }

    private YearMonth parseYearMonth(String raw) {
        if (raw == null || raw.isBlank()) return YearMonth.now();
        try { return YearMonth.parse(raw); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid yearMonth (expected YYYY-MM): " + raw); }
    }

    public record DiaryListResponse(List<DiaryListItem> diaries) {}

    public record DiaryListItem(LocalDate dateKey, String emotionEmoji) {}

    public record DiaryUpdateRequest(String title, String markdown, String emotionEmoji, List<String> tags) {}

    public record DiaryDetail(Long id, LocalDate dateKey, String title, String markdown,
                              String emotionEmoji, List<String> tags) {
        static DiaryDetail from(Diary d) {
            return new DiaryDetail(d.getId(), d.getDateKey(), d.getTitle(), d.getMarkdown(),
                    d.getEmotionEmoji(), d.getTags());
        }
    }
}
