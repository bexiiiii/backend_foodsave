package com.foodsave.backend.dto;

import com.foodsave.backend.entity.CartItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class CartItemDTO {
    private Long id;
    
    private Long cartId;
    
    @NotNull
    private Long productId;
    
    private String productName;
    
    private List<String> productImages;
    
    @NotNull
    @Min(1)
    private Integer quantity;
    
    private BigDecimal price;
    
    private BigDecimal discountPrice;
    
    private BigDecimal subtotal;
    
    private BigDecimal discount;
    
    private BigDecimal total;
    
    public static CartItemDTO fromEntity(CartItem item) {
        List<String> imageUrls = new ArrayList<>();
        if (item.getProduct() != null && item.getProduct().getImages() != null) {
            for (Object img : item.getProduct().getImages()) {
                // If images is List<String>
                if (img instanceof String) {
                    imageUrls.add((String) img);
                }
                // If images is List<ProductImage>
                else if (img != null && img.getClass().getSimpleName().equals("ProductImage")) {
                    try {
                        imageUrls.add((String) img.getClass().getMethod("getUrl").invoke(img));
                    } catch (Exception ignore) {}
                }
            }
        }
        return CartItemDTO.builder()
                .id(item.getId())
                .cartId(item.getCart().getId())
                .productId(item.getProduct().getId())
                .productName(item.getProduct().getName())
                .productImages(imageUrls)
                .quantity(item.getQuantity())
                .price(item.getPrice())
                .discountPrice(item.getDiscountPrice())
                .subtotal(item.getSubtotal())
                .discount(item.getDiscount())
                .total(item.getTotal())
                .build();
    }
}
