package com.example.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_nodes")
@Data
@NoArgsConstructor
public class FileNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type; // "FILE" or "FOLDER"

    @Column(columnDefinition = "TEXT")
    private String content;

    private String extension; // e.g. "js", "java", "py"

    private Long parentId; // null = root

    private Long createdBy; // userId who created this node

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;
}
