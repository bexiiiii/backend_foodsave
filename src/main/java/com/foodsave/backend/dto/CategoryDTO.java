package com.foodsave.backend.dto;

import com.foodsave.backend.entity.Category;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;

@Data
@Builder
public class CategoryDTO {
    private Long id;
    
    @NotBlank
    private String name;
    
    private String description;
    
    private String imageUrl;
    
    private boolean active;
    
    public static CategoryDTO fromEntity(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .active(category.isActive())
                .build();
    }
} 