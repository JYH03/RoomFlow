package com.jyh03.roomflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "roomflow.offer-expiration.enabled=false",
        "roomflow.no-show.enabled=false",
        "roomflow.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@ActiveProfiles("test")
class RoomflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
