package com.forA.chatbot.medications.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.forA.chatbot.apiPayload.code.status.ErrorStatus;
import com.forA.chatbot.apiPayload.code.status.SuccessStatus;
import com.forA.chatbot.apiPayload.exception.handler.MedicationHandler;
import com.forA.chatbot.auth.jwt.CustomUserDetails;
import com.forA.chatbot.auth.jwt.JwtAuthenticationFilter;
import com.forA.chatbot.medications.dto.MedicationRequestDto;
import com.forA.chatbot.medications.dto.MedicationResponseDto;
import com.forA.chatbot.medications.dto.MedicationUpdateRequestDto;
import com.forA.chatbot.medications.service.MedicationService;
import java.time.LocalDateTime;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MedicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class MedicationControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private MedicationService medicationService;

  @MockitoBean
  private JwtAuthenticationFilter jwtAuthenticationFilter;

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("createMedicationPlan returns medication success response")
  void createMedicationPlan_success() throws Exception {
    authenticateAsUser(1L);
    given(medicationService.createMedicationPlan(eq(1L), any(MedicationRequestDto.class)))
        .willReturn(
            MedicationResponseDto.builder()
                .id(10L)
                .name("vitamin")
                .typeTags(Collections.singletonList("SUPPLEMENT"))
                .takeDays(Collections.singletonList("MONDAY"))
                .takeTime("09:00")
                .createdAt(LocalDateTime.of(2026, 3, 16, 9, 0))
                .build());

    mockMvc
        .perform(
            post("/api/v1/medications/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"vitamin\",\"typeTags\":[\"SUPPLEMENT\"],\"takeDays\":[\"MONDAY\"],\"takeTime\":\"09:00\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.isSuccess").value(true))
        .andExpect(jsonPath("$.code").value(SuccessStatus.MEDICATION_CREATED.getCode()))
        .andExpect(jsonPath("$.message").value(SuccessStatus.MEDICATION_CREATED.getMessage()))
        .andExpect(jsonPath("$.result.id").value(10))
        .andExpect(jsonPath("$.result.name").value("vitamin"))
        .andExpect(jsonPath("$.result.typeTags[0]").value("SUPPLEMENT"))
        .andExpect(jsonPath("$.result.takeDays[0]").value("MONDAY"))
        .andExpect(jsonPath("$.result.takeTime").value("09:00"));

    verify(medicationService).createMedicationPlan(eq(1L), any(MedicationRequestDto.class));
  }

  @Test
  @DisplayName("createMedicationPlan returns validation error for blank name")
  void createMedicationPlan_validationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            post("/api/v1/medications/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"\",\"typeTags\":[\"SUPPLEMENT\"],\"takeDays\":[\"MONDAY\"],\"takeTime\":\"09:00\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus._BAD_REQUEST.getMessage()))
        .andExpect(jsonPath("$.result.name").exists());

    verify(medicationService, never()).createMedicationPlan(anyLong(), any(MedicationRequestDto.class));
  }

  @Test
  @DisplayName("createMedicationPlan maps medication business error through exception advice")
  void createMedicationPlan_duplicateName() throws Exception {
    authenticateAsUser(1L);
    given(medicationService.createMedicationPlan(eq(1L), any(MedicationRequestDto.class)))
        .willThrow(new MedicationHandler(ErrorStatus.MEDICATION_PLAN_ALREADY_EXISTS));

    mockMvc
        .perform(
            post("/api/v1/medications/plan")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"vitamin\",\"typeTags\":[\"SUPPLEMENT\"],\"takeDays\":[\"MONDAY\"],\"takeTime\":\"09:00\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus.MEDICATION_PLAN_ALREADY_EXISTS.getCode()))
        .andExpect(jsonPath("$.message").value(ErrorStatus.MEDICATION_PLAN_ALREADY_EXISTS.getMessage()));

    verify(medicationService).createMedicationPlan(eq(1L), any(MedicationRequestDto.class));
  }

  @Test
  @DisplayName("updateMedicationPlan returns validation error for blank name")
  void updateMedicationPlan_blankNameValidationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            patch("/api/v1/medications/plan/{planId}", 20L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.result.name").exists());

    verify(medicationService, never())
        .updateMedicationPlan(anyLong(), anyLong(), any(MedicationUpdateRequestDto.class));
  }

  @Test
  @DisplayName("updateMedicationPlan returns validation error for empty takeDays")
  void updateMedicationPlan_emptyTakeDaysValidationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            patch("/api/v1/medications/plan/{planId}", 20L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"takeDays\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.result.takeDays").exists());

    verify(medicationService, never())
        .updateMedicationPlan(anyLong(), anyLong(), any(MedicationUpdateRequestDto.class));
  }

  @Test
  @DisplayName("updateMedicationPlan returns validation error for invalid time format")
  void updateMedicationPlan_invalidTimeValidationFailure() throws Exception {
    authenticateAsUser(1L);

    mockMvc
        .perform(
            patch("/api/v1/medications/plan/{planId}", 20L)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"takeTime\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.isSuccess").value(false))
        .andExpect(jsonPath("$.code").value(ErrorStatus._BAD_REQUEST.getCode()))
        .andExpect(jsonPath("$.result.takeTime").exists());

    verify(medicationService, never())
        .updateMedicationPlan(anyLong(), anyLong(), any(MedicationUpdateRequestDto.class));
  }

  private void authenticateAsUser(Long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(userId), null, Collections.emptyList()));
  }
}
