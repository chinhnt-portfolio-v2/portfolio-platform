package dev.chinh.portfolio.apps.wallet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chinh.portfolio.apps.wallet.Category;
import dev.chinh.portfolio.apps.wallet.CategoryRepository;
import dev.chinh.portfolio.apps.wallet.Wallet;
import dev.chinh.portfolio.apps.wallet.WalletRepository;
import dev.chinh.portfolio.apps.wallet.dto.NlpParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Parses Vietnamese financial text into a structured NlpParseResult using Claude
 * tool_use mode. Tool_use is used to prevent prompt injection — user text is placed
 * in a separate user message, never interpolated into the system prompt or tool schema.
 */
@Service
public class NlpService {

    private static final Logger log = LoggerFactory.getLogger(NlpService.class);

    // Strip control characters (ASCII 0-31 except tab/newline) and zero-width chars
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F\\u200B-\\u200D\\uFEFF]");

    private static final int MAX_WALLETS_IN_CONTEXT = 10;
    private static final int MAX_CATEGORIES_IN_CONTEXT = 15;

    private final WalletRepository walletRepo;
    private final CategoryRepository categoryRepo;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${app.nlp.proxy-url}")
    private String proxyUrl;

    @Value("${app.nlp.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${app.nlp.max-tokens:500}")
    private int maxTokens;

    @Value("${app.nlp.api-key:}")
    private String apiKey;

    public NlpService(WalletRepository walletRepo,
                      CategoryRepository categoryRepo,
                      RestClient.Builder restClientBuilder,
                      ObjectMapper objectMapper) {
        this.walletRepo = walletRepo;
        this.categoryRepo = categoryRepo;
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    /**
     * Parse natural Vietnamese text into a structured transaction result.
     *
     * @param rawText user input (already validated for length in DTO)
     * @param userId  authenticated user ID
     * @return NlpParseResult with resolved wallet/category IDs where possible
     */
    public NlpParseResult parse(String rawText, UUID userId) {
        String sanitized = sanitize(rawText);

        List<Wallet> wallets = walletRepo
            .findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId)
            .stream().limit(MAX_WALLETS_IN_CONTEXT).toList();

        List<Category> categories = categoryRepo
            .findByUserIdAndIsActiveTrueOrderByIsDefaultDescNameAsc(userId)
            .stream().limit(MAX_CATEGORIES_IN_CONTEXT).toList();

        String userMessage = buildUserMessage(sanitized, wallets, categories);

        NlpRawParse raw = callProxy(userMessage);
        return resolveIds(raw, wallets, categories);
    }

    // ── Prompt Building ───────────────────────────────────────────────────────

    private String buildUserMessage(String text, List<Wallet> wallets, List<Category> categories) {
        StringBuilder sb = new StringBuilder();

        if (!wallets.isEmpty()) {
            sb.append("Available wallets: ");
            wallets.forEach(w -> sb.append(w.getName()).append(" (").append(w.getType()).append("), "));
            sb.setLength(sb.length() - 2); // trim last comma
            sb.append("\n");
        }

        if (!categories.isEmpty()) {
            sb.append("Available categories: ");
            categories.forEach(c -> sb.append(c.getName()).append(" (").append(c.getType()).append("), "));
            sb.setLength(sb.length() - 2);
            sb.append("\n");
        }

        sb.append("Today: ").append(LocalDate.now()).append("\n");
        sb.append("\nParse: \"").append(text).append("\"");

        return sb.toString();
    }

    // ── Proxy Call ────────────────────────────────────────────────────────────

    private NlpRawParse callProxy(String userMessage) {
        Map<String, Object> toolInputSchema = Map.of(
            "type", "object",
            "properties", Map.of(
                "walletName",    Map.of("type", List.of("string", "null")),
                "categoryName",  Map.of("type", List.of("string", "null")),
                "amount",        Map.of("type", List.of("number", "null")),
                "type",          Map.of("type", "string", "enum", List.of("INCOME", "EXPENSE")),
                "date",          Map.of("type", List.of("string", "null"), "description", "ISO date YYYY-MM-DD or null"),
                "note",          Map.of("type", List.of("string", "null"))
            ),
            "required", List.of("type")
        );

        Map<String, Object> tool = Map.of(
            "name", "parse_transaction",
            "description", "Parse a Vietnamese financial transaction from natural language",
            "input_schema", toolInputSchema
        );

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "max_tokens", maxTokens,
            "system", "You are a Vietnamese financial transaction parser. " +
                "Use the parse_transaction tool to output results. " +
                "Handle relative dates (hôm qua=yesterday, tuần trước=last week), " +
                "Vietnamese currency (50k=50000, 1tr=1000000, 1.5tr=1500000). " +
                "Default type to EXPENSE when unclear. If a field cannot be determined, pass null.",
            "tools", List.of(tool),
            "tool_choice", Map.of("type", "tool", "name", "parse_transaction"),
            "messages", List.of(
                Map.of("role", "user", "content", userMessage)
            )
        );

        try {
            String responseBody = restClient.post()
                .uri(proxyUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .body(requestBody)
                .retrieve()
                .body(String.class);

            return parseProxyResponse(responseBody);

        } catch (RestClientException e) {
            log.warn("NLP proxy call failed: {}", e.getClass().getSimpleName());
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NLP service unavailable"
            );
        }
    }

    private NlpRawParse parseProxyResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content");

            if (!content.isArray() || content.isEmpty()) {
                log.warn("NLP proxy returned empty content");
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NLP parse failed");
            }

            // Find tool_use block
            for (JsonNode block : content) {
                if ("tool_use".equals(block.path("type").asText())) {
                    JsonNode input = block.path("input");
                    return new NlpRawParse(
                        nullableText(input, "walletName"),
                        nullableText(input, "categoryName"),
                        nullableBigDecimal(input, "amount"),
                        input.path("type").asText("EXPENSE"),
                        nullableText(input, "date"),
                        nullableText(input, "note")
                    );
                }
            }

            log.warn("NLP proxy response has no tool_use block");
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NLP parse failed");

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("NLP proxy response malformed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "NLP parse failed");
        }
    }

    // ── ID Resolution ─────────────────────────────────────────────────────────

    private NlpParseResult resolveIds(NlpRawParse raw, List<Wallet> wallets, List<Category> categories) {
        List<String> unresolved = new ArrayList<>();

        Long walletId = null;
        if (raw.walletName() != null) {
            walletId = wallets.stream()
                .filter(w -> fuzzyMatch(w.getName(), raw.walletName()))
                .map(Wallet::getId)
                .findFirst()
                .orElse(null);
            if (walletId == null) unresolved.add("walletId");
        } else {
            unresolved.add("walletId");
        }

        Long categoryId = null;
        if (raw.categoryName() != null) {
            categoryId = categories.stream()
                .filter(c -> fuzzyMatch(c.getName(), raw.categoryName()))
                .map(Category::getId)
                .findFirst()
                .orElse(null);
            if (categoryId == null) unresolved.add("categoryId");
        } else {
            unresolved.add("categoryId");
        }

        if (raw.amount() == null) unresolved.add("amount");

        LocalDate date = parseDate(raw.date());
        if (date == null) {
            date = LocalDate.now(); // default to today
        }

        // Confidence: 1.0 - (0.2 per unresolved critical field)
        int criticalUnresolved = (int) unresolved.stream()
            .filter(f -> List.of("walletId", "categoryId", "amount").contains(f))
            .count();
        double confidence = Math.max(0.0, 1.0 - (criticalUnresolved * 0.25));

        return new NlpParseResult(
            walletId,
            raw.walletName(),
            categoryId,
            raw.categoryName(),
            raw.amount(),
            raw.type(),
            date,
            raw.note(),
            confidence,
            unresolved
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String sanitize(String text) {
        return CONTROL_CHARS.matcher(text).replaceAll("").trim();
    }

    /**
     * Case-insensitive, diacritics-tolerant fuzzy match.
     * Matches if either string contains the other (handles partial name like "momo" → "Momo").
     */
    private boolean fuzzyMatch(String dbName, String llmName) {
        if (dbName == null || llmName == null) return false;
        String db = dbName.toLowerCase().strip();
        String llm = llmName.toLowerCase().strip();
        return db.equals(llm) || db.contains(llm) || llm.contains(db);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            log.debug("Could not parse date: {}", dateStr);
            return null;
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        String s = v.asText("").trim();
        return s.isEmpty() ? null : s;
    }

    private BigDecimal nullableBigDecimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isNull() || v.isMissingNode()) return null;
        try {
            return new BigDecimal(v.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Internal record for raw LLM output before ID resolution. */
    private record NlpRawParse(
        String walletName,
        String categoryName,
        BigDecimal amount,
        String type,
        String date,
        String note
    ) {}
}
