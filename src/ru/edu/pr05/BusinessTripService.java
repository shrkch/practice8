package ru.edu.pr05;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сервисный слой: создаёт заявки, хранит их и ищет по статусу.
 * Правила переходов остаются в BusinessTrip, сервис только координирует.
 */
public class BusinessTripService {

    private final List<BusinessTrip> trips = new ArrayList<>();

    // Вариант 26, часть А: нельзя создать заявку, пока у сотрудника есть активная
    public BusinessTrip create(String id, String employee, LocalDate start, LocalDate end) {
        if (employee == null || employee.isBlank()) {
            throw new IllegalArgumentException("employee is null or blank");
        }
        if (findById(id) != null) {
            throw new IllegalArgumentException("trip " + id + " already exists");
        }
        // Сначала данные (R1, R2 в конструкторе), потом состояние сотрудника
        BusinessTrip trip = new BusinessTrip(id, employee, start, end);
        requireNoActiveTrip(employee, id);
        trips.add(trip);
        return trip;
    }

    // Вариант 26, часть А: R3 через сервис — повторная проверка при отправке
    public void submit(String id) {
        BusinessTrip trip = findById(id);
        if (trip == null) {
            throw new IllegalArgumentException("trip " + id + " not found");
        }
        requireNoActiveTrip(trip.getEmployee(), id);
        trip.submit();
    }

    public List<BusinessTrip> findByStatus(TripStatus status) {
        Objects.requireNonNull(status, "status is null");
        List<BusinessTrip> result = new ArrayList<>();
        for (BusinessTrip trip : trips) {
            if (trip.getStatus() == status) {
                result.add(trip);
            }
        }
        return List.copyOf(result);
    }

    public BusinessTrip findById(String id) {
        for (BusinessTrip trip : trips) {
            if (trip.getId().equals(id)) {
                return trip;
            }
        }
        return null;
    }

    public List<BusinessTrip> findAll() {
        return List.copyOf(trips);
    }

    // Вариант 26, часть Б: количество активных командировок (SUBMITTED и APPROVED)
    public static int addActiveTripsCount(BusinessTripService service) {
        Objects.requireNonNull(service, "service is null");
        return service.findByStatus(TripStatus.SUBMITTED).size()
                + service.findByStatus(TripStatus.APPROVED).size();
    }

    // Активная заявка того же сотрудника, кроме заявки с указанным id
    private void requireNoActiveTrip(String employee, String exceptId) {
        for (BusinessTrip trip : trips) {
            if (trip.isActive() && trip.getEmployee().equals(employee)
                    && !trip.getId().equals(exceptId)) {
                throw new IllegalStateException("У сотрудника " + employee
                        + " уже есть активная командировка " + trip.getId() + " (" + trip.getStatus() + ")");
            }
        }
    }
}
