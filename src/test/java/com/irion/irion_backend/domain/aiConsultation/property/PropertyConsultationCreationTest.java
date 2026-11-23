package com.irion.irion_backend.domain.aiConsultation.property;

import com.irion.irion_backend.global.config.TestcontainersConfig;
import com.irion.irion_backend.global.security.jwt.JwtProvider;
import com.irion.irion_backend.global.security.jwt.TokenBlacklistRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "seoul-open-api.refresh-enabled=false",
        "consultation.ai.model=test-model", "consultation.ai.key=test-key", "consultation.ai.timeout=3s"})
@Import(TestcontainersConfig.class)
@AutoConfigureMockMvc
@DirtiesContext
@SqlConfig(encoding = "UTF-8")
@Sql(scripts = "/sql/property-consultation-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class PropertyConsultationCreationTest {
    private static final String PATH = "/api/consultations/properties";
    private static final String CONTENT = """
            {"summary":"테스트 요약","riskFactors":[{"level":"HIGH","content":"비용 검토"}],
             "improvementSuggestions":[{"priority":1,"content":"계약 조건 확인"}],"contractConsiderations":"계약 검토"}
            """;
    private static HttpServer upstream;
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int upstreamStatus = 200;
    private volatile String content = CONTENT;
    private volatile String received;
    private CountDownLatch entered, release;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtProvider jwt;
    @MockitoBean TokenBlacklistRepository blacklist;

    @DynamicPropertySource
    static void endpoint(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.start();
        registry.add("consultation.ai.url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort() + "/chat");
    }

    @BeforeEach
    void prepare() {
        jdbc.update("delete from property_ai_consultations where property_analysis_id = 9001");
        jdbc.update("update analysis_runs set status = 'COMPLETED' where id = 9001");
        jdbc.update("""
                update property_analyses set expected_monthly_sales=120, monthly_fixed_cost=10,
                variable_cost_rate=0.2, monthly_operating_profit=70, break_even_sales=30, break_even_status='test',
                initial_required_cash=50, initial_operating_cash=40, safety_score=80, safety_grade='test',
                recommendation_rank=1, recommendation_reason='test' where id=9001
                """);
        entered = new CountDownLatch(1);
        release = new CountDownLatch(0);
        upstream.createContext("/chat", exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                received = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                entered.countDown();
                try {
                    release.await(8, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                byte[] bytes = mapper.writeValueAsBytes(Map.of("model", "test-model-revision",
                        "choices", List.of(Map.of("message", Map.of("content", content)))));
                exchange.sendResponseHeaders(upstreamStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
    }

    @AfterEach
    void removeHandler() {
        release.countDown();
        upstream.removeContext("/chat");
    }

    @AfterAll
    static void close() {
        upstream.stop(0);
    }

    private ResultActions submit(long user, long analysis) throws Exception {
        return mvc.perform(post(PATH).header("Authorization", bearer(user)).contentType("application/json")
                .content("{\"propertyAnalysisId\":" + analysis + "}"));
    }

    private String bearer(long user) {
        return "Bearer " + jwt.createAccessToken(user).token();
    }

    @Test
    void createsOnceAndReturnsSameContentFromGetAndRepeatedPost() throws Exception {
        var body = submit(9001, 9001).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary").value("테스트 요약"))
                .andExpect(jsonPath("$.riskFactors[0].level").value("HIGH"))
                .andExpect(jsonPath("$.improvementSuggestions[0].priority").value(1))
                .andExpect(jsonPath("$.completedAt").isString()).andReturn().getResponse().getContentAsByteArray();
        var saved = mapper.readTree(body);
        var getBody = mvc.perform(get(PATH + "/" + saved.get("propertyConsultId").asLong())
                        .header("Authorization", bearer(9001))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(saved, mapper.readTree(getBody));
        submit(9001, 9001).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        assertEquals(1, calls.get());
        var input = mapper.readTree(mapper.readTree(received).get("messages").get(1).get("content").asString());
        assertEquals(120, input.at("/analysis/expectedMonthlySales").asInt());
        assertEquals("이대 정문 1층", input.at("/property/name").asString());
        assertEquals("서대문구 카페 창업", input.at("/condition/title").asString());
        assertEquals("test-model-revision", jdbc.queryForObject(
                "select model_name from property_ai_consultations where property_analysis_id=9001", String.class));
    }

    @Test
    void simultaneousCallerSeesCommittedPendingRow() throws Exception {
        release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var first = executor.submit(() -> submit(9001, 9001).andReturn().getResponse().getStatus());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            submit(9001, 9001).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"));
            release.countDown();
            assertEquals(201, first.get(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void validatesAuthenticationBodyOwnershipAndExistence() throws Exception {
        mvc.perform(post(PATH)).andExpect(status().isUnauthorized());
        for (String body : List.of("{}", "{", "{\"propertyAnalysisId\":0}")) {
            mvc.perform(post(PATH).header("Authorization", bearer(9001)).contentType("application/json")
                    .content(body)).andExpect(status().isBadRequest());
        }
        submit(9002, 9001).andExpect(status().isForbidden());
        submit(9001, 999999).andExpect(status().isNotFound());
        assertEquals(0, calls.get());
    }

    @Test
    void requiresCompletedRunAndPopulatedMetrics() throws Exception {
        jdbc.update("update analysis_runs set status='PROCESSING' where id=9001");
        submit(9001, 9001).andExpect(status().isConflict());
        jdbc.update("update analysis_runs set status='COMPLETED' where id=9001");
        jdbc.update("update property_analyses set safety_score=null where id=9001");
        submit(9001, 9001).andExpect(status().isConflict());
        assertEquals(0, calls.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bad json", "bad level", "bad priority", "missing field", "upstream", "timeout"})
    void persistsFailureAndDoesNotRetry(String scenario) throws Exception {
        String code = "JSON_PARSING_FAILED";
        int statusCode = 500;
        switch (scenario) {
            case "upstream" -> { upstreamStatus = 503; code = "AI_CONSULTATION_GENERATION_FAILED"; statusCode = 502; }
            case "timeout" -> { release = new CountDownLatch(1); code = "AI_CONSULTATION_TIMEOUT"; statusCode = 504; }
            case "bad json" -> content = "{";
            case "bad level" -> content = CONTENT.replace("HIGH", "unknown");
            case "bad priority" -> content = CONTENT.replace(":1,", ":\"1\",");
            case "missing field" -> content = CONTENT.replace("contractConsiderations", "other");
        }
        var body = submit(9001, 9001).andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.status").value(statusCode)).andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value(PATH)).andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.propertyConsultId").isNumber())
                .andReturn().getResponse().getContentAsByteArray();
        long failedId = mapper.readTree(body).at("/data/propertyConsultId").asLong();
        release.countDown();
        submit(9001, 9001).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.propertyConsultId").value(failedId))
                .andExpect(jsonPath("$.completedAt").isEmpty());
        assertEquals(1, calls.get());
    }
}
