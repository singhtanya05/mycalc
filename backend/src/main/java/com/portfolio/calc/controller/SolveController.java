package com.portfolio.calc.controller;

import com.portfolio.calc.dto.SolveRequest;
import com.portfolio.calc.dto.SolveResponse;
import com.portfolio.calc.service.SolverService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:5174"})
public class SolveController {

    private final SolverService solverService;

    public SolveController(SolverService solverService) {
        this.solverService = solverService;
    }

    @PostMapping("/solve")
    public ResponseEntity<SolveResponse> solve(@Valid @RequestBody SolveRequest request) {
        SolveResponse response = solverService.solve(request.getEquation(), request.getProblemType());
        return ResponseEntity.ok(response);
    }
}
