package com.mygrinlog.retrospective;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetrospectiveRepository extends JpaRepository<Retrospective, Long> {

    List<Retrospective> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
