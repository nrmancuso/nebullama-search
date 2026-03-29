package com.example.nebullamasearch;

import com.example.nebullamasearch.config.IndexInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class NebullamaSearchApplicationTests {

    @MockBean
    IndexInitializer indexInitializer;

    @Test
    void contextLoads() {
    }
}
