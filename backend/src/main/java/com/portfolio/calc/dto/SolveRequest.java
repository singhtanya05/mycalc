package com.portfolio.calc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SolveRequest {
    @NotBlank(message = "Equation cannot be empty")
    @Size(max = 250, message = "Equation is too long (max 250 characters)")
    private String equation;

    private String problemType; // "algebra", "derivative", "integral", "limit", "auto"

    public String getEquation() {
        return equation;
    }

    public void setEquation(String equation) {
        this.equation = equation;
    }

    public String getProblemType() {
        return problemType;
    }

    public void setProblemType(String problemType) {
        this.problemType = problemType;
    }
}
