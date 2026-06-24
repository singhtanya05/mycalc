package com.portfolio.calc.service;

import com.portfolio.calc.dto.SolveResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SolverServiceTest {

    private final SolverService solverService = new SolverService();

    @Test
    public void testDerivativePowerRule() {
        SolveResponse response = solverService.solve("x^3", "derivative");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Power Rule")));
        assertEquals("3*x^2", response.getFinalAnswer());
    }

    @Test
    public void testDerivativeSumRule() {
        SolveResponse response = solverService.solve("x^2 + x", "derivative");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Sum Rule")));
    }

    @Test
    public void testDerivativeProductRule() {
        SolveResponse response = solverService.solve("x * sin(x)", "derivative");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Product Rule")));
    }

    @Test
    public void testDerivativeChainRule() {
        SolveResponse response = solverService.solve("sin(x^2)", "derivative");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Chain Rule")));
    }

    @Test
    public void testIntegralSumRule() {
        SolveResponse response = solverService.solve("integrate(x^2 + x, x)", "integral");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.toLowerCase().contains("sum rule")));
    }

    @Test
    public void testLimitEvaluation() {
        SolveResponse response = solverService.solve("Limit(x^2, x, 2)", "limit");
        assertNotNull(response);
        assertEquals("4", response.getFinalAnswer());
    }

    @Test
    public void testOcrWithDownarrowCirc() {
        SolveResponse response = solverService.solve("\\downarrow_{\\circ}\\int4x\\cos\\left(2-3x\\right)\\,d x", "integral");
        assertNotNull(response);
        assertTrue(response.getSteps().getFirst().contains("\\int 4 \\cdot x \\cdot \\cos"));
    }

    @Test
    public void testIntegralIntegrationByParts() {
        SolveResponse response = solverService.solve("integrate(x * cos(x), x)", "integral");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Integration by Parts")));
        assertTrue(response.getFinalAnswer().toLowerCase().contains("cos"));
        assertTrue(response.getFinalAnswer().toLowerCase().contains("sin"));
    }

    @Test
    public void testIntegralTrigRules() {
        SolveResponse cosResponse = solverService.solve("integrate(cos(x), x)", "integral");
        assertNotNull(cosResponse);
        assertTrue(cosResponse.getSteps().stream().anyMatch(step -> step.contains("Cosine Integration Rule")));

        SolveResponse sinResponse = solverService.solve("integrate(sin(x), x)", "integral");
        assertNotNull(sinResponse);
        assertTrue(sinResponse.getSteps().stream().anyMatch(step -> step.contains("Sine Integration Rule")));
    }

    @Test
    public void testAlgebraSolving() {
        SolveResponse response = solverService.solve("3*x + 6 = 0", "algebra");
        assertNotNull(response);
        assertTrue(response.getSteps().stream().anyMatch(step -> step.contains("Solve the algebraic equation")));
        assertTrue(response.getFinalAnswer().contains("-2"));
    }
}
