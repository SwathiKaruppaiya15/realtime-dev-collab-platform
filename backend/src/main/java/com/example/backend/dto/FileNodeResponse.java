package com.example.backend.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileNodeResponse {
    private Long id;
    private String name;
    private String type;
    private String content;
    private String extension;
    private Long parentId;
    private Long roomId;
    private Long createdBy;
    private List<FileNodeResponse> children = new ArrayList<>();
}
