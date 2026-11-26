package com.InFrame.domains.reservation;

import com.InFrame.common.exception.CustomException;
import com.InFrame.common.exception.error.ErrorCode;
import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.repository.ExperienceRepository;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.repository.HostRepository;
import com.InFrame.domains.reservation.entity.Reservation;
import com.InFrame.domains.reservation.repository.ReservationRepository;
import com.InFrame.domains.reservation.reqdto.ReservationRequestDto;
import com.InFrame.domains.reservation.service.ReservationService;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// 스레드마다 자기 트랜잭션으로 커밋해야 서로 경쟁하므로 테스트에 @Transactional 을 붙이지 않는다
@SpringBootTest
@ActiveProfiles("local")
@Import(TestFixtures.class)
class ReservationConcurrencyTest {

    private static final int THREADS = 10;
    private static final int REPEAT = 20;
    private static final LocalTime SLOT = LocalTime.of(10, 0);

    @Autowired TestFixtures fixtures;
    @Autowired ReservationService reservationService;
    @Autowired ReservationRepository reservationRepository;
    @Autowired ExperienceRepository experienceRepository;
    @Autowired HostRepository hostRepository;
    @Autowired UserRepository userRepository;

    private Host host;
    private Experience experience;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        host = fixtures.host();
        experience = fixtures.experience(host, SLOT);
        for (int i = 0; i < THREADS; i++) {
            users.add(fixtures.user(Role.USER));
        }
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.deleteAll(reservationsOfExperience());
        experienceRepository.deleteById(experience.getId());
        hostRepository.deleteById(host.getId());
        userRepository.deleteById(host.getUser().getId());
        users.forEach(user -> userRepository.deleteById(user.getId()));
        users.clear();
    }

    @RepeatedTest(REPEAT)
    @DisplayName("서로 다른 유저 10명이 같은 시간대에 동시에 예약하면 1건만 저장된다")
    void sameSlot_concurrentReservations_onlyOneSaved() throws InterruptedException {
        ReservationRequestDto request = new ReservationRequestDto(
                experience.getId(), LocalDate.now().plusDays(1), SLOT, 2, 0);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger slotTaken = new AtomicInteger();   // 409: 이미 예약된 시간
        List<Throwable> errors = new CopyOnWriteArrayList<>(); // 그 밖의 예외

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS); // 스레드가 모두 준비될 때까지 대기
        CountDownLatch start = new CountDownLatch(1);       // 신호 한 번에 동시에 출발
        CountDownLatch done = new CountDownLatch(THREADS);  // 모두 끝날 때까지 대기

        for (User user : users) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    reservationService.createReservation(user, request);
                    success.incrementAndGet();
                } catch (CustomException e) {
                    if (e.getErrorCode() == ErrorCode.RESERVATION_SLOT_NOT_AVAILABLE) slotTaken.incrementAndGet();
                    else errors.add(e);
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        long saved = reservationsOfExperience().size();
        System.out.printf("[RESERVATION] threads=%d saved=%d success=%d slotTaken=%d errors=%d %s%n",
                THREADS, saved, success.get(), slotTaken.get(), errors.size(),
                errors.stream().map(e -> e.getClass().getSimpleName()).distinct().toList());

        assertThat(saved).isEqualTo(1);
        assertThat(errors).isEmpty();
    }

    private List<Reservation> reservationsOfExperience() {
        return reservationRepository.findAll().stream()
                .filter(r -> r.getExperience().getId().equals(experience.getId()))
                .toList();
    }
}
