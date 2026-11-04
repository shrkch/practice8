package ru.edu.pr05;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Заявка на командировку. Статус меняется только доменными методами
 * submit, approve, reject, cancel, complete (R10: setter для статуса нет).
 */
public class BusinessTrip {

    // R6: минимальная длина причины отклонения (граница включена)
    public static final int MIN_REASON_LENGTH = 10;

    // R10: поля private; id, сотрудник и даты не меняются после создания (final)
    private final String id;
    private final String employee;
    private final LocalDate start;
    private final LocalDate end;
    private TripStatus status = TripStatus.DRAFT;
    private String rejectionReason;

    // Конструктор из исходного проекта: сотрудник не указан
    public BusinessTrip(String id, LocalDate start, LocalDate end) {
        this(id, null, start, end);
    }

    // Вариант 26: заявка с сотрудником, её создаёт BusinessTripService.create
    public BusinessTrip(String id, String employee, LocalDate start, LocalDate end) {
        // R1: id не null и не пустой
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is null or blank");
        }
        // R2: даты заданы и start <= end
        if (start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("start must be <= end, got " + start + " .. " + end);
        }
        this.id = id;
        this.employee = employee;
        this.start = start;
        this.end = end;
    }

    // DRAFT -> SUBMITTED (R3; R4 обеспечен конструктором)
    public void submit() {
        requireStatus(TripStatus.DRAFT, TripStatus.SUBMITTED);
        status = TripStatus.SUBMITTED;
    }

    // SUBMITTED -> APPROVED (R5)
    public void approve() {
        requireStatus(TripStatus.SUBMITTED, TripStatus.APPROVED);
        status = TripStatus.APPROVED;
    }

    // SUBMITTED -> REJECTED с причиной (R5, затем R6)
    public void reject(String reason) {
        requireStatus(TripStatus.SUBMITTED, TripStatus.REJECTED);
        if (reason == null || reason.length() < MIN_REASON_LENGTH) {
            int length = reason == null ? 0 : reason.length();
            throw new IllegalArgumentException("rejectionReason must be at least "
                    + MIN_REASON_LENGTH + " characters, got " + length);
        }
        rejectionReason = reason;
        status = TripStatus.REJECTED;
    }

    // DRAFT / SUBMITTED / APPROVED -> CANCELLED (R7)
    public void cancel() {
        if (isTerminal()) {
            throw transitionError(TripStatus.CANCELLED);
        }
        status = TripStatus.CANCELLED;
    }

    // APPROVED -> COMPLETED не раньше даты окончания (R8, затем R9)
    public void complete(LocalDate today) {
        requireStatus(TripStatus.APPROVED, TripStatus.COMPLETED);
        Objects.requireNonNull(today, "today is null");
        if (today.isBefore(end)) {
            throw new IllegalStateException("complete возможен только после " + end);
        }
        status = TripStatus.COMPLETED;
    }

    // Активная заявка: отправлена или одобрена (используется в варианте 26)
    public boolean isActive() {
        return status == TripStatus.SUBMITTED || status == TripStatus.APPROVED;
    }

    // R3, R5, R8: переход разрешён только из одного статуса
    private void requireStatus(TripStatus expected, TripStatus target) {
        if (status != expected) {
            throw transitionError(target);
        }
    }

    // Терминальные статусы не имеют исходящих переходов
    private boolean isTerminal() {
        return status == TripStatus.REJECTED
                || status == TripStatus.CANCELLED
                || status == TripStatus.COMPLETED;
    }

    private IllegalStateException transitionError(TripStatus target) {
        return new IllegalStateException("Ошибка перехода: " + status + " → " + target + " недопустим");
    }

    // Геттеры без setter (R10). LocalDate и String иммутабельны, копии не нужны
    public String getId() { return id; }
    public String getEmployee() { return employee; }
    public LocalDate getStart() { return start; }
    public LocalDate getEnd() { return end; }
    public TripStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }

    @Override
    public String toString() {
        return "BusinessTrip{" + id + ", " + start + ".." + end + ", " + status + "}";
    }
}
