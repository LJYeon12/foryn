package com.forA.chatbot.global;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/test")
@Slf4j
@Tag(name = "Test", description = "Test API")
public class TestController {

  @GetMapping("/")
  @Operation(summary = "테스트용 API", description = "스웨거 동작 테스트용 API")
  @ApiResponses({
    @ApiResponse(responseCode = "COMMON200", description = "OK, 성공"),
    @ApiResponse(responseCode = "TEST500", description = "테스트를 실패하였습니다.")
  })
  public String test() {
    return "테스트 성공, OK";
  }
}
