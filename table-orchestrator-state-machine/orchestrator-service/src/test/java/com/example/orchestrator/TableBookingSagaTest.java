package com.example.orchestrator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.orchestrator.dto.BookingRequest;
import com.example.orchestrator.dto.BookingResult;
import com.example.orchestrator.model.BookingScenario;
import com.example.orchestrator.service.BookingService;
import com.example.orchestrator.service.TableBookingSagaOrchestrator;
import com.example.payment.model.TransactionRecord;
import com.example.payment.model.TransactionType;
import com.example.payment.repository.TransactionRepository;
import com.example.payment.service.PaymentProcessingService;
import com.example.table.model.TableStatus;
import com.example.table.service.TableReservationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TableBookingSagaTest {

    private static final String TABLE = "B7";
    private static final String BOOKING = "REST-2026-101";
    private static final long DEPOSIT = 500000L;

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @Autowired
    private TableBookingSagaOrchestrator orchestrator;

    @Autowired
    private TableReservationService tableReservationService;

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private BookingService bookingService;

    /**
     * Gan appender thu log SAU khi Spring Boot da khoi tao xong LoggingSystem
     * (neu gan truoc, Boot se reset Logback va go mat appender).
     */
    @BeforeEach
    void setUp() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        tableReservationService.reset();
        paymentProcessingService.reset();
        bookingService.clear();
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(appender);
    }

    /** Lay log that su cua he thong (moi thanh phan) theo dung thu tu phat sinh. */
    private List<String> sagaLogs() {
        return new ArrayList<>(appender.list).stream()
                .filter(event -> event.getLoggerName() != null && event.getLoggerName().startsWith("com.example"))
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("["))
                .toList();
    }

    private BookingRequest request(String bookingId, String tableNumber, BookingScenario scenario) {
        return new BookingRequest(bookingId, tableNumber, "VIP-2024", "rika@email.com", DEPOSIT, scenario);
    }

    @Test
    void failedAlreadyTaken_kichHoatCompensationVaGhiNhanRefund() {
        BookingResult result = orchestrator.execute(request(BOOKING, TABLE, BookingScenario.FAILED_ALREADY_TAKEN));

        List<String> logs = sagaLogs();
        System.out.println(">>> [KB1 FAILED_ALREADY_TAKEN] finalState=" + result.finalState()
                + " | tableStatus=" + result.tableStatus()
                + " | bookingStatus=" + result.bookingStatus()
                + " | so but toan=" + result.auditTrail().size());
        logs.forEach(line -> System.out.println("    " + line));

        assertThat(logs).containsExactly(
                "[Orchestrator] State: INITIATED -> Event: RESERVE_TABLE -> New State: TABLE_RESERVING",
                "[TableService] Table B7: AVAILABLE -> RESERVED (Semantic Lock acquired)",
                "[TableService] Table B7 reserved for booking REST-2026-101",
                "[Orchestrator] State: TABLE_RESERVING -> Event: TABLE_RESERVED -> New State: PAYMENT_PENDING",
                "[PaymentService] Payment of 500000 VND processed for booking REST-2026-101",
                "[Orchestrator] State: PAYMENT_PENDING -> Event: PAYMENT_SUCCESS -> New State: PAYMENT_COMPLETED",
                "[Orchestrator] State: PAYMENT_COMPLETED -> Event: CONFIRM_BOOKING -> New State: BOOKING_CONFIRMING",
                "[TableService] ERROR: Table B7 is already taken by another booking!",
                "[Orchestrator] Table reservation failed for B7 (Already taken).",
                "[Orchestrator] State: BOOKING_CONFIRMING -> Event: TABLE_UNAVAILABLE -> New State: CANCELLED",
                "[Orchestrator] Initiating Compensation: Calling refundPayment for booking REST-2026-101...",
                "[RefundActivity] Refund of 500000 VND processed for booking REST-2026-101.",
                "[Transaction] Created REFUND record: +500000 VND for booking REST-2026-101 (Audit Trail)",
                "[TableService] Table B7: RESERVED -> AVAILABLE (Semantic Lock released)",
                "[BookingService] Booking REST-2026-101 updated to CANCELLED.",
                "[Final State] Table B7 is now AVAILABLE (semantic lock released).");

        assertThat(result.finalState()).isEqualTo("CANCELLED");
        assertThat(result.bookingStatus()).isEqualTo("CANCELLED");
        assertThat(result.tableStatus()).isEqualTo(TableStatus.AVAILABLE.name());
        assertThat(tableReservationService.holderOf(TABLE)).isNull();
    }

    @Test
    void refundTaoBanGhiMoi_khongXoaBanGhiPayment() {
        orchestrator.execute(request(BOOKING, TABLE, BookingScenario.FAILED_ALREADY_TAKEN));

        List<TransactionRecord> ledger = transactionRepository.findByBookingId(BOOKING);
        System.out.println(">>> [KB2 AUDIT TRAIL] so but toan = " + ledger.size());
        ledger.forEach(record -> System.out.println("    " + record.type() + " " + record.signedAmount()
                + " status=" + record.status() + " booking=" + record.bookingId()));

        assertThat(ledger).hasSize(2);
        assertThat(ledger.get(0).type()).isEqualTo(TransactionType.PAYMENT);
        assertThat(ledger.get(0).amount()).isEqualTo(DEPOSIT);
        assertThat(ledger.get(1).type()).isEqualTo(TransactionType.REFUND);
        assertThat(ledger.get(1).amount()).isEqualTo(DEPOSIT);
        assertThat(transactionRepository.netAmountOf(BOOKING)).isZero();
        assertThat(transactionRepository.findAll()).hasSize(2);
    }

    @Test
    void happyPath_datBanThanhCong() {
        BookingResult result = orchestrator.execute(request(BOOKING, TABLE, BookingScenario.SUCCESS));

        List<String> logs = sagaLogs();
        System.out.println(">>> [KB3 HAPPY PATH] finalState=" + result.finalState()
                + " | tableStatus=" + result.tableStatus()
                + " | so but toan=" + result.auditTrail().size());
        logs.forEach(line -> System.out.println("    " + line));

        assertThat(result.finalState()).isEqualTo("BOOKING_CONFIRMED");
        assertThat(result.tableStatus()).isEqualTo(TableStatus.BOOKED.name());
        assertThat(logs).containsExactly(
                "[Orchestrator] State: INITIATED -> Event: RESERVE_TABLE -> New State: TABLE_RESERVING",
                "[TableService] Table B7: AVAILABLE -> RESERVED (Semantic Lock acquired)",
                "[TableService] Table B7 reserved for booking REST-2026-101",
                "[Orchestrator] State: TABLE_RESERVING -> Event: TABLE_RESERVED -> New State: PAYMENT_PENDING",
                "[PaymentService] Payment of 500000 VND processed for booking REST-2026-101",
                "[Orchestrator] State: PAYMENT_PENDING -> Event: PAYMENT_SUCCESS -> New State: PAYMENT_COMPLETED",
                "[Orchestrator] State: PAYMENT_COMPLETED -> Event: CONFIRM_BOOKING -> New State: BOOKING_CONFIRMING",
                "[TableService] Table B7: RESERVED -> BOOKED (booking REST-2026-101 confirmed)",
                "[Orchestrator] State: BOOKING_CONFIRMING -> Event: BOOKING_SUCCESS -> New State: BOOKING_CONFIRMED",
                "[BookingService] Booking REST-2026-101 updated to CONFIRMED.",
                "[Final State] Booking REST-2026-101 is CONFIRMED. Table B7 is now BOOKED.");
        assertThat(transactionRepository.findByType(TransactionType.REFUND)).isEmpty();
        assertThat(transactionRepository.findByBookingId(BOOKING)).hasSize(1);
    }

    @Test
    void thanhToanThatBai_thiNhaSemanticLockVaLedgerKhongCoRefund() {
        paymentProcessingService.setDeclineAll(true);

        BookingResult result = orchestrator.execute(request(BOOKING, TABLE, BookingScenario.SUCCESS));

        List<String> logs = sagaLogs();
        System.out.println(">>> [KB4 PAYMENT FAILED] finalState=" + result.finalState()
                + " | tableStatus=" + result.tableStatus()
                + " | so but toan=" + result.auditTrail().size());
        logs.forEach(line -> System.out.println("    " + line));

        assertThat(result.finalState()).isEqualTo("CANCELLED");
        assertThat(result.tableStatus()).isEqualTo(TableStatus.AVAILABLE.name());
        assertThat(logs).contains(
                "[Orchestrator] State: PAYMENT_PENDING -> Event: PAYMENT_FAILED -> New State: CANCELLED",
                "[TableService] Table B7: RESERVED -> AVAILABLE (Semantic Lock released)",
                "[Final State] Table B7 is now AVAILABLE (semantic lock released).");
        assertThat(logs).noneMatch(line -> line.startsWith("[RefundActivity]"));
        assertThat(transactionRepository.findAll()).isEmpty();
    }

    @Test
    void banDangBiGiuBoiNguoiKhac_thiKhachKhacKhongDatDuoc() {
        BookingResult first = orchestrator.execute(request("REST-2026-100", "B8", BookingScenario.SUCCESS));
        assertThat(first.finalState()).isEqualTo("BOOKING_CONFIRMED");

        BookingResult second = orchestrator.execute(request("REST-2026-102", "B8", BookingScenario.SUCCESS));

        List<String> logs = sagaLogs();
        System.out.println(">>> [KB5 BAN DA BI GIU] finalState=" + second.finalState()
                + " | tableStatus=" + second.tableStatus());
        logs.forEach(line -> System.out.println("    " + line));

        assertThat(second.finalState()).isEqualTo("CANCELLED");
        assertThat(logs).contains(
                "[Orchestrator] State: TABLE_RESERVING -> Event: TABLE_UNAVAILABLE -> New State: CANCELLED",
                "[Final State] Table B8 is BOOKED (this booking never held the lock).");
        assertThat(transactionRepository.findByBookingId("REST-2026-102")).isEmpty();
    }

    @Test
    void semanticLock_uiHienThiBanDangDuocGiu() {
        tableReservationService.reserveTable(TABLE, BOOKING);

        System.out.println(">>> [KB6 SEMANTIC LOCK] B7 status=" + tableReservationService.statusOf(TABLE)
                + " | heldBy=" + tableReservationService.holderOf(TABLE));
        assertThat(tableReservationService.statusOf(TABLE)).isEqualTo(TableStatus.RESERVED);
        assertThat(tableReservationService.holderOf(TABLE)).isEqualTo(BOOKING);
        assertThat(tableReservationService.heldSecondsOf(TABLE)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void autoRelease_quaThoiGianGiuThiTuDongTraBan() {
        tableReservationService.setHoldTimeout(Duration.ZERO);
        tableReservationService.reserveTable(TABLE, BOOKING);
        assertThat(tableReservationService.statusOf(TABLE)).isEqualTo(TableStatus.RESERVED);

        tableReservationService.autoReleaseExpiredHolds();

        List<String> logs = sagaLogs();
        System.out.println(">>> [KB7 AUTO RELEASE] B7 status=" + tableReservationService.statusOf(TABLE));
        logs.forEach(line -> System.out.println("    " + line));

        assertThat(tableReservationService.statusOf(TABLE)).isEqualTo(TableStatus.AVAILABLE);
        assertThat(logs).contains(
                "[TableService] Table B7: RESERVED -> AVAILABLE (Semantic Lock released)",
                "[TableService] Auto-release expired reservation for table B7");
    }
}
