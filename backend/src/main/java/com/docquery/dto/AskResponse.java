package com.docquery.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AskResponse {
    private String answer;
    private String sourceSnippet;
    private boolean fromCache;
}
