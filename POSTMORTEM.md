# Post-mortem: 11 lỗi làm BE fail 6 tiếng liên tục
**Ngày:** 30/03/2026 | **Version affected:** v0.4.0 → v0.4.1
**Repo:** chinhnt-portfolio-v2/portfolio-platform

---

## 🔴 Root Causes & Lessons Learned

---

### 1. BOM (UTF-8 with Signature) trong Java source files

**Lỗi:** 2 file (`TransferService.java`, `TransactionResponse.java`) chứa ký tự `\uFEFF` ở đầu file.

**Hậu quả:** `javac` compile thành công (bỏ qua BOM), nhưng runtime lỗi `NoSuchMethodError` → crash.

**Bài học:**
```bash
# Fix ngay: xóa BOM bằng cách rewrite file
# Kiểm tra BOM: hex dump -n 3 file.java
# Nếu thấy EF BB BF → có BOM
```

**Prevention:** Thêm `.editorconfig` vào repo (xem file `.editorconfig` trong root).

---

### 2. Type mismatch: `double` vs `null` trong record

**Lỗi:** `UpdateBudgetRequest` dùng `@Positive double` nhưng field `monthlyLimit` có thể null → "bad operand types".

**Bài học:**
```java
// ✅ ĐÚNG: dùng wrapper type cho nullable
@Positive(message = "...") Double monthlyLimit

// ❌ SAI: primitive type không thể null
@Positive double monthlyLimit
```

---

### 3. Tên field không khớp giữa constructor và biến class

**Lỗi:** `RecurringService` có field `recurringRuleRepository` nhưng constructor nhận param `repository`.

**Bài học:**
```java
// ✅ Dùng Lombok để tránh lỗi thủ công
@RequiredArgsConstructor
@Service
public class RecurringService {
    private final RecurringRuleRepository repository;
}

// Hoặc đặt tên field = tên param trong constructor
```

---

### 4. Record field order không khớp với constructor call

**Lỗi:** `TransactionResponse` record có 15 fields, gọi constructor trong `toResponse()` chỉ truyền 14 args → `wrong args count`.

**Bài học:**
```java
// ✅ Luôn dùng named factory method thay vì gọi constructor trực tiếp
public static TransactionResponse from(Transaction t) { ... }

// Hoặc IDE auto-complete khi gọi constructor
```

---

### 5. Duplicate Flyway migration version

**Lỗi:** 2 file `V6__*.sql` cùng version → Flyway crash ngay khi khởi động.

**Bài học:**
```bash
# ✅ QUY TẮC: Mỗi feature mới → migration version mới, không reuse version cũ
V6__feature_a.sql          # first
V7__feature_b.sql          # second — KHÔNG bao giờ V6

# Nếu cần thêm vào V6 đã có:
V6__feature_a.sql          # V6 đã tồn tại
V6_1__feature_a_extension.sql  # ✅ OK: underscore version

# Hoặc:
V7__feature_b.sql          # ✅ OK: version mới
```

---

### 6. Flyway checksum mismatch (do sửa migration đã applied)

**Lỗi:** V6 đã applied (checksum `1827004417`) nhưng file V6 trong repo bị thay đổi nội dung → checksum mới khác → Flyway từ chối migrate.

**Bài học:**
```
❌ KHÔNG BAO GIỜ sửa migration file đã applied lên production
✅ Nếu cần fix: tạo migration mới (V8, V9...)
✅ "Applied" trong Flyway history ≠ table tồn tại (migration có thể fail giữa chừng)
```

---

### 7. Thiếu `actions/checkout` trong deploy job

**Lỗi:** Deploy job chỉ chạy `gcloud run deploy`, không checkout code → dùng cached artifact từ commit cũ.

**Bài học:**
```yaml
# ✅ MỌI job cần code đều phải có actions/checkout@v4
deploy:
  needs: build-and-push
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4    # ← BẮT BUỘC
    - uses: google-github-actions/auth@v2
    - run: gcloud run deploy ...
```

---

### 8. `@JoinColumn` camelCase thay vì snake_case

**Lỗi:** `RecurringRule` dùng `@JoinColumn(name = "walletId")` nhưng DB column là `wallet_id`.

**Bài học:**
```java
// ✅ QUY TẮC: DB columns luôn snake_case
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "wallet_id", insertable = false, updatable = false)
private Wallet wallet;

// ❌ SAI: dùng camelCase
@JoinColumn(name = "walletId")
```

---

### 9. Duplicate mapping: 2 logical names cho 1 DB column (PHỔ BIẾN NHẤT)

**Lỗi:** `Budget.categoryId` field không có `@Column(name = "category_id")` → Hibernate tự infer `categoryId → category_id`. Đồng thời `@JoinColumn(name = "category_id")` → 1 column = 2 logical names → `DuplicateMappingException`.

**Bài học:**
```java
// ✅ MỌI field có column name khác với field name đều cần @Column(name = "...")
// Cả primitive fields lẫn FK fields!

@Entity
@Table(name = "budgets")
public class Budget {
    @Column(name = "user_id")           // ← BẮT BUỘC cho UUID fields
    private UUID userId;

    @Column(name = "category_id")       // ← BẮT BUỘC cho FK fields
    private Long categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id",
                insertable = false, updatable = false)
    private Category category;
}
```

---

### 10. Docker layer cache reuse JAR cũ

**Lỗi:** Mỗi commit fix code nhưng Docker build vẫn cache layers từ commit cũ → image mới không chứa code fix.

**Bài học:**
```yaml
# ✅ Dùng cache key = commit SHA để mỗi commit có image riêng
- uses: docker/build-push-action@v6
  with:
    cache-from: type=gha,scope=NAME,key=NAME-${{ github.sha }}
    cache-to: type=gha,mode=max,scope=NAME,key=NAME-${{ github.sha }}

# ✅ HOẶC tắt cache hoàn toàn khi fix critical bug
- uses: docker/build-push-action@v6
  with:
    push: true
    tags: ...
    # Không có cache-from/cache-to
```

---

### 11. Migration đã applied nhưng tables không tồn tại

**Lỗi:** Production DB đã ghi Flyway history V6/V7, nhưng tables không tồn tại (do V6 fail giữa chừng).

**Bài học:**
```sql
-- ✅ Dùng CREATE TABLE IF NOT EXISTS là backup cuối cùng
CREATE TABLE IF NOT EXISTS budgets (...);

-- Kiểm tra trước khi conclude:
-- SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;
-- SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
```

---

## ✅ Checklist trước mỗi Push BE

```markdown
□ Java files: UTF-8 without BOM (kiểm tra hex: EF BB BF ở đầu file)
□ Wrapper types cho nullable fields (@Positive Double, Integer, Long)
□ @Column(name = "snake_case") cho MỌI field có DB column name khác
□ @JoinColumn(name = "snake_case_db_column", referencedColumnName = "id") cho FK
□ Migration version: DUY NHẤT, không duplicate với file đã tồn tại
□ ĐÃ applied: KHÔNG sửa migration cũ, luôn tạo V_ mới
□ .github/workflows: MỌI job đều có actions/checkout@v4
□ Critical fix: verify /actuator/health SAU khi deploy trước khi kết luận
```

---

## 🔧 File cấu hình được thêm sau incident

- `.editorconfig` — chuẩn hóa encoding (UTF-8 without BOM, LF)
- `V8__create_budget_recurring_push_tables.sql` — backup migration (CREATE TABLE IF NOT EXISTS)
- `src/main/resources/application-prod.yml` — flyway: out-of-order: true, validate-on-migrate: false

---

**6 tiếng debug → 1 bài học: "Hibernate không đoán được ý bạn — annotate rõ ràng từ đầu."**
**Version:** v0.4.1 | **Date:** 2026-03-30
