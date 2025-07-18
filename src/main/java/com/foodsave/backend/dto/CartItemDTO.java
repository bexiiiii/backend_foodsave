package com.foodsave.backend.dto;

import com.foodsave.backend.entity.CartItem;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

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
    
    public static CartItemDTO fromEntity(CartItem cartItem) {
        return CartItemDTO.builder()
                .id(cartItem.getId())
                .cartId(cartItem.getCart().getId())
                .productId(cartItem.getProduct().getId())
                .productName(cartItem.getProduct().getName())
                .productImages(cartItem.getProduct().getImages())
                .quantity(cartItem.getQuantity())
                .price(cartItem.getPrice())
                .discountPrice(cartItem.getDiscountPrice())
                .subtotal(cartItem.getSubtotal())
                .discount(cartItem.getDiscount())
                .total(cartItem.getTotal())
                .build();
    }
}
