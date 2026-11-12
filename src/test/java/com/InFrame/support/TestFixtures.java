package com.InFrame.support;

import com.InFrame.domains.experience.entity.Experience;
import com.InFrame.domains.experience.entity.enums.DetailField;
import com.InFrame.domains.experience.entity.enums.ProfessionalField;
import com.InFrame.domains.experience.repository.ExperienceRepository;
import com.InFrame.domains.host.entity.Host;
import com.InFrame.domains.host.entity.enums.Category;
import com.InFrame.domains.host.repository.HostRepository;
import com.InFrame.domains.host.reqdto.HostRequestDto;
import com.InFrame.domains.user.entity.Role;
import com.InFrame.domains.user.entity.User;
import com.InFrame.domains.user.repository.UserRepository;
import org.springframework.boot.test.context.TestComponent;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** 테스트용 엔티티 생성 헬퍼. 값은 매번 고유하게 만들어 유니크 제약 충돌을 피한다. */
@TestComponent
public class TestFixtures {

    private final UserRepository userRepository;
    private final HostRepository hostRepository;
    private final ExperienceRepository experienceRepository;

    public TestFixtures(UserRepository userRepository, HostRepository hostRepository,
                        ExperienceRepository experienceRepository) {
        this.userRepository = userRepository;
        this.hostRepository = hostRepository;
        this.experienceRepository = experienceRepository;
    }

    public static String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public User user(Role role) {
        String s = unique();
        return userRepository.save(User.builder()
                .email("u-" + s + "@test.com").password("encoded").nickname("n" + s)
                .name("테스터").role(role).build());
    }

    public static HostRequestDto hostRequest() {
        String businessNumber = String.valueOf(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
        return new HostRequestDto(businessNumber, Category.MASTER_ARTISAN, "공방 " + unique(), "010-0000-0000",
                "biz@test.com", "http://pf.kakao.com/test", "소개", 35.82, 128.74, "경산시 대학로 1", null,
                LocalTime.of(9, 0), LocalTime.of(18, 0), "취소 정책", "자세한 소개");
    }

    /** 호스트 등록까지 마친 HOST 유저의 Host */
    public Host host() {
        User user = user(Role.HOST);
        return hostRepository.save(hostRequest().toEntity(user));
    }

    /** 매일, 지정한 시간에 예약 가능한 체험 */
    public Experience experience(Host host, LocalTime time) {
        return experienceRepository.save(Experience.builder()
                .professionalField(ProfessionalField.CRAFT_CREATION)
                .detailField(DetailField.CERAMIC_CRAFT)
                .title("도자기 체험 " + unique()).description("물레 체험")
                .price(10_000).durationInHours(2).maxCapacityPerSlot(4)
                .availableDaysOfWeek(EnumSet.allOf(DayOfWeek.class))
                .availableTimes(Set.of(time))
                .host(host)
                .build());
    }
}
