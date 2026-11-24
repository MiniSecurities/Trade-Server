package com.sangyunpark99.tradeserver.user.repository;

import com.sangyunpark99.tradeserver.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);
}
