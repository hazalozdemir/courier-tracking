package com.migros.couriertracking.api;

import com.migros.couriertracking.api.dto.StoreResponse;
import com.migros.couriertracking.store.StoreRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreRegistry storeRegistry;

    public StoreController(StoreRegistry storeRegistry) {
        this.storeRegistry = storeRegistry;
    }

    @GetMapping
    public List<StoreResponse> stores() {
        return storeRegistry.all().stream().map(StoreResponse::from).toList();
    }
}
