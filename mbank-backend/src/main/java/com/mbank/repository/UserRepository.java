package com.mbank.repository;

import com.mbank.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据手机号查询用户
     *
     * @param phone 手机号
     * @return 用户（Optional）
     */
    Optional<User> findByPhone(String phone);

    /**
     * 检查手机号是否已注册
     *
     * @param phone 手机号
     * @return true 表示已存在
     */
    boolean existsByPhone(String phone);
}
