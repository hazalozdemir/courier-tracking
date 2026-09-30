package com.migros.couriertracking.api;

import com.migros.couriertracking.api.dto.CourierIds;
import com.migros.couriertracking.api.dto.StoreEntranceResponse;
import com.migros.couriertracking.api.dto.TotalDistanceResponse;
import com.migros.couriertracking.courier.CourierQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/couriers/{courierId}")
public class CourierController {

    private final CourierQueryService queryService;

    public CourierController(CourierQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/total-distance")
    public TotalDistanceResponse totalDistance(
            @PathVariable @Pattern(regexp = CourierIds.PATTERN, message = CourierIds.MESSAGE) String courierId) {
        return TotalDistanceResponse.of(courierId, queryService.getTotalTravelDistance(courierId));
    }

    @GetMapping("/store-entrances")
    public List<StoreEntranceResponse> storeEntrances(
            @PathVariable @Pattern(regexp = CourierIds.PATTERN, message = CourierIds.MESSAGE) String courierId) {
        return queryService.getStoreEntrances(courierId).stream().map(StoreEntranceResponse::from).toList();
    }
}
