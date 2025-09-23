package com.foodsave.backend.controller;

import com.foodsave.backend.dto.OrderDTO;
import com.foodsave.backend.dto.miniapp.MiniAppReservationRequest;
import com.foodsave.backend.service.MiniAppReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/reservations")
@RequiredArgsConstructor
public class MiniAppReservationController {

    private final MiniAppReservationService reservationService;

    @PostMapping
    public ResponseEntity<OrderDTO> createReservation(@Valid @RequestBody MiniAppReservationRequest request) {
        return ResponseEntity.ok(reservationService.createReservation(request));
    }
}
