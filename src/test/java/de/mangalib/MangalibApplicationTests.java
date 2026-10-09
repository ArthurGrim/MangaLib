package de.mangalib;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import javax.sql.DataSource;
import de.mangalib.testsupport.GuardedTestDataSource;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@ActiveProfiles("test")
class MangalibApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() {
		assertInstanceOf(GuardedTestDataSource.class, dataSource);
	}

}
