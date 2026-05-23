package com.mbank.repository;

import com.mbank.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 资金账户数据访问层
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 根据用户ID查询账户
     *
     * @param userId 用户ID
     * @return 账户（Optional）
     */
    Optional<Account> findByUserId(Long userId);
}
