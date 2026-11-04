package ru.edu.pr05;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Scanner;

/**
 * Тестовый раннер: unit-проверки, data-driven режим по data/sample.csv и интерактивный режим.
 */
public class SelfCheck {

    private static final LocalDate START = LocalDate.parse("2026-10-10");
    private static final LocalDate END = LocalDate.parse("2026-10-12");
    private static final String REASON_10 = "Нет бюджет";             // ровно 10 символов
    private static final String DEFAULT_REASON = "Недостаточно бюджета";

    private static int passed = 0;
    private static int failed = 0;

    // Сервис data-driven режима: строки варианта 26 работают с общим списком заявок
    private static BusinessTripService dataService = new BusinessTripService();

    public static void main(String[] args) {
        System.out.println("=== Unit-проверки (18 базовых) ===");
        checkConstructor();
        checkSubmitApprove();
        checkReject();
        checkCancel();
        checkComplete();
        System.out.println();
        System.out.println("=== Вариант 26 ===");
        checkVariantA();
        checkVariantB();
        System.out.println();
        System.out.println("=== Граничные и терминальные случаи ===");
        checkExtra();
        System.out.println("Unit: " + passed + "/" + (passed + failed) + " passed");
        System.out.println();
        runDataDriven("data/sample.csv");
        runInteractive();
    }

    // ===== 1–5: конструктор (R1, R2) =====
    private static void checkConstructor() {
        check("1 Конструктор: корректные данные -> DRAFT", "OK:status=DRAFT".equals(create("T-01", START, END)));
        check("2 Конструктор: id=null", create(null, START, END).equals("IAE:id is null or blank"));
        check("3 Конструктор: id=\"\"", create("", START, END).equals("IAE:id is null or blank"));
        check("4 Конструктор: start=null", create("T-01", null, END).startsWith("IAE:start must be <= end"));
        check("5 Конструктор: start > end", create("T-01", END, START).startsWith("IAE:start must be <= end"));
    }

    // ===== 6–9: submit (R3), approve (R5) =====
    private static void checkSubmitApprove() {
        check("6 submit из DRAFT -> SUBMITTED", "OK:status=SUBMITTED".equals(run(trip(), "SUBMIT")));
        check("7 submit из SUBMITTED (R3)",
                run(trip(), "SUBMIT>SUBMIT").equals("ISE:Ошибка перехода: SUBMITTED → SUBMITTED недопустим"));
        check("8 approve из SUBMITTED -> APPROVED", "OK:status=APPROVED".equals(run(trip(), "SUBMIT>APPROVE")));
        check("9 approve из DRAFT (R5)",
                run(trip(), "APPROVE").equals("ISE:Ошибка перехода: DRAFT → APPROVED недопустим"));
    }

    // ===== 10–12: reject (R5, R6) =====
    private static void checkReject() {
        BusinessTrip trip = trip();
        check("10 reject с причиной 10 символов -> REJECTED",
                "OK:status=REJECTED".equals(run(trip, "SUBMIT>REJECT:" + REASON_10))
                        && REASON_10.equals(trip.getRejectionReason()));
        check("11 reject с причиной 3 символа (R6)", run(trip(), "SUBMIT>REJECT:Нет")
                .equals("IAE:rejectionReason must be at least 10 characters, got 3"));
        check("12 reject из DRAFT (R5)",
                run(trip(), "REJECT:" + REASON_10).equals("ISE:Ошибка перехода: DRAFT → REJECTED недопустим"));
    }

    // ===== 13–14: cancel (R7) =====
    private static void checkCancel() {
        check("13 cancel из DRAFT -> CANCELLED", "OK:status=CANCELLED".equals(run(trip(), "CANCEL")));
        check("14 cancel из COMPLETED (R7)", run(trip(), "SUBMIT>APPROVE>COMPLETE>CANCEL")
                .equals("ISE:Ошибка перехода: COMPLETED → CANCELLED недопустим"));
    }

    // ===== 15–18: complete (R8, R9), getRejectionReason =====
    private static void checkComplete() {
        check("15 complete из APPROVED, сегодня = end -> COMPLETED",
                "OK:status=COMPLETED".equals(run(trip(), "SUBMIT>APPROVE>COMPLETE:" + END)));
        check("16 complete из SUBMITTED (R8)", run(trip(), "SUBMIT>COMPLETE:" + END)
                .equals("ISE:Ошибка перехода: SUBMITTED → COMPLETED недопустим"));
        check("17 complete, сегодня < end (R9)", run(trip(), "SUBMIT>APPROVE>COMPLETE:" + END.minusDays(1))
                .equals("ISE:complete возможен только после " + END));
        BusinessTrip trip = trip();
        run(trip, "SUBMIT>REJECT:" + DEFAULT_REASON);
        check("18 getRejectionReason после reject", DEFAULT_REASON.equals(trip.getRejectionReason()));
    }

    // ===== 19–22: часть А — нельзя создать вторую заявку при активной =====
    private static void checkVariantA() {
        BusinessTripService service = new BusinessTripService();
        service.create("A-01", "Ivanov", START, END);
        service.submit("A-01");
        check("19 А: create при SUBMITTED-заявке -> ISE", serviceCreate(service, "A-02", "Ivanov")
                .equals("ISE:У сотрудника Ivanov уже есть активная командировка A-01 (SUBMITTED)"));
        service.findById("A-01").approve();
        check("20 А: create при APPROVED-заявке -> ISE",
                serviceCreate(service, "A-02", "Ivanov").startsWith("ISE:У сотрудника Ivanov"));
        check("21 А: другой сотрудник не блокируется",
                "OK:status=DRAFT".equals(serviceCreate(service, "A-03", "Petrov")));
        service.findById("A-01").complete(END);
        check("22 А: после COMPLETED создать можно",
                "OK:status=DRAFT".equals(serviceCreate(service, "A-04", "Ivanov")));
        checkVariantASubmit();
    }

    // Две заявки в DRAFT: после отправки первой вторая не отправляется
    private static void checkVariantASubmit() {
        BusinessTripService service = new BusinessTripService();
        service.create("S-01", "Sidorov", START, END);
        service.create("S-02", "Sidorov", START, END);
        service.submit("S-01");
        String result;
        try {
            service.submit("S-02");
            result = "OK:status=" + service.findById("S-02").getStatus();
        } catch (IllegalStateException e) {
            result = "ISE:" + e.getMessage();
        }
        check("23 А: submit второй заявки при активной -> ISE", result.startsWith("ISE:У сотрудника Sidorov"));
    }

    // ===== 24–26: часть Б — addActiveTripsCount =====
    private static void checkVariantB() {
        BusinessTripService service = new BusinessTripService();
        check("24 Б: пустой сервис -> 0", BusinessTripService.addActiveTripsCount(service) == 0);
        service.create("B-01", "Ivanov", START, END);                         // DRAFT
        service.create("B-02", "Petrov", START, END);
        service.submit("B-02");                                               // SUBMITTED
        service.create("B-03", "Sidorov", START, END);
        service.submit("B-03");
        service.findById("B-03").approve();                                   // APPROVED
        service.create("B-04", "Orlov", START, END);
        service.submit("B-04");
        service.findById("B-04").reject(DEFAULT_REASON);                      // REJECTED
        check("25 Б: DRAFT, SUBMITTED, APPROVED, REJECTED -> 2",
                BusinessTripService.addActiveTripsCount(service) == 2);
        service.findById("B-03").complete(END);                               // APPROVED -> COMPLETED
        check("26 Б: после complete активных 1", BusinessTripService.addActiveTripsCount(service) == 1);
    }

    // ===== 27–30: границы и терминальные состояния =====
    private static void checkExtra() {
        check("27 reject с причиной 9 символов (граница R6)",
                run(trip(), "SUBMIT>REJECT:123456789").startsWith("IAE:rejectionReason must be at least 10"));
        check("28 cancel из APPROVED -> CANCELLED", "OK:status=CANCELLED".equals(run(trip(), "SUBMIT>APPROVE>CANCEL")));
        check("29 из REJECTED submit запрещён", run(trip(), "SUBMIT>REJECT:" + DEFAULT_REASON + ">SUBMIT")
                .equals("ISE:Ошибка перехода: REJECTED → SUBMITTED недопустим"));
        check("30 start = end (командировка на 1 день)", "OK:status=DRAFT".equals(create("T-30", START, START)));
    }

    // ===== Data-driven режим: id;start;end;action;expected =====
    private static void runDataDriven(String path) {
        System.out.println("=== Data-driven: " + path + " ===");
        List<String> lines = readLines(path);
        int ok = 0;
        int total = 0;
        for (int i = 1; i < lines.size(); i++) {                // строка 0 — заголовок
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            total++;
            String[] p = line.split(";", -1);
            String expected = p.length == 5 ? p[4] : "";
            String actual = p.length == 5 ? evaluate(p) : "FORMAT:ожидалось 5 полей";
            boolean pass = !expected.isEmpty() && actual.startsWith(expected);
            if (pass) {
                ok++;
            }
            System.out.println((pass ? "PASS data/" : "FAIL data/") + total + " " + line
                    + (pass ? "" : " -> получено " + actual));
        }
        System.out.println("Data-driven: " + ok + "/" + total + " passed");
    }

    private static List<String> readLines(String path) {
        try {
            return Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            System.out.println("FAIL data: не удалось прочитать " + path);
            return List.of();
        }
    }

    // Одна строка CSV: создать заявку и выполнить цепочку действий через «>»
    private static String evaluate(String[] p) {
        if (p[3].equals("ACTIVE_COUNT")) {
            return "OK:count=" + BusinessTripService.addActiveTripsCount(dataService);
        }
        LocalDate start;
        LocalDate end;
        try {
            start = p[1].isEmpty() ? null : LocalDate.parse(p[1]);
            end = p[2].isEmpty() ? null : LocalDate.parse(p[2]);
        } catch (DateTimeParseException e) {
            return "ERROR:DateTimeParseException";
        }
        String[] steps = p[3].split(">", 2);
        BusinessTrip trip;
        String rest = steps.length > 1 ? steps[1] : "";
        try {
            if (steps[0].startsWith("CREATE:")) {
                trip = dataService.create(p[0], steps[0].substring(7), start, end);
            } else {
                trip = new BusinessTrip(p[0], start, end);
                rest = p[3];
            }
        } catch (IllegalArgumentException e) {
            return "SKIP:" + e.getMessage();
        } catch (IllegalStateException e) {
            return "ERROR:IllegalStateException";
        }
        // Строки варианта 26 отправляют заявку через сервис, остальные — напрямую
        BusinessTripService service = steps[0].startsWith("CREATE:") ? dataService : null;
        String result = rest.isEmpty() ? "OK:status=" + trip.getStatus() : run(trip, rest, service);
        if (result.startsWith("ISE:")) {
            return "ERROR:IllegalStateException:" + result.substring(4);
        }
        if (result.startsWith("IAE:")) {
            return "ERROR:IllegalArgumentException:" + result.substring(4);
        }
        return result;
    }

    // ===== Интерактивный режим для защиты =====
    private static void runInteractive() {
        System.out.println();
        System.out.println("=== Интерактивный режим ===");
        System.out.println("Команды: new <id> <start> <end> <сотрудник>, use <id>, submit, approve,");
        System.out.println("reject <причина>, cancel, complete [дата], list, count. Пустая строка или exit — выход.");
        BusinessTripService service = new BusinessTripService();
        BusinessTrip current = null;
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            String line = scanner.nextLine().trim();
            if (line.isEmpty() || line.equalsIgnoreCase("exit")) {
                break;
            }
            try {
                current = handleCommand(line, current, service);
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("Ошибка: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            if (current != null) {
                System.out.println("Текущая заявка: " + current);
            }
        }
        System.out.println("Выход из интерактивного режима");
    }

    private static BusinessTrip handleCommand(String line, BusinessTrip current, BusinessTripService service) {
        String[] c = line.split("\\s+", 2);
        String command = c[0].toLowerCase();
        String arg = c.length > 1 ? c[1] : "";
        switch (command) {
            case "new" -> {
                String[] a = arg.split("\\s+");
                if (a.length != 4) {
                    throw new IllegalArgumentException("формат: new <id> <start> <end> <сотрудник>");
                }
                return service.create(a[0], a[3], parseDate(a[1]), parseDate(a[2]));
            }
            case "use" -> {
                BusinessTrip trip = service.findById(arg);
                if (trip == null) {
                    throw new IllegalArgumentException("trip " + arg + " not found");
                }
                return trip;
            }
            case "list" -> {
                for (BusinessTrip trip : service.findAll()) {
                    System.out.println("  " + trip + " сотрудник " + trip.getEmployee());
                }
                return current;
            }
            case "count" -> {
                System.out.println("Активных командировок: " + BusinessTripService.addActiveTripsCount(service));
                return current;
            }
            default -> {
                if (current == null) {
                    throw new IllegalStateException("сначала создайте заявку командой new");
                }
                applyCommand(command, arg, current, service);
                return current;
            }
        }
    }

    private static void applyCommand(String command, String arg, BusinessTrip trip, BusinessTripService service) {
        switch (command) {
            case "submit" -> service.submit(trip.getId());
            case "approve" -> trip.approve();
            case "reject" -> trip.reject(arg);
            case "cancel" -> trip.cancel();
            case "complete" -> trip.complete(arg.isEmpty() ? LocalDate.now() : parseDate(arg));
            default -> throw new IllegalArgumentException("неизвестная команда: " + command);
        }
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("дата должна быть в формате yyyy-MM-dd: " + text);
        }
    }

    // ===== Вспомогательные методы =====

    // Выполняет цепочку действий «SUBMIT>APPROVE>COMPLETE:2026-10-12»; результат — статус или исключение
    private static String run(BusinessTrip trip, String steps) {
        return run(trip, steps, null);
    }

    // service != null: SUBMIT идёт через BusinessTripService (проверка варианта 26)
    private static String run(BusinessTrip trip, String steps, BusinessTripService service) {
        try {
            for (String step : steps.split(">")) {
                applyStep(trip, step, service);
            }
            return "OK:status=" + trip.getStatus();
        } catch (IllegalStateException e) {
            return "ISE:" + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "IAE:" + e.getMessage();
        }
    }

    private static void applyStep(BusinessTrip trip, String step, BusinessTripService service) {
        String[] s = step.split(":", 2);
        String arg = s.length > 1 ? s[1] : null;
        switch (s[0]) {
            case "SUBMIT" -> {
                if (service == null) {
                    trip.submit();
                } else {
                    service.submit(trip.getId());
                }
            }
            case "APPROVE" -> trip.approve();
            case "REJECT" -> trip.reject(arg == null ? DEFAULT_REASON : arg);
            case "CANCEL" -> trip.cancel();
            case "COMPLETE" -> trip.complete(arg == null ? trip.getEnd() : LocalDate.parse(arg));
            default -> throw new IllegalArgumentException("unknown action " + s[0]);
        }
    }

    private static String create(String id, LocalDate start, LocalDate end) {
        try {
            return "OK:status=" + new BusinessTrip(id, start, end).getStatus();
        } catch (IllegalArgumentException e) {
            return "IAE:" + e.getMessage();
        }
    }

    private static String serviceCreate(BusinessTripService service, String id, String employee) {
        try {
            return "OK:status=" + service.create(id, employee, START, END).getStatus();
        } catch (IllegalStateException e) {
            return "ISE:" + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "IAE:" + e.getMessage();
        }
    }

    private static BusinessTrip trip() {
        return new BusinessTrip("T-01", START, END);
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS " + name);
        } else {
            failed++;
            System.out.println("FAIL " + name);
        }
    }
}
