package com.InFrame;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 로컬/CI 모두 docker-compose(또는 CI 서비스 컨테이너)의 DB를 사용
@SpringBootTest
@ActiveProfiles("local")
class InFrameWasApplicationTests {

	@Test
	void contextLoads() {
	}

}
