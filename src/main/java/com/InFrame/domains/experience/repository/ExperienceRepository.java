package com.InFrame.domains.experience.repository;

import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.host.entity.Host;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
    // 호스트가 등록한 모든 체험 찾기
    List<Experience> findAllByHost(Host host);

    @Query("SELECT e FROM Experience e LEFT JOIN FETCH e.imageUrls WHERE e.host.id = :hostId")
    List<Experience> findAllByHostIdWithImages(@Param("hostId") Long hostId);

    // 호스트의 체험 중 첫 번째 데이터를 가져옴
    Optional<Experience> findTopByHost(Host host);

    // 체험을 가진 호스트 조회
    @Query("SELECT DISTINCT e.host FROM Experience e")
    List<Host> findDistinctHostsWithExperiences();

    @Query("SELECT DISTINCT e FROM Experience e " +
            "LEFT JOIN FETCH e.imageUrls " +
            "WHERE e.host IN :hosts")
    List<Experience> findAllByHostInWithImages(@Param("hosts") List<Host> hosts);

    @Query("SELECT e FROM Experience e JOIN FETCH e.host WHERE e.id IN :ids")
    List<Experience> findAllByIdWithHost(@Param("ids") List<Long> ids);

    // 예약 생성 시 같은 체험의 예약을 한 번에 하나씩 처리하기 위해 체험 행에 X 락(SELECT ... FOR UPDATE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Experience e WHERE e.id = :id")
    Optional<Experience> findByIdForUpdate(@Param("id") Long id);
}
