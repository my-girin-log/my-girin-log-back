package com.mygrinlog.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGithubId(String githubId);

    @Override
    List<User> findAll();
}
