package com.InFrame.domains.reservation;

import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.repository.ExperienceRepository;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.repository.HostRepository;
import com.InFrame.domains.reservation.repository.ReservationRepository;
import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import com.InFrame.security.jwt.JwtUtil;
import com.InFrame.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// 다른 트랜잭션이 체험 행 락을 오래 쥐고 있을 때, 예약 요청이 무한정 기다리지 않는지 확인
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestFixtures.class)
class ReservationLockTimeoutTest {

    private static final LocalTime SLOT = LocalTime.of(10, 0);
    private static final long HOLD_SECONDS = 8; // 락을 쥐고 있는 최대 시간

    @Autowired MockMvc mockMvc;
    @Autowired JwtUtil jwtUtil;
    @Autowired TestFixtures fixtures;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ExperienceRepository experienceRepository;
    @Autowired ReservationRepository reservationRepository;
    @Autowired HostRepository hostRepository;
    @Autowired UserRepository userRepository;

    private Host host;
    private Experience experience;
    private User user;

    @BeforeEach
    void setUp() {
        host = fixtures.host();
        experience = fixtures.experience(host, SLOT);
        user = fixtures.user(Role.USER);
    }

    @AfterEach
    void cleanUp() {
        reservationRepository.findAll().stream()
                .filter(r -> r.getExperience().getId().equals(experience.getId()))
                .forEach(reservationRepository::delete);
        experienceRepository.deleteById(experience.getId());
        hostRepository.deleteById(host.getId());
        userRepository.deleteById(host.getUser().getId());
        userRepository.deleteById(user.getId());
    }

    @Test
    @DisplayName("체험 행 락을 다른 트랜잭션이 오래 쥐고 있으면, 예약 요청은 약 3초 뒤 503으로 실패한다")
    void lockHeldTooLong_reservationFailsFastWith503() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // 다른 요청이 체험 행 락을 쥐고 커밋하지 않는 상황
        Thread holder = new Thread(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            experienceRepository.findByIdForUpdate(experience.getId());
            locked.countDown();
            try {
                release.await(HOLD_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        holder.start();
        locked.await();

        String token = jwtUtil.createAccessToken(user.getId(), user.getEmail(), "USER");
        String body = """
                {"experienceId": %d, "reservationDate": "%s", "startTime": "10:00", "numAdults": 2, "numChildren": 0}
                """.formatted(experience.getId(), LocalDate.now().plusDays(1));

        long start = System.nanoTime();
        MvcResult result;
        try {
            result = mockMvc.perform(post("/api/v1/reservation")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();
        } finally {
            release.countDown();
            holder.join();
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        System.out.printf("[LOCK-TIMEOUT] status=%d elapsedMs=%d body=%s%n",
                result.getResponse().getStatus(), elapsedMs, result.getResponse().getContentAsString());

        assertThat(result.getResponse().getStatus()).isEqualTo(503);
        assertThat(elapsedMs).isBetween(2_500L, 5_000L);
    }
}
