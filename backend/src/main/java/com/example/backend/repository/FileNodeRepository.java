package com.example.backend.repository;

import com.example.backend.entity.FileNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileNodeRepository extends JpaRepository<FileNode, Long> {
    List<FileNode> findByRoomId(Long roomId);
    List<FileNode> findByParentId(Long parentId);
    void deleteByRoomId(Long roomId);
}
