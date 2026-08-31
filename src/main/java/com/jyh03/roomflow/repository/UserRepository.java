package com.jyh03.roomflow.repository;

import com.jyh03.roomflow.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}