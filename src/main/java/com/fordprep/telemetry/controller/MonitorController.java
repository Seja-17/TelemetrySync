package com.fordprep.telemetry.controller;

import com.fordprep.telemetry.model.MonitoredService;
import com.fordprep.telemetry.repository.MonitoredServiceRepository;
import com.fordprep.telemetry.service.MonitorEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class MonitorController {

    private static final Logger log = LoggerFactory.getLogger(MonitorController.class);

    private final MonitoredServiceRepository repository;
    private final MonitorEngine monitorEngine;

    public MonitorController(MonitoredServiceRepository repository, MonitorEngine monitorEngine) {
        this.repository = repository;
        this.monitorEngine = monitorEngine;
    }

    @PostMapping
    public ResponseEntity<MonitoredService> register(@RequestBody MonitoredService request) {
        if (isBlank(request.getName()) || isBlank(request.getUrl())) {
            return ResponseEntity.badRequest().build();
        }

        // Never trust client-supplied id/status/lastChecked
        MonitoredService service = new MonitoredService(
                null, request.getName().trim(), request.getUrl().trim(), "PENDING", null);

        MonitoredService saved = repository.save(service);
        log.info("Registered service '{}' with id {}", saved.getName(), saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping
    public List<MonitoredService> listAll() {
        return repository.findAll();
    }

    @PostMapping("/ping-all")
    public List<MonitoredService> pingAll() {
        log.info("Manual ping-all triggered via API");
        monitorEngine.checkAllServices();
        return repository.findAll();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}