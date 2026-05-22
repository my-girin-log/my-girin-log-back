package com.mygrinlog.diary;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiaryRepository extends JpaRepository<Diary, Long> {

    Optional<Diary> findByUserIdAndDateKey(Long userId, LocalDate dateKey);

    List<Diary> findAllByUserIdAndDateKeyBetweenOrderByDateKeyAsc(Long userId, LocalDate from, LocalDate to);

    boolean existsByUserIdAndDateKey(Long userId, LocalDate dateKey);
}
