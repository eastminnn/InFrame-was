package com.InFrame.domains.like;

import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.repository.ExperienceRepository;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.repository.HostRepository;
import com.InFrame.domains.like.entity.ExperienceLike;
import com.InFrame.domains.like.repository.ExperienceLikeRepository;
import com.InFrame.domains.like.service.LikeService;
import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import com.InFrame.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 유저가 같은 체험에 좋아요 토글을 동시에 보내는 상황 (따닥 클릭).
 * 실제 커밋이 필요하므로 @Transactional 을 쓰지 않고 직접 정리한다.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(TestFixtures.class)
class LikeConcurrencyTest {

    private static final int THREADS = 10;
    private static final int REPEAT = 20; // 타이밍에 따라 드물게 나는 문제(데드락 등)를 잡기 위해 반복

    @Autowired TestFixtures fixtures;
    @Autowired LikeService likeService;
    @Autowired ExperienceLikeRepository experienceLikeRepository;
    @Autowired ExperienceRepository experienceRepository;
    @Autowired HostRepository hostRepository;
    @Autowired UserRepository userRepository;

    private User user;
    private Host host;
    private Experience experience;

    @BeforeEach
    void setUp() {
        host = fixtures.host();
        experience = fixtures.experience(host, LocalTime.of(10, 0));
        user = fixtures.user(Role.USER);
    }

    @AfterEach
    void cleanUp() {
        experienceLikeRepository.findAll().stream()
                .filter(like -> like.getExperience().getId().equals(experience.getId()))
                .forEach(experienceLikeRepository::delete);
        experienceRepository.deleteById(experience.getId());
        hostRepository.deleteById(host.getId());
        userRepository.deleteById(host.getUser().getId());
        userRepository.deleteById(user.getId());
    }

    /** THREADS 개의 토글을 동시에 출발시키고, 발생한 예외를 모아 반환 */
    private List<Throwable> toggleConcurrently() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    likeService.toggleExperienceLike(user.getId(), experience.getId());
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown(); // 동시에 출발
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();
        return errors;
    }

    private long likeRows() {
        return experienceLikeRepository.findAll().stream()
                .filter(like -> like.getExperience().getId().equals(experience.getId()))
                .count();
    }

    @RepeatedTest(REPEAT)
    @DisplayName("A. 좋아요 없는 상태에서 동시 토글 → 좋아요는 최대 1개, 서버 오류 없음")
    void concurrentToggle_fromUnliked_keepsAtMostOneRow() throws Exception {
        List<Throwable> errors = toggleConcurrently();
        long rows = likeRows();

        System.out.printf("[LIKE-A] threads=%d rows=%d errors=%d %s%n", THREADS, rows, errors.size(),
                errors.stream().map(e -> e.getClass().getSimpleName()).distinct().toList());
        assertThat(rows).isLessThanOrEqualTo(1);
        assertThat(errors).isEmpty();
    }

    @RepeatedTest(REPEAT)
    @DisplayName("B. 좋아요 있는 상태에서 동시 토글 → 좋아요는 최대 1개, 서버 오류 없음")
    void concurrentToggle_fromLiked_noServerError() throws Exception {
        experienceLikeRepository.save(ExperienceLike.builder().user(user).experience(experience).build());

        List<Throwable> errors = toggleConcurrently();
        long rows = likeRows();

        System.out.printf("[LIKE-B] threads=%d rows=%d errors=%d %s%n", THREADS, rows, errors.size(),
                errors.stream().map(e -> e.getClass().getSimpleName()).distinct().toList());
        assertThat(rows).isLessThanOrEqualTo(1);
        assertThat(errors).isEmpty();
    }
}
