package com.irion.irion_backend.domain.aiConsultation.comparison.client;

import com.irion.irion_backend.domain.aiConsultation.comparison.entity.PropertySnapshot;
import com.irion.irion_backend.global.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.irion.irion_backend.domain.aiConsultation.common.exception.ConsultationErrorCode.*;

class ChatCompletionComparisonClientTest {
    final ObjectMapper mapper = new ObjectMapper();
    final ChatCompletionComparisonClient client = new ChatCompletionComparisonClient(mapper,"","", "", Duration.ofSeconds(1));

    PropertySnapshot snapshot(long id, Object cost) {
        return snapshot(id,cost,"v1",
                Map.of("userEmail","secret","stabilityByProperty",Map.of(""+id,Map.of("fundingScore",id))));
    }

    PropertySnapshot snapshot(long id, Object cost, String version, Map<String,Object> assumptions) {
        return new PropertySnapshot(2,id,id,null,version,null,
                Map.of("industryId",1,"preferredRegionCode","11410","totalCapital",100,"availableCapital",80,
                        "loanAmount",20,"monthlyLaborCost",10,"monthlyOtherOperatingCost",2,"costOfSalesRate",cost,
                        "userId",id,"title","private "+id),assumptions,
                id,"name","address",null,null,null,null,null,null,null);
    }

    ComparisonConsultationAiClient.Input input(Object cost) {
        return ComparisonConsultationAiClient.Input.from(List.of(snapshot(8,"30.0"),snapshot(12,cost)));
    }

    Map<String,Object> valid() {
        var answer = new HashMap<String,Object>();
        answer.put("summary","검토");
        answer.put("description","보류 이유");
        answer.put("recommendedPropertyAnalysisId",8);
        answer.put("riskFactors",List.of("비용"));
        answer.put("positiveFactors",List.of());
        answer.put("recommendedContractConditions",List.of());
        return answer;
    }

    String envelope(String content) {
        return mapper.writeValueAsString(Map.of("model","revision","choices",List.of(Map.of("message",Map.of("content",content)))));
    }

    @Test
    void excludesMetadataAndComparesOnlySubstantiveConditions() {
        var input = input(30);
        assertFalse(input.differentConditions());
        assertFalse(input.differentCalculationBasis());
        String payload = mapper.writeValueAsString(ComparisonConsultationAiClient.AiProperty.from(input.properties().get(0)));
        assertFalse(payload.contains("private"));
        assertFalse(payload.contains("secret"));
        assertFalse(payload.contains("userId"));
        assertTrue(payload.contains("fundingScore"));
        assertTrue(input(31).differentConditions());
    }

    @Test
    void acceptsSelectedRecommendationOrExplicitAbstention() {
        var fields = valid();
        var report = client.parse(input(30),envelope(mapper.writeValueAsString(fields)));
        assertEquals(8L,report.recommendedPropertyAnalysisId());
        fields.put("recommendedPropertyAnalysisId",null);
        fields.put("conditionDifferences","원가율이 다름");
        assertNull(client.parse(input(31),envelope(mapper.writeValueAsString(fields))).recommendedPropertyAnalysisId());
    }

    @ParameterizedTest
    @ValueSource(strings={"recommendedPropertyAnalysisId","summary","description","riskFactors","positiveFactors","recommendedContractConditions"})
    void rejectsMissingFields(String field) {
        var fields = valid();
        fields.remove(field);
        rejects(input(30),mapper.writeValueAsString(fields));
    }

    @Test
    void rejectsBadTypesOutOfSelectionAndMissingExplanation() {
        var fields = valid();
        rejects(input(31),mapper.writeValueAsString(fields));
        rejects(input(30),"{");
        rejects(input(30),mapper.writeValueAsString(fields)+" {}");
        for (Object id : List.of(999,"8",8.25)) {
            fields.put("recommendedPropertyAnalysisId",id);
            rejects(input(30),mapper.writeValueAsString(fields));
        }
        fields = valid();
        for (Object risks : List.of("text",List.of(" "),Arrays.asList("ok",null))) {
            fields.put("riskFactors",risks);
            rejects(input(30),mapper.writeValueAsString(fields));
        }
        fields = valid();
        fields.put("summary"," ");
        rejects(input(30),mapper.writeValueAsString(fields));
    }

    @Test
    void requiresExplanationForVersionOrPolicyChanges() {
        var first = snapshot(8,30,"v1",Map.of("scoreRounding","HALF_UP"));
        for (var second : List.of(snapshot(12,30,"v2",Map.of("scoreRounding","HALF_UP")),
                snapshot(12,30,"v1",Map.of("scoreRounding","HALF_EVEN")))) {
            var input = ComparisonConsultationAiClient.Input.from(List.of(first,second));
            assertFalse(input.differentConditions());
            assertTrue(input.differentCalculationBasis());
            rejects(input,mapper.writeValueAsString(valid()));
            var fields = valid();
            fields.put("conditionDifferences","점수 산출 기준이 달라 직접 비교에 한계가 있습니다.");
            assertEquals(fields.get("conditionDifferences"),
                    client.parse(input,envelope(mapper.writeValueAsString(fields))).conditionDifferences());
        }
    }

    @Test
    void sendsOnlyTypedValuesToLoopbackServer() throws Exception {
        var hidden = Map.of("userId",42,"email","private@example.test");
        var malformed = Map.<String,Object>of("scoreRounding",hidden,
                "monthlySalesEstimation",Map.of("formula",hidden,"divisionScale","invalidScale"),
                "financialCalculation",Map.of("openingCosts",List.of(hidden),
                        "approvedMonthlySalesWonByProperty",Map.of("8",hidden)),
                "stabilityByProperty",Map.of("8",Map.of("fundingScore",hidden,"gradeRestrictions",List.of(hidden))));
        var normal = Map.<String,Object>of("financialCalculation",Map.of("openingCosts",List.of("DEPOSIT"),
                "approvedMonthlySalesWonByProperty",Map.of("12","150.25")),
                "stabilityByProperty",Map.of("12",Map.of("fundingScore",42,"gradeRestrictions",List.of("운영자금 부족"))));
        var input = ComparisonConsultationAiClient.Input.from(List.of(snapshot(8,30,"v1",malformed),
                snapshot(12,30,"v1",normal)));
        var answer = valid();
        answer.put("conditionDifferences","계산 가정 확인 필요");
        var received = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            try (exchange) {
                received.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                byte[] bytes = envelope(mapper.writeValueAsString(answer)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var http = new ChatCompletionComparisonClient(mapper,"http://127.0.0.1:"+server.getAddress().getPort(),
                    "test","test",Duration.ofSeconds(3));
            assertEquals(8L,http.generate(input).recommendedPropertyAnalysisId());
            var payload = mapper.readTree(mapper.readTree(received.get()).path("messages").path(1).path("content").asString());
            var properties = payload.path("properties");
            var filtered = properties.path(0).path("assumptions");
            assertFalse(filtered.has("scoreRounding"));
            assertFalse(filtered.has("fundingScore"));
            assertFalse(filtered.has("gradeRestrictions"));
            assertFalse(filtered.has("approvedMonthlySalesWonByProperty"));
            assertFalse(payload.toString().contains("private@example.test"));
            assertFalse(payload.toString().contains("userId"));
            assertFalse(payload.toString().contains("invalidScale"));
            var audit = properties.path(1).path("assumptions");
            assertTrue(audit.path("approvedMonthlySalesWonByProperty").isNumber());
            assertEquals("운영자금 부족",audit.path("gradeRestrictions").path(0).asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void translatesTransportStatusTimeoutMalformedBodyAndConfiguration() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/slow")) {
                    try { release.await(4,java.util.concurrent.TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    return;
                }
                byte[] body = "{".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(Integer.parseInt(path.substring(1)),body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:"+server.getAddress().getPort();
            for (String path : List.of("200","500","408","504","slow")) {
                var http = new ChatCompletionComparisonClient(mapper,url+"/"+path,"test","test",
                        path.equals("slow") ? Duration.ofMillis(150) : Duration.ofSeconds(3));
                var expected = switch (path) {
                    case "200" -> JSON_PARSING_FAILED;
                    case "500" -> AI_CONSULTATION_GENERATION_FAILED;
                    default -> AI_CONSULTATION_TIMEOUT;
                };
                assertEquals(expected,assertThrows(BusinessException.class,() -> http.generate(input(30))).getErrorCode());
            }
            assertEquals(AI_CONFIGURATION_ERROR,assertThrows(BusinessException.class,
                    () -> client.generate(input(30))).getErrorCode());
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    void rejects(ComparisonConsultationAiClient.Input input, String content) {
        assertEquals(JSON_PARSING_FAILED,assertThrows(BusinessException.class,
                () -> client.parse(input,envelope(content))).getErrorCode());
    }
}
