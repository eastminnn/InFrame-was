package com.InFrame.domains.like.service;

import com.InFrame.common.exception.CustomException;
import com.InFrame.common.exception.error.ErrorCode;
import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.repository.ExperienceRepository;
import com.InFrame.domains.experience.resdto.ExperienceLikeResponseDto;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.repository.HostRepository;
import com.InFrame.domains.host.resdto.HostLikeResponseDto;
import com.InFrame.domains.like.entity.ExperienceLike;
import com.InFrame.domains.like.entity.HostLike;
import com.InFrame.domains.like.repository.ExperienceLikeRepository;
import com.InFrame.domains.like.repository.HostLikeRepository;
import com.InFrame.domains.review.entity.Review;
import com.InFrame.domains.review.repository.ReviewRepository;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeService {
    private final UserRepository userRepository;
    private final ExperienceRepository experienceRepository;
    private final HostRepository hostRepository;
    private final ExperienceLikeRepository experienceLikeRepository;
    private final HostLikeRepository hostLikeRepository;
    private final ReviewRepository reviewRepository;

    private static final int MAX_DEADLOCK_RETRIES = 3;

    // 조건부 DELETE → 지운 게 없으면 INSERT. 중복은 유니크 제약이 막는다.
    // 제약 위반을 catch 해야 하므로 트랜잭션 밖에서 실행한다 (rollback-only 방지)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean toggleExperienceLike(Long userId, Long experienceId) {
        User user = findUserById(userId);
        Experience experience = findExperienceById(experienceId);

        return retryOnDeadlock(() -> {
            if (experienceLikeRepository.deleteByUserIdAndExperienceId(userId, experienceId) > 0) {
                return false;
            }
            try {
                experienceLikeRepository.save(ExperienceLike.builder().user(user).experience(experience).build());
            } catch (DataIntegrityViolationException alreadyLiked) {
                // 다른 요청이 먼저 저장함 → 좋아요 상태
            }
            return true;
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean toggleHostLike(Long userId, Long hostId) {
        User user = findUserById(userId);
        Host host = findHostById(hostId);

        return retryOnDeadlock(() -> {
            if (hostLikeRepository.deleteByUserIdAndHostId(userId, hostId) > 0) {
                return false;
            }
            try {
                hostLikeRepository.save(HostLike.builder().user(user).host(host).build());
            } catch (DataIntegrityViolationException alreadyLiked) {
                // 다른 요청이 먼저 저장함 → 좋아요 상태
            }
            return true;
        });
    }

    // 삭제 직후 같은 키로 동시에 INSERT 하면 중복 검사의 S 락끼리 데드락이 날 수 있어 재시도한다
    private <T> T retryOnDeadlock(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (PessimisticLockingFailureException deadlock) {
                if (attempt >= MAX_DEADLOCK_RETRIES) {
                    throw deadlock;
                }
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(5, 30)); // 동시 재시도 충돌 방지
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw deadlock;
                }
            }
        }
    }

    // 내가 '하트' 누른 체험 목록 조회
    public List<ExperienceLikeResponseDto> getMyLikedExperiences(Long userId) {
        User user = findUserById(userId);

        // 1. '좋아요', 체험, 호스트, 호스트 유저 정보를 한 번에 가져옴
        List<ExperienceLike> likes = experienceLikeRepository.findAllByUserWithExperienceAndHost(user);

        if (likes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. '체험' 엔티티 목록만 추출
        List<Experience> experiences = likes.stream()
                .map(ExperienceLike::getExperience)
                .toList();

        // 3. '체험' 목록에 해당하는 모든 '리뷰'를 한 번에 가져옴
        List<Review> allReviews = reviewRepository.findAllByExperienceInWithExperience(experiences);

        // 4. 리뷰 목록을 체험별로 그룹화
        Map<Experience, List<Review>> reviewsByExperience = allReviews.stream()
                .collect(Collectors.groupingBy(
                        review -> review.getReservation().getExperience()
                ));

        return likes.stream()
                .map(like -> {
                    Experience experience = like.getExperience();
                    Host host = experience.getHost();
                    List<Review> reviews = reviewsByExperience.getOrDefault(experience, Collections.emptyList());

                    return ExperienceLikeResponseDto.from(experience, host, reviews);
                })
                .collect(Collectors.toList());
    }

    public List<HostLikeResponseDto> getMyLikedHosts(Long userId) {
        User user = findUserById(userId);

        // 1. '좋아요', '호스트', '호스트 유저' 정보를 한 번에 가져옴
        List<HostLike> likes = hostLikeRepository.findAllByUserWithHostAndUser(user);

        if (likes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. '좋아요' 목록에서 '호스트' 엔티티 목록만 추출
        List<Host> hosts = likes.stream()
                .map(HostLike::getHost)
                .toList();

        // 3. 추출된 '호스트' 목록에 해당하는 모든 '체험'과 '체험 이미지'를 한 번에 가져옴
        List<Experience> allExperiences = experienceRepository.findAllByHostInWithImages(hosts);

        // 3.5. 호스트가 등록한 모든 체험에 대한 '리뷰'를 한 번에 가져옴
        List<Review> allReviews = reviewRepository.findAllByExperienceInWithExperience(allExperiences);

        // 4. 체험 목록을 호스트별로 그룹화
        Map<Host, List<Experience>> experiencesByHost = allExperiences.stream()
                .collect(Collectors.groupingBy(Experience::getHost));

        // 5. 호스트별 '평균 평점' 및 '전체 후기 개수' 계산
        Map<Host, HostReviewSummary> hostReviewSummaries = allReviews.stream()
                .collect(Collectors.groupingBy(
                        review -> review.getReservation().getExperience().getHost(), // 리뷰의 예약, 체험을 통해 호스트를 추출
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                reviews -> {
                                    long reviewCount = reviews.size();
                                    // Review 엔티티에 getRating()이 있고 숫자형(int/double)을 반환한다고 가정
                                    double averageRating = reviews.stream()
                                            .mapToDouble(Review::getRating)
                                            .average()
                                            .orElse(0.0);
                                    // 소수점 한 자리로 반올림 (선택 사항)
                                    double roundedRating = Math.round(averageRating * 10) / 10.0;
                                    return new HostReviewSummary(roundedRating, reviewCount);
                                }
                        )
                ));

        return likes.stream()
                .map(like -> {
                    Host host = like.getHost();
                    List<Experience> experiences = experiencesByHost.getOrDefault(host, Collections.emptyList());
                    // 후기가 없는 호스트의 경우 기본값(평점 0.0, 개수 0L) 사용
                    HostReviewSummary summary = hostReviewSummaries.getOrDefault(host, new HostReviewSummary(0.0, 0L));

                    return HostLikeResponseDto.from(
                            host,
                            experiences,
                            summary.averageRating(),
                            summary.reviewCount()
                    );
                })
                .collect(Collectors.toList());
    }

    // 호스트 리뷰 요약을 위한 내부 레코드
    private record HostReviewSummary(double averageRating, long reviewCount) {}

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private Experience findExperienceById(Long experienceId) {
        return experienceRepository.findById(experienceId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERIENCE_NOT_FOUND));
    }

    private Host findHostById(Long hostId) {
        return hostRepository.findById(hostId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOST_NOT_FOUND));
    }
}
