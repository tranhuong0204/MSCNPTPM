package com.pm.authservice.infrastructure.repo;

import com.pm.authservice.domain.FaceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FaceDataRepository extends JpaRepository<FaceData, UUID> {

}
