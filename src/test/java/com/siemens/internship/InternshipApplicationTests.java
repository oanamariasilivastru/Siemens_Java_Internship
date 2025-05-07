package com.siemens.internship;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InternshipApplicationTests {

	/** Simply verifies that the Spring context starts up. */
	@Test
	void contextLoads() {
		// no-op
	}

	/**
	 * Explicitly calls the generated main() method
	 * to bump coverage on that line.
	 */
	@Test
	void mainRunsWithoutException() {
		// Disable the web server for this direct invocation
		System.setProperty("spring.main.web-application-type", "none");
		InternshipApplication.main(new String[]{});
	}
}
