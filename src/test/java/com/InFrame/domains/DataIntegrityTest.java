package com.InFrame.domains;

import com.InFrame.common.exception.CustomException;
import com.InFrame.common.exception.error.ErrorCode;
import com.InFrame.common.service.BusinessValidationService;
import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.service.ExperienceService;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.service.HostService;
import com.InFrame.domains.reservation.entity.Reservation;
import com.InFrame.domains.reservation.entity.enums.ReservationStatus;
import com.InFrame.domains.reservation.repository.ReservationRepository;
import com.InFrame.domains.reservation.reqdto.ReservationRequestDto;
import com.InFrame.domains.reservation.resdto.ReservationResponseDto;
import com.InFrame.domains.reservation.service.ReservationService;
import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import com.InFrame.domains.user.service.UserService;
import com.InFrame.support.TestFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("local")
@Import(TestFixtures.class)
@Transactional // 테스트마다 롤백
class DataIntegrityTest {

    @Autowired TestFixtures fixtures;
    @Autowired EntityManager em;
    @Autowired HostService hostService;
    @Autowired ExperienceService experienceService;
    @Autowired ReservationService reservationService;
    @Autowired UserService userService;
    @Autowired UserRepository userRepository;
    @Autowired ReservationRepository reservationRepository;

    @MockitoBean BusinessValidationService businessValidationService; // 외부 공공 API 호출 차단

    private static ErrorCode errorCode(Throwable e) {
        return ((CustomException) e).getErrorCode();
    }

    @Test
    @DisplayName("호스트 등록 후 USER 로 전환한 유저가 다시 호스트 등록하면 409")
    void changeToHost_whenHostAlreadyRegistered_returnsConflict() {
        Host host = fixtures.host();
        User user = host.getUser();
        user.updateRole(Role.USER); // /user/me/role 로 USER 전환한 상태
        em.flush();
        em.clear();
        User reloaded = userRepository.findById(user.getId()).orElseThrow();

        assertThatThrownBy(() -> hostService.changeToHost(reloaded, TestFixtures.hostRequest()))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(ErrorCode.USER_ALREADY_HOST));
    }

    @Test
    @DisplayName("HOST 권한인데 호스트 정보가 없으면 로고 업로드 시 NPE 가 아닌 404")
    void uploadCompanyLogo_withoutHostInfo_returnsNotFound() {
        User user = fixtures.user(Role.HOST);
        var file = new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> hostService.uploadCompanyLogo(user, file))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(ErrorCode.HOST_INFO_NOT_FOUND));
    }

    @Test
    @DisplayName("HOST 권한인데 호스트 정보가 없으면 체험 이미지 업로드 시 NPE 가 아닌 403")
    void uploadExperienceImages_withoutHostInfo_returnsForbidden() {
        Experience experience = fixtures.experience(fixtures.host(), LocalTime.of(10, 0));
        User user = fixtures.user(Role.HOST);
        var file = new MockMultipartFile("images", "a.png", "image/png", new byte[]{1});

        assertThatThrownBy(() -> experienceService.uploadExperienceImages(user, experience.getId(), List.of(file)))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(ErrorCode.FORBIDDEN_ACCESS));
    }

    @Test
    @DisplayName("오늘 이미 지난 시간은 예약할 수 없다")
    void createReservation_pastTimeToday_isRejected() {
        Experience experience = fixtures.experience(fixtures.host(), LocalTime.MIDNIGHT);
        User user = fixtures.user(Role.USER);
        var request = new ReservationRequestDto(experience.getId(), LocalDate.now(), LocalTime.MIDNIGHT, 1, 0);

        assertThatThrownBy(() -> reservationService.createReservation(user, request))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(ErrorCode.RESERVATION_IN_PAST));
    }

    @Test
    @DisplayName("예약 총 인원 = 성인 + 아동 (호스트 예약 목록에 표시되는 값)")
    void reservation_totalParticipants_isAdultsPlusChildren() {
        Experience experience = fixtures.experience(fixtures.host(), LocalTime.of(10, 0));
        User user = fixtures.user(Role.USER);
        var request = new ReservationRequestDto(experience.getId(), LocalDate.now().plusDays(1), LocalTime.of(10, 0), 2, 1);

        ReservationResponseDto response = reservationService.createReservation(user, request);
        Reservation saved = reservationRepository.findById(response.reservationId()).orElseThrow();

        assertThat(saved.getTotalParticipants()).isEqualTo(3);
    }

    @Test
    @DisplayName("예약 내역이 있는 유저도 탈퇴할 수 있고, 예약 기록은 남는다")
    void deleteUser_withReservation_succeedsAndKeepsReservation() {
        Experience experience = fixtures.experience(fixtures.host(), LocalTime.of(10, 0));
        User user = fixtures.user(Role.USER);
        var request = new ReservationRequestDto(experience.getId(), LocalDate.now().plusDays(1), LocalTime.of(10, 0), 1, 0);
        Long reservationId = reservationService.createReservation(user, request).reservationId();
        em.flush();

        String originalEmail = user.getEmail();

        assertThatCode(() -> {
            userService.deleteUser(user);
            em.flush();
        }).doesNotThrowAnyException();
        em.clear();

        // 예약 기록은 남고, 진행 중이던 예약은 취소되어 슬롯이 비워진다
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        // 개인정보는 익명화되고, 기존 이메일로는 조회되지 않는다 (로그인·기존 토큰 무효)
        User withdrawn = userRepository.findById(user.getId()).orElseThrow();
        assertThat(withdrawn.isDeleted()).isTrue();
        assertThat(withdrawn.getEmail()).isNotEqualTo(originalEmail);
        assertThat(withdrawn.getName()).isEqualTo("탈퇴회원");
        assertThat(userRepository.findByEmail(originalEmail)).isEmpty();
    }

    @Test
    @DisplayName("호스트 정보가 있는 계정은 탈퇴할 수 없다")
    void deleteUser_host_isRejected() {
        User hostUser = fixtures.host().getUser();
        em.flush();

        assertThatThrownBy(() -> userService.deleteUser(hostUser))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(errorCode(e)).isEqualTo(ErrorCode.HOST_WITHDRAWAL_NOT_ALLOWED));
    }
}
