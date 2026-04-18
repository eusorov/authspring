package com.authspring.api.repo;

import com.authspring.api.domain.PersonalAccessToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalAccessTokenRepository extends JpaRepository<PersonalAccessToken, Long> {

    Optional<PersonalAccessToken> findByToken(String token);

    void deleteByToken(String token);

    void deleteByTokenableTypeAndTokenableId(String tokenableType, Long tokenableId);
}
