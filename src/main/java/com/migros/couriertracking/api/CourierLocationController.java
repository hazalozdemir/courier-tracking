package com.migros.couriertracking.api;

import com.migros.couriertracking.api.dto.LocationIngestResponse;
import com.migros.couriertracking.api.dto.LocationRequest;
import com.migros.couriertracking.courier.LocationIngestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
public class CourierLocationController {

    private final LocationIngestionService ingestionService;

    public CourierLocationController(LocationIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public LocationIngestResponse ingest(@Valid @RequestBody LocationRequest request) {
        return LocationIngestResponse.from(ingestionService.ingest(request.toLocationUpdate()));
    }
}
