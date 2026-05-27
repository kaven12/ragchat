package com.example.ragchat.repository;

import com.example.ragchat.entity.File;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, String> {
    
    Page<File> findByUserIdAndIsDeletedFalse(String userId, Pageable pageable);
    
    Page<File> findByUserIdAndDirectoryIdAndIsDeletedFalse(String userId, String directoryId, Pageable pageable);
    
    Optional<File> findByIdAndUserIdAndIsDeletedFalse(String id, String userId);
    
    boolean existsByStorageKey(String storageKey);
    
    List<File> findByIdIn(List<String> ids);
}
