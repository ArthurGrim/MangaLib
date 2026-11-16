package de.mangalib.controller;

import de.mangalib.service.ReadingHistoryService;
import de.mangalib.service.ReadingHistoryStatistics.Kind;
import de.mangalib.validation.WriteRequests;
import de.mangalib.validation.WriteValidation;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Explizite Leseaktionen; alle Fehlerantworten bleiben frei von internen Exceptiontexten. */
@RestController
@RequestMapping("/reading-history")
public class ReadingHistoryController {
    private final ObjectProvider<ReadingHistoryService> services;
    public ReadingHistoryController(ObjectProvider<ReadingHistoryService> services) { this.services = services; }

    @GetMapping("/status")
    public Map<String, Object> status() {
        var service = services.getIfAvailable();
        return service == null ? Map.of("enabled", false) : Map.of("enabled", true, "today", service.today().toString());
    }

    @GetMapping("/bands/{id}")
    public ResponseEntity<?> history(@PathVariable long id) {
        return run(service -> { WriteValidation.require(id > 0); return Map.of("events", service.history(id)); });
    }

    @GetMapping("/months")
    public ResponseEntity<?> months() { return run(service -> Map.of("months", service.months())); }

    @PostMapping(value = "/complete", consumes = "application/json")
    public ResponseEntity<?> complete(@RequestBody Map<String, Object> data) {
        return run(service -> {
            WriteValidation.require(data.keySet().equals(Set.of("bandIds", "operationId", "kind", "date")));
            WriteValidation.require(data.get("bandIds") instanceof List<?>);
            var values = (List<?>) data.get("bandIds");
            WriteValidation.require(!values.isEmpty() && values.size() <= WriteValidation.MAX_VOLUMES);
            var ids = values.stream().map(value -> {
                WriteValidation.require(value != null);
                return WriteRequests.purchaseId(Map.of("id", value), "id");
            }).toList();
            WriteValidation.require(data.get("kind") instanceof String && data.get("operationId") instanceof String);
            String token = (String) data.get("operationId");
            UUID operation = UUID.fromString(token);
            WriteValidation.require(operation.toString().equals(token));
            return Map.of("events", service.complete(ids, operation, Kind.valueOf((String) data.get("kind")), date(data.get("date"))));
        });
    }

    @PostMapping(value = "/bands/{bandId}/events/{eventId}/date", consumes = "application/json")
    public ResponseEntity<?> correct(@PathVariable long bandId, @PathVariable long eventId, @RequestBody Map<String, Object> data) {
        return run(service -> {
            WriteValidation.require(data.keySet().equals(Set.of("revision", "date")) && data.get("revision") instanceof Integer);
            return Map.of("event", service.correct(bandId, eventId, (Integer) data.get("revision"), date(data.get("date"))));
        });
    }

    private LocalDate date(Object value) {
        WriteValidation.require(value instanceof String && ((String) value).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"));
        try { return LocalDate.parse((String) value); }
        catch (java.time.DateTimeException invalid) { throw new IllegalArgumentException("Ungültiges Datum."); }
    }

    private ResponseEntity<?> run(java.util.function.Function<ReadingHistoryService, Object> work) {
        var service = services.getIfAvailable();
        if (service == null) return ResponseEntity.status(503).body(Map.of("message", "Lesehistorie ist noch nicht aktiviert."));
        try { return ResponseEntity.ok(work.apply(service)); }
        catch (ReadingHistoryService.Missing missing) { return ResponseEntity.notFound().build(); }
        catch (ReadingHistoryService.Conflict conflict) {
            return ResponseEntity.status(409).body(Map.of("message", "Leseaktion widerspricht dem aktuellen Stand. Bitte neu laden."));
        }
        catch (IllegalArgumentException invalid) { return ResponseEntity.badRequest().build(); }
        catch (Exception failure) { return ResponseEntity.internalServerError().build(); }
    }
}
