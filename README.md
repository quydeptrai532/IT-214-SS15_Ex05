# BÀI TẬP 5 (SS15) — XỬ LÝ LỖI VỚI GIAO DỊCH BÙ & SEMANTIC LOCK

Xem phân tích tại `Ex05_Analysis.md`, bằng chứng chạy test tại `Ex05_TestEvidence.txt`.

## Tóm tắt

Mở rộng Orchestrator State Machine của Bài tập 4 sang nghiệp vụ **đặt bàn nhà hàng**, bổ sung hai bảo đảm:

- **Semantic Lock**: bàn đi qua trạng thái trung gian `AVAILABLE → RESERVED → BOOKED`. Nhờ `RESERVED`,
  khách khác **thấy được** "bàn đang được giữ" thay vì cùng lao vào đặt (chống dirty read).
  Bàn `RESERVED` quá **5 phút** sẽ được job `@Scheduled` tự động trả về `AVAILABLE`.
- **Compensating Transaction**: khi bàn đã bị khách khác chốt (`TABLE_UNAVAILABLE`), Orchestrator chuyển
  `CANCELLED`, **tạo bản ghi REFUND** (ghi có) — **không xoá** bản ghi `PAYMENT` (ghi nợ) — rồi giải phóng
  Semantic Lock. Sổ cái append-only phục vụ kiểm toán.

Bù trừ chỉ đảo ngược **những gì chính saga này đã làm** (`paymentSettled`, `tableHeld`), không bù vượt phạm vi.

- **States**: `INITIATED, TABLE_RESERVING, PAYMENT_PENDING, PAYMENT_COMPLETED, BOOKING_CONFIRMING, BOOKING_CONFIRMED, CANCELLED`
- **Events**: `RESERVE_TABLE, TABLE_RESERVED, TABLE_UNAVAILABLE, PAYMENT_SUCCESS, PAYMENT_FAILED, CONFIRM_BOOKING, BOOKING_SUCCESS`

## Cấu trúc

```
Ex05/
├── Ex05_Analysis.md
├── Ex05_TestEvidence.txt
├── README.md
└── table-orchestrator-state-machine/
    ├── orchestrator-service/
    │   └── src/main/java/com/example/orchestrator/
    │       ├── machine/TableBookingStateMachineImpl.java   # luong chuyen trang thai + bu tru
    │       ├── model/BookingState.java, BookingEvent.java, BookingScenario.java, BookingTransaction.java
    │       ├── listener/StateChangeListener.java
    │       ├── service/TableOrchestrationService.java      # Command Sender
    │       ├── service/PaymentOrchestrationService.java    # Command Sender
    │       ├── service/BookingService.java                 # cap nhat trang thai don
    │       ├── service/TableBookingSagaOrchestrator.java   # diem vao Saga
    │       ├── controller/BookingController.java           # POST /api/bookings
    │       └── src/test/java/.../TableBookingSagaTest.java # 7 test
    ├── table-service/          # Semantic Lock + auto-release 5 phut + API trang thai ban
    └── payment-service/        # So cai append-only: PAYMENT / REFUND (audit trail)
```

## Chạy test

```bash
cd table-orchestrator-state-machine
./gradlew test          # Windows: gradlew.bat test
```

## Chạy thật

```bash
./gradlew :orchestrator-service:bootRun        # port 8200

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

curl http://localhost:8200/api/tables/B7                       # UI hien thi trang thai ban
curl http://localhost:8200/api/payments/ledger/REST-2026-101   # doi soat so cai
```

`scenario`: `SUCCESS` | `FAILED_ALREADY_TAKEN` | `PAYMENT_FAILED` (bỏ trống → `SUCCESS`).

## Kết quả

```
>>> [KB1 FAILED_ALREADY_TAKEN] finalState=CANCELLED | tableStatus=AVAILABLE | so but toan=2
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

>>> [KB2 AUDIT TRAIL]    PAYMENT -500000 VND  +  REFUND +500000 VND  => so du rong = 0
>>> [KB3 HAPPY PATH]     finalState=BOOKING_CONFIRMED | tableStatus=BOOKED | so but toan=1
>>> [KB4 PAYMENT FAILED] finalState=CANCELLED | tra ban, khong hoan tien
>>> [KB5 BAN DA BI GIU]  finalState=CANCELLED | khong cuop ban cua nguoi khac
>>> [KB6 SEMANTIC LOCK]  B7 status=RESERVED | message="Bàn đang được giữ"
>>> [KB7 AUTO RELEASE]   B7 status=AVAILABLE sau khi qua han giu ban
```

`7/7 test PASSED — BUILD SUCCESSFUL in 19s`

> **Ghi chú phạm vi:** sổ cái và trạng thái bàn lưu **in-memory**; gọi service **in-process** qua lớp
> `*OrchestrationService` (điểm nối đã tách sẵn). Production: đổi `TransactionRepository` sang JPA với bảng
> chỉ-được-thêm, thay thân 2 lớp `*OrchestrationService` bằng `RestClient`, thay `synchronized` bằng khoá DB.
