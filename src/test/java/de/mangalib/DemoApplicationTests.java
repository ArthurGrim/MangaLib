package de.mangalib;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import de.mangalib.repository.MangaReiheRepository;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class DemoApplicationTests {

	@Autowired
	private MangaReiheRepository mangaReiheRepository;

	@Test
	void contextLoads() {
		assertNotNull(mangaReiheRepository);
	}

}
