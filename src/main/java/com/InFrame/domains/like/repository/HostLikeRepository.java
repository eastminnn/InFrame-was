package com.InFrame.domains.like.repository;

import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.like.entity.HostLike;
import com.InFrame.domains.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HostLikeRepository extends JpaRepository<HostLike, Long> {

    // 조건부 삭제: 이미 지워진 행이면 0 을 반환할 뿐 예외가 나지 않는다 (동시 취소 대비)
    @Transactional
    @Modifying
    @Query("DELETE FROM HostLike hl WHERE hl.user.id = :userId AND hl.host.id = :hostId")
    int deleteByUserIdAndHostId(@Param("userId") Long userId, @Param("hostId") Long hostId);

    // 유저와 호스트 객체로 좋아요 찾기
    Optional<HostLike> findByUserAndHost(User user, Host host);

    // 특정 유저가 누른 모든 '하트'를 '호스트' 정보와 함께 조회
    @Query("SELECT hl FROM HostLike hl " +
            "JOIN FETCH hl.host h " +
            "LEFT JOIN FETCH h.user " + // Host의 User 정보까지 한 번에 가져옴
            "WHERE hl.user = :user")
    List<HostLike> findAllByUserWithHostAndUser(@Param("user") User user);

    // 호스트가 받은 좋아요 수
    long countByHost(Host host);

    // 유저가 누른 호스트 좋아요 수
    long countByUser(User user);

    boolean existsByUserAndHost(User user, Host host);
}