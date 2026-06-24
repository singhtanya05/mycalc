package com.portfolio.calc.dto;

import java.util.List;

public class SolveResponse {
    private List<String> steps;
    private String finalAnswer;
    private String latex;

    public SolveResponse() {}

    public SolveResponse(List<String> steps, String finalAnswer, String latex) {
        this.steps = steps;
        this.finalAnswer = finalAnswer;
        this.latex = latex;
    }

    public List<String> getSteps() {
        return steps;
    }

    public void setSteps(List<String> steps) {
        this.steps = steps;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }

    public String getLatex() {
        return latex;
    }

    public void setLatex(String latex) {
        this.latex = latex;
    }
}
