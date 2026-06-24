package com.portfolio.calc.controller;

import com.portfolio.calc.service.SolverService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {SolveController.class})
public class SolveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SolverService solverService;

    @Test
    public void testSolveEndpointSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equation\":\"x^2 + 5x + 6 = 0\",\"problemType\":\"algebra\"}"))
                .andExpect(status().isOk());
    }

    @Test
    public void testSolveEndpointValidationErrorEmpty() throws Exception {
        mockMvc.perform(post("/api/v1/solve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"equation\":\"\",\"problemType\":\"algebra\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.equation").exists());
    }
}
