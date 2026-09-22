# BÀI TẬP 5 (SS15) — XỬ LÝ LỖI VỚI GIAO DỊCH BÙ & SEMANTIC LOCK

**Bối cảnh:** Bài tập 4 đã dựng được "nhạc trưởng" (Orchestrator + State Machine) điều phối luồng đặt vé.
Nhưng khi áp vào nghiệp vụ **đặt bàn nhà hàng**, ba lỗ hổng lộ ra:

1. **Trải nghiệm người dùng:** trong lúc một khách đang đặt, không ai biết "bàn đang được giữ" ⇒ nhiều
   người lao vào tranh nhau cùng một bàn.
2. **Dữ liệu tài chính không minh bạch:** hoàn tiền bằng cách **xoá** bản ghi thanh toán ⇒ kế toán và
   kiểm toán mất dấu vết.
3. **Bù trừ chưa hệ thống:** Orchestrator chưa có logic bù trừ dựa trên trạng thái hiện tại.

Bài này mở rộng State Machine của Bài tập 4 để giải quyết cả ba.

---

## 1. KẾT NỐI VỚI BÀI TẬP 4 — MỞ RỘNG CÁI GÌ?

Bộ khung của Bài tập 4 được **giữ nguyên về kiến trúc**, chỉ đổi nghiệp vụ và bổ sung hai bảo đảm mới:

| Thành phần | Bài tập 4 | Bài tập 5 (mở rộng) |
|---|---|---|
| State Machine | `ConcertBookingStateMachineImpl` | `TableBookingStateMachineImpl` — **thêm nhánh bù trừ** `TABLE_UNAVAILABLE` |
| Trạng thái đầu | `INITIATED` | `INITIATED` (giữ nguyên) |
| Thứ tự bước | Giữ chỗ vé → thanh toán | **Giữ bàn (Semantic Lock) → thanh toán → xác nhận bàn** |
| Trạng thái trung gian | `SEAT_RESERVING` | `TABLE_RESERVING` + bàn ở trạng thái `RESERVED` |
| Trạng thái kết thúc | `BOOKING_CONFIRMED`, `CANCELLED` | `BOOKING_CONFIRMED`, `CANCELLED` (giữ nguyên) |
| Command Sender | `PaymentOrchestrationService`, `ReservationOrchestrationService` | `PaymentOrchestrationService`, `TableOrchestrationService` |
| Listener | `StateChangeListener` | `StateChangeListener` (giữ nguyên) |
| Bù trừ | Hoàn tiền + trả ghế | Hoàn tiền (**tạo bản ghi REFUND**) + trả bàn (giải phóng lock) |
| **Mới** | — | **Semantic Lock có trạng thái trung gian `RESERVED`** |
| **Mới** | — | **Auto-release sau timeout 5 phút** |
| **Mới** | — | **Sổ cái append-only + bản ghi bù trừ cho kiểm toán** |

Điểm quan trọng: **State Machine vẫn không chứa logic nghiệp vụ.** Nó không biết bàn nào trống, không
biết trừ bao nhiêu tiền. Nó chỉ chuyển trạng thái và gửi Command:

```java
tableService.reserveTable(tableNumber, bookingId);        // Command -> Table Service
moveTo(bookingId, BookingState.PAYMENT_PENDING, BookingEvent.TABLE_RESERVED);   // chuyen trang thai
```

### Một điều chỉnh so với bản phác thảo trong đề

Đề bài ở phần gợi ý có `case TABLE_RESERVED:` (coi `TABLE_RESERVED` là một **trạng thái**), nhưng phần
**"Kết quả mong muốn"** lại in ra:

```
[Orchestrator] State: TABLE_RESERVING -> Event: TABLE_RESERVED -> New State: PAYMENT_PENDING
```

tức `TABLE_RESERVED` chỉ là **sự kiện**, còn trạng thái mới là `PAYMENT_PENDING`. Hai chỗ này mâu thuẫn.
Em **ưu tiên bám đúng log trong "Kết quả mong muốn"** (vì đó là thứ được đối chiếu khi chấm), nên:

- `TABLE_RESERVED` là **event** — không đưa vào enum `BookingState`.
- `PROCESS_PAYMENT` (có trong bản phác thảo) **không dùng** vì log mong đợi không có transition nào mang
  tên sự kiện này; đưa vào sẽ thành hằng số chết trong enum.

Nếu giảng viên muốn tách `TABLE_RESERVED` thành một trạng thái riêng (rõ ràng hơn về mặt mô hình), chỉ cần
thêm 1 hằng số vào enum và tách 1 dòng `moveTo(...)` — không phải sửa gì ở các service.

---

## 2. SEMANTIC LOCK — VÌ SAO CẦN TRẠNG THÁI `RESERVED`?

### 2.1. Vấn đề của việc đi thẳng `AVAILABLE → BOOKED`

Nếu bàn chỉ có hai trạng thái, trong lúc Saga đang chạy (đã giữ bàn nhưng chưa thu tiền xong), bàn sẽ
hiện là gì?

| Phương án | Điều xảy ra với khách khác | Hệ quả |
|---|---|---|
| Giữ `AVAILABLE` cho tới khi xong | Tưởng bàn còn trống, cùng đặt | **Dirty read** — hai khách cùng tưởng mình đặt được, một người sẽ bị từ chối ở phút cuối |
| Chuyển thẳng `BOOKED` | Tưởng bàn đã bán | Mất bàn oan khi Saga thất bại và phải rollback; khách khác bỏ đi tìm chỗ khác |
| **`RESERVED` (trung gian)** | Biết **"bàn đang được giữ"** | Không ai lao vào nữa, nhưng nếu Saga hỏng thì bàn **tự trả về** |

`RESERVED` là một lời hứa **có thời hạn và có thể hoàn tác**: nó vừa đủ chặt để chặn tranh chấp, vừa đủ
lỏng để không "cháy bàn" khi giao dịch đổ vỡ.

### 2.2. Lợi ích cho trải nghiệm người dùng

Chính vì `RESERVED` là trạng thái **nhìn thấy được**, UI mới nói được câu mà đề yêu cầu:

```
GET /api/tables/B7
{ "tableNumber": "B7", "status": "RESERVED",
  "message": "Bàn đang được giữ", "heldBy": "REST-2026-101", "heldSeconds": 12 }
```

Ba mức thông tin khác nhau thay vì một câu "hết bàn" vô nghĩa:

| Status | Message trả cho UI | Khách hiểu là |
|---|---|---|
| `AVAILABLE` | "Bàn trống - có thể đặt ngay" | Đặt được luôn |
| `RESERVED` | **"Bàn đang được giữ"** | Có người đang đặt, thử lại sau |
| `BOOKED` | "Bàn đã được đặt" | Tìm bàn khác |

### 2.3. Cài đặt: cửa sổ chuyển trạng thái là điểm chốt

`TableReservationService.reserveTable` kiểm tra trạng thái **và** đổi trạng thái trong cùng một khối
`synchronized`, nên giữa hai thao tác không có khe hở cho giao dịch khác lọt vào:

```java
if (table.getStatus() != TableStatus.AVAILABLE) {        // kiem tra
    log.error("[TableService] ERROR: Table {} is already taken by another booking!", tableNumber);
    throw new TableUnavailableException(tableNumber);
}
table.setStatus(TableStatus.RESERVED);                   // chiem lock - khong co khe ho o giua
table.setReservedBy(bookingId);
```

Kết quả đúng như KB5 trong bằng chứng: khách thứ hai đặt trùng bàn **thất bại ngay từ bước 1**, không
được đi tiếp, và bàn của khách thứ nhất không bị đụng tới.

### 2.4. Tự động giải phóng bàn quá hạn (timeout 5 phút)

Semantic lock giải quyết tranh chấp nhưng tạo ra rủi ro mới: nếu khách **bỏ ngang** giữa lúc giữ bàn,
bàn sẽ bị khoá vĩnh viễn. Vì vậy mỗi lần giữ bàn đều ghi `reservedAt`, và có một job quét định kỳ:

```java
@Scheduled(fixedDelayString = "${saga.table.auto-release-interval-ms:300000}")   // 5 phut
public void autoReleaseExpiredHolds() {
    Instant deadline = Instant.now().minus(holdTimeout);   // holdTimeout = 5 phut
    tables.values().stream()
            .filter(table -> table.getStatus() == TableStatus.RESERVED)
            .filter(table -> table.getReservedAt().isBefore(deadline))
            .forEach(table -> { releaseTable(table.getTableNumber()); /* log auto-release */ });
}
```

Cấu hình ở `application.yml`: `saga.table.hold-timeout-ms: 300000`. KB7 trong bằng chứng chứng minh cơ chế
này chạy (test rút timeout về 0 để mô phỏng "đã quá 5 phút" rồi gọi job quét).

---

## 3. COMPENSATING TRANSACTION — VÌ SAO TẠO `REFUND` MÀ KHÔNG XOÁ `PAYMENT`?

### 3.1. Nguyên tắc: giao dịch bù là **đảo ngược nghiệp vụ**, không phải **xoá dấu vết**

Kế toán ghi kép không bao giờ xoá một bút toán. Muốn huỷ một khoản ghi nợ, người ta ghi thêm một khoản
**ghi có** đối ứng. Sổ vẫn còn **cả hai** dòng, và tổng thì bằng 0. Đó đúng là cách `PaymentProcessingService`
được viết ở đây:

```java
public long refundPayment(String bookingId, long amount) {
    transactionRepository.save(new TransactionRecord(       // THEM ban ghi moi
            bookingId, amount, TransactionType.REFUND, "PROCESSED", Instant.now()));
    ...
}
```

Và `TransactionRepository` **cố ý không có** method `update`/`delete` — chỉ có `save`, `findByBookingId`,
`findByType`, `findAll`, `netAmountOf`. Muốn xoá bản ghi cũ cũng không có API để xoá.

### 3.2. Vì sao điều này quan trọng cho kiểm toán

| Nếu XOÁ bản ghi PAYMENT | Nếu TẠO bản ghi REFUND (cách đã làm) |
|---|---|
| Không trả lời được "hôm đó đã thu tiền chưa?" | Trả lời được: có, đã thu, và đã hoàn |
| Không đối chiếu được với sao kê ngân hàng (có giao dịch thật đã xảy ra) | Đối chiếu được: 1 giao dịch ra, 1 giao dịch vào |
| Mất dấu thời điểm, ai/khi nào gây ra việc hoàn tiền | Có `timestamp` của cả hai bút toán ⇒ dựng lại được dòng thời gian |
| Không phát hiện được lạm dụng (nhân viên hoàn tiền khống) | Số dư ròng bất thường là dấu hiệu bất thường |
| Tranh chấp khách hàng: không có bằng chứng | Có bằng chứng hai chiều: đã trừ, đã hoàn |

Bằng chứng KB2 cho thấy sau khi bù trừ, sổ cái **vẫn giữ 2 dòng** và tổng ròng bằng 0:

```
>>> [KB2 AUDIT TRAIL] so but toan = 2
    PAYMENT -500000 VND status=PROCESSED booking=REST-2026-101
    REFUND  +500000 VND status=PROCESSED booking=REST-2026-101
```

### 3.3. Bù trừ phải **giới hạn trong phạm vi**: chỉ bù cái mình đã làm

Đây là bài học đắt nhất, và State Machine thực thi nó bằng hai cờ nghiệp vụ được bật **ngay tại thời điểm
hành động thành công**:

```java
private void compensate(BookingTransaction transaction) {
    if (transaction.isPaymentSettled()) {          // CHI hoan tien neu DA tru duoc tien
        log.info("[Orchestrator] Initiating Compensation: Calling refundPayment for booking {}...", bookingId);
        paymentService.refundPayment(bookingId, transaction.getDepositAmount());
    }
    if (transaction.isTableHeld()) {               // CHI tra ban neu CHINH SAGA NAY da giu duoc ban
        tableService.releaseTable(tableNumber);
    }
    bookingService.markCancelled(bookingId);
}
```

Bảng đối chiếu hành vi bù trừ theo trạng thái lúc lỗi:

| Trạng thái lúc lỗi | `paymentSettled` | `tableHeld` | Hành động bù trừ | Bằng chứng |
|---|---|---|---|---|
| `TABLE_RESERVING` (bàn đã bị khách khác giữ) | false | false | Không hoàn tiền, **không đụng vào bàn** | KB5 |
| `PAYMENT_PENDING` (thanh toán bị từ chối) | false | true | **Trả bàn**, không hoàn tiền | KB4 |
| `BOOKING_CONFIRMING` (`TABLE_UNAVAILABLE`) | **true** | **true** | **Hoàn tiền (tạo REFUND) + trả bàn** | KB1 |

Nếu bù trừ vô điều kiện, ở KB5 hệ thống sẽ **trả bàn của khách thứ nhất về AVAILABLE** — đúng lỗi
"bù vượt phạm vi" đã gặp ở bài trước. Chi tiết tinh tế hơn ở KB5: bàn `B8` đang `BOOKED` vì thuộc về
giao dịch khác, nên câu log cuối phải phản ánh đúng sự thật:

```
[Final State] Table B8 is BOOKED (this booking never held the lock).
```

chứ không được nói "(semantic lock released)" — vì saga này chưa từng giữ lock đó.

---

## 4. LUỒNG CHUYỂN ĐỔI TRẠNG THÁI

### 4.1. States và Events

| State | Ý nghĩa |
|---|---|
| `INITIATED` | Vừa nhận yêu cầu đặt bàn |
| `TABLE_RESERVING` | Đang gọi Table Service giữ bàn (Semantic Lock) |
| `PAYMENT_PENDING` | Đang gọi Payment Service trừ tiền đặt cọc |
| `PAYMENT_COMPLETED` | Đã trừ tiền thành công |
| `BOOKING_CONFIRMING` | Đang gọi Table Service xác nhận bàn |
| `BOOKING_CONFIRMED` | Đặt bàn hoàn tất |
| `CANCELLED` | Đã huỷ — kích hoạt bù trừ |

| Event | Nguồn → Đích |
|---|---|
| `RESERVE_TABLE` | `INITIATED` → `TABLE_RESERVING` |
| `TABLE_RESERVED` | `TABLE_RESERVING` → `PAYMENT_PENDING` |
| `TABLE_UNAVAILABLE` | `TABLE_RESERVING` → `CANCELLED` **và** `BOOKING_CONFIRMING` → `CANCELLED` |
| `PAYMENT_SUCCESS` | `PAYMENT_PENDING` → `PAYMENT_COMPLETED` |
| `PAYMENT_FAILED` | `PAYMENT_PENDING` → `CANCELLED` |
| `CONFIRM_BOOKING` | `PAYMENT_COMPLETED` → `BOOKING_CONFIRMING` |
| `BOOKING_SUCCESS` | `BOOKING_CONFIRMING` → `BOOKING_CONFIRMED` |

Lưu ý `TABLE_UNAVAILABLE` xuất hiện ở **hai** trạng thái nguồn — cùng một sự kiện nhưng nghĩa khác nhau
tuỳ vị trí. Đây chính là giá trị của State Machine: bảng chuyển trạng thái mới là nơi định nghĩa "bàn đã
bị lấy" có ý nghĩa gì ở từng thời điểm.

### 4.2. Sơ đồ

```
                        RESERVE_TABLE                     TABLE_RESERVED
      ┌───────────┐ ────────────────────► ┌───────────────────┐ ──────────────────► ┌───────────────────┐
      │ INITIATED │                       │  TABLE_RESERVING  │                     │  PAYMENT_PENDING  │
      └───────────┘                       └───────────────────┘                     └───────────────────┘
                                                    │                                        │
                                          TABLE_UNAVAILABLE                     PAYMENT_SUCCESS │  │ PAYMENT_FAILED
                                       (ban da bi nguoi khac giu)                                │  │ (the bi tu choi)
                                                    │                                            ▼  │
                                                    │                              ┌──────────────────────┐
                                                    │                              │  PAYMENT_COMPLETED   │
                                                    │                              └──────────────────────┘
                                                    │                                         │
                                                    │                                CONFIRM_BOOKING
                                                    │                                         ▼
                                                    │                              ┌──────────────────────┐
                                                    │                              │  BOOKING_CONFIRMING  │
                                                    │                              └──────────────────────┘
                                                    │                                  │            │
                                                    │                    BOOKING_SUCCESS│            │TABLE_UNAVAILABLE
                                                    │                                  ▼            │(ban da bi nguoi
                                                    │                    ┌──────────────────────┐   │ khac chot truoc)
                                                    │                    │  BOOKING_CONFIRMED   │   │
                                                    │                    └──────────────────────┘   │
                                                    ▼                                               ▼
                                          ┌──────────────────────────────────────────────────────────────┐
                                          │                        CANCELLED                             │
                                          │  BU TRU (theo trang thai hien tai):                          │
                                          │   - paymentSettled? -> refundPayment()  (tao ban ghi REFUND)  │
                                          │   - tableHeld?      -> releaseTable()   (RESERVED -> AVAILABLE)│
                                          │   - bookingService.markCancelled()                            │
                                          └──────────────────────────────────────────────────────────────┘
```

---

## 5. HƯỚNG DẪN CÀI ĐẶT VÀ CHẠY

**Yêu cầu:** JDK 17+. Gradle Wrapper 9.5.1 đã kèm sẵn, không cần cài Gradle.

### 5.1. Chạy test (khuyến nghị)

```bash
cd Session15/Ex05/table-orchestrator-state-machine
./gradlew test          # Windows: gradlew.bat test
```

### 5.2. Chạy thật bằng REST

```bash
./gradlew :orchestrator-service:bootRun        # port 8200
```

Gọi đúng dữ liệu đầu vào của đề (trạng thái `FAILED_ALREADY_TAKEN`):

```bash
curl -X POST http://localhost:8200/api/bookings \
  -H "Content-Type: application/json" \
  -d '{
        "bookingId": "REST-2026-101",
        "tableNumber": "B7",
        "customerId": "VIP-2024",
        "customerEmail": "rika@email.com",
        "depositAmount": 500000,
        "scenario": "FAILED_ALREADY_TAKEN"
      }'
```

Các endpoint phụ trợ:

```bash
curl http://localhost:8200/api/tables/B7            # UI: "Bàn đang được giữ" khi RESERVED
curl http://localhost:8200/api/payments/ledger/REST-2026-101   # doi soat so cai (audit trail)
```

Hai service thành phần cũng chạy độc lập:

```bash
./gradlew :table-service:bootRun               # port 8201
./gradlew :payment-service:bootRun             # port 8202
curl -X POST http://localhost:8201/api/tables/... # xem TableController
curl -X POST http://localhost:8202/api/payments/charge \
  -H "Content-Type: application/json" -d '{"bookingId":"REST-2026-101","amount":500000}'
```

Giá trị hợp lệ của `scenario`: `SUCCESS`, `FAILED_ALREADY_TAKEN`, `PAYMENT_FAILED`
(bỏ trống thì mặc định là `SUCCESS`).

---

## 6. KẾT QUẢ CHẠY THỬ VỚI DỮ LIỆU ĐẦU VÀO CỦA ĐỀ

`./gradlew test` → **7/7 test PASSED, BUILD SUCCESSFUL in 19s**.

Với `bookingId = REST-2026-101`, `tableNumber = B7`, `depositAmount = 500000`,
`scenario = FAILED_ALREADY_TAKEN`, log hệ thống in ra **đúng từng ký tự** 16 dòng mà đề yêu cầu
(xem đầy đủ ở `Ex05_TestEvidence.txt`):

```
[Orchestrator] State: INITIATED -> Event: RESERVE_TABLE -> New State: TABLE_RESERVING
[TableService] Table B7: AVAILABLE -> RESERVED (Semantic Lock acquired)
[TableService] Table B7 reserved for booking REST-2026-101
[Orchestrator] State: TABLE_RESERVING -> Event: TABLE_RESERVED -> New State: PAYMENT_PENDING
[PaymentService] Payment of 500000 VND processed for booking REST-2026-101
[Orchestrator] State: PAYMENT_PENDING -> Event: PAYMENT_SUCCESS -> New State: PAYMENT_COMPLETED
[Orchestrator] State: PAYMENT_COMPLETED -> Event: CONFIRM_BOOKING -> New State: BOOKING_CONFIRMING
[TableService] ERROR: Table B7 is already taken by another booking!
[Orchestrator] Table reservation failed for B7 (Already taken).
[Orchestrator] State: BOOKING_CONFIRMING -> Event: TABLE_UNAVAILABLE -> New State: CANCELLED
[Orchestrator] Initiating Compensation: Calling refundPayment for booking REST-2026-101...
[RefundActivity] Refund of 500000 VND processed for booking REST-2026-101.
[Transaction] Created REFUND record: +500000 VND for booking REST-2026-101 (Audit Trail)
[TableService] Table B7: RESERVED -> AVAILABLE (Semantic Lock released)
[BookingService] Booking REST-2026-101 updated to CANCELLED.
[Final State] Table B7 is now AVAILABLE (semantic lock released).
```

Test **bắt log thật** của mọi thành phần bằng một Logback `ListAppender` gắn vào root logger rồi so khớp
bằng `containsExactly`, nên kết quả không thể là "tự thuật lại".

| Test | Kịch bản | Kết quả |
|---|---|---|
| KB1 | `FAILED_ALREADY_TAKEN` | Khớp **16/16** dòng log; `CANCELLED`; bàn về `AVAILABLE` |
| KB2 | Kiểm toán sổ cái | 2 bút toán: PAYMENT còn nguyên + REFUND mới; số dư ròng = 0 |
| KB3 | Happy path | `BOOKING_CONFIRMED`; bàn `BOOKED`; chỉ 1 bút toán, không có REFUND |
| KB4 | Thanh toán thất bại | `CANCELLED`; trả bàn; **không** hoàn tiền (sổ cái rỗng) |
| KB5 | Bàn đã bị khách khác giữ | Từ chối ngay bước 1; không đụng vào bàn của người khác |
| KB6 | Semantic Lock + UI | `RESERVED`, `heldBy=REST-2026-101`, message "Bàn đang được giữ" |
| KB7 | Quá hạn giữ bàn | Job `@Scheduled` tự trả bàn về `AVAILABLE` |

---

## 7. GHI CHÚ PHẠM VI VÀ HƯỚNG PHÁT TRIỂN

- **Sổ cái đang in-memory** (`CopyOnWriteArrayList` trong `TransactionRepository`). Điều được chứng minh
  là **tính chất** quan trọng: append-only, không xoá, có bản ghi bù. Ở môi trường thật chỉ cần đổi
  `TransactionRepository` thành Spring Data JPA + bảng `transaction` với ràng buộc **không cho UPDATE/DELETE**
  (hoặc dùng trigger chặn), `id` + `timestamp` do DB sinh, thêm cột `created_by` để truy vết.
- **Lời gọi service đang in-process** qua `TableOrchestrationService` / `PaymentOrchestrationService`.
  Đây là **điểm nối** đã tách sẵn: chuyển sang microservice thật chỉ cần thay thân hai lớp này bằng
  `RestClient`/Feign tới port 8201/8202, **không sửa dòng nào trong State Machine**.
- **Trường `scenario` trong request là công cụ mô phỏng**, không phải thiết kế production. Nó dựng sẵn
  điều kiện "bàn đã bị khách khác chốt" để demo được luồng bù trừ mà không cần chạy hai khách đồng thời.
  Trong hệ thống thật, điều kiện đó do khách hàng khác tạo ra và Table Service phát hiện khi xác nhận.
- **Hướng mở rộng:** thêm bước gửi email xác nhận ⇒ chỉ cần thêm 1 state + 1 event + 1 transition, không
  phải sửa các service đã có. Muốn chịu tải cao hơn, thay `synchronized` bằng khoá ở tầng DB
  (`SELECT ... FOR UPDATE` hoặc optimistic locking với cột `version`).
