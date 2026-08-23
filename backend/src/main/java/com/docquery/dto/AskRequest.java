package com.docquery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AskRequest {
    @NotBlank
    private String question;
}
