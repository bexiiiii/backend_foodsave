package com.foodsave.backend.service;

import com.foodsave.backend.entity.Product;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.Category;
import com.foodsave.backend.dto.ProductDTO;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.repository.StoreRepository;
import com.foodsave.backend.repository.CategoryRepository;
import com.foodsave.backend.domain.enums.ProductStatus;
import com.foodsave.backend.util.SecurityUtil;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final SecurityUtil securityUtil;
    private final com.foodsave.backend.repository.UserRepository userRepository;

    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        log.info("DEBUG: Getting all products");
        log.info("DEBUG: Current user admin check: {}", securityUtil.isCurrentUserAdmin());
        
        if (securityUtil.isCurrentUserAdmin()) {
            // Super admins see all products
            log.info("DEBUG: User is admin, fetching all products");
            Page<Product> products = productRepository.findAll(pageable);
            log.info("DEBUG: Found {} products in database", products.getTotalElements());
            return products.map(this::convertToDTO);
        } else {
            // Check if user is a store manager
            org.springframework.security.core.Authentication authentication = 
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.getPrincipal() instanceof com.foodsave.backend.security.UserPrincipal) {
                com.foodsave.backend.security.UserPrincipal userPrincipal = 
                    (com.foodsave.backend.security.UserPrincipal) authentication.getPrincipal();
                
                if (userPrincipal.getRole() == com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER) {
                    // Manager sees only products from their managed store
                    log.info("DEBUG: User is store manager, fetching managed store products");
                    Long managedStoreId = getCurrentManagedStoreId(userPrincipal.getId());
                    if (managedStoreId != null) {
                        Page<Product> products = productRepository.findByStoreId(managedStoreId, pageable);
                        log.info("DEBUG: Found {} products for managed store {}", products.getTotalElements(), managedStoreId);
                        return products.map(this::convertToDTO);
                    }
                }
            }
            
            // Store users see only their store's products
            log.info("DEBUG: User is store owner, fetching store products");
            Set<Long> userStoreIds = securityUtil.getCurrentUserStoreIds();
            log.info("DEBUG: User store IDs: {}", userStoreIds);
            if (userStoreIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return productRepository.findByStoreIdIn(userStoreIds, pageable)
                    .map(this::convertToDTO);
        }
    }

    private Long getCurrentManagedStoreId(Long managerId) {
        try {
            com.foodsave.backend.entity.User manager = userRepository.findById(managerId).orElse(null);
            
            if (manager != null) {
                // Поиск заведения, которым управляет данный менеджер
                org.springframework.data.domain.Page<com.foodsave.backend.entity.Store> stores = 
                    storeRepository.findByManager(manager, org.springframework.data.domain.Pageable.unpaged());
                
                if (!stores.getContent().isEmpty()) {
                    return stores.getContent().get(0).getId();
                }
            }
        } catch (Exception e) {
            log.error("Error finding managed store: ", e);
        }
        return null;
    }

    public ProductDTO getProductById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return convertToDTO(product);
    }

    public Page<ProductDTO> getProductsByStore(Long storeId, Pageable pageable) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Store not found"));
        return productRepository.findByStore(store, pageable)
                .map(this::convertToDTO);
    }

    public List<String> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(Category::getName)
                .collect(Collectors.toList());
    }

    public Page<ProductDTO> getFeaturedProducts(Pageable pageable) {
        return productRepository.findByDiscountPercentageGreaterThan(0.0, pageable)
                .map(this::convertToDTO);
    }

    public ProductDTO createProduct(ProductDTO productDTO) {
        Store store = storeRepository.findById(productDTO.getStoreId())
                .orElseThrow(() -> new EntityNotFoundException("Store not found"));
        Category category = categoryRepository.findById(productDTO.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));

        Product product = new Product();
        updateProductFromDTO(product, productDTO);
        product.setStore(store);
        product.setCategory(category);
        return convertToDTO(productRepository.save(product));
    }

    public ProductDTO updateProduct(Long id, ProductDTO productDTO) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        Category category = categoryRepository.findById(productDTO.getCategoryId())
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        
        updateProductFromDTO(product, productDTO);
        product.setCategory(category);
        return convertToDTO(productRepository.save(product));
    }

    public void deleteProduct(Long id) {
        productRepository.deleteById(id);
    }

    public Page<ProductDTO> searchProducts(String query, Pageable pageable) {
        return productRepository.searchProducts(query, pageable)
                .map(this::convertToDTO);
    }

    public Page<ProductDTO> getProductsByCategory(Long categoryId, Pageable pageable) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found"));
        return productRepository.findByCategory(category, pageable)
                .map(this::convertToDTO);
    }

    public Page<ProductDTO> getDiscountedProducts(Pageable pageable) {
        return productRepository.findByDiscountPercentageGreaterThan(0.0, pageable)
                .map(this::convertToDTO);
    }

    public Page<ProductDTO> getLowStockProducts(Integer threshold, Pageable pageable) {
        return productRepository.findByStockQuantityLessThanEqual(threshold, pageable)
                .map(this::convertToDTO);
    }

    public Page<ProductDTO> getExpiringProducts(Pageable pageable) {
        return productRepository.findByExpiryDateIsNotNull(pageable)
                .map(this::convertToDTO);
    }

    public ProductDTO updateProductStatus(Long id, ProductStatus status) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        product.setStatus(status);
        return convertToDTO(productRepository.save(product));
    }

    /**
     * Update stock quantity for a product
     */
    public ProductDTO updateStockQuantity(Long productId, Integer newQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative");
        }
        
        product.setStockQuantity(newQuantity);
        return convertToDTO(productRepository.save(product));
    }

    /**
     * Reduce stock quantity by specified amount
     */
    public ProductDTO reduceStockQuantity(Long productId, Integer quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        
        if (product.getStockQuantity() < quantity) {
            throw new IllegalArgumentException("Insufficient stock. Available: " + 
                product.getStockQuantity() + ", Requested: " + quantity);
        }
        
        product.setStockQuantity(product.getStockQuantity() - quantity);
        return convertToDTO(productRepository.save(product));
    }

    /**
     * Check if product has sufficient stock
     */
    public boolean hasSufficientStock(Long productId, Integer requiredQuantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));
        return product.getStockQuantity() >= requiredQuantity;
    }

    private ProductDTO convertToDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .originalPrice(product.getOriginalPrice())
                .discountPercentage(product.getDiscountPercentage())
                .stockQuantity(product.getStockQuantity())
                .storeId(product.getStore().getId())
                .storeName(product.getStore().getName())
                .storeLogo(product.getStore().getLogo())
                .storeAddress(product.getStore().getAddress())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .images(product.getImages())
                .expiryDate(product.getExpiryDate())
                .status(product.getStatus())
                .active(product.getActive())
                // Computed properties for frontend compatibility
                .isAvailable(product.getActive() && 
                           product.getStatus() == ProductStatus.AVAILABLE && 
                           product.getStockQuantity() > 0)
                .availableQuantity(product.getStockQuantity())
                .imageUrl(!product.getImages().isEmpty() ? product.getImages().get(0) : null)
                .expirationDate(product.getExpiryDate() != null ? product.getExpiryDate().toString() : null)
                .isFeatured(product.getDiscountPercentage() != null && product.getDiscountPercentage() > 0)
                .rating(0.0) // Default rating for now
                .build();
    }

    private void updateProductFromDTO(Product product, ProductDTO dto) {
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setPrice(dto.getPrice());
        product.setOriginalPrice(dto.getOriginalPrice());
        product.setDiscountPercentage(dto.getDiscountPercentage());
        product.setStockQuantity(dto.getStockQuantity());
        product.setImages(dto.getImages());
        product.setExpiryDate(dto.getExpiryDate());
        product.setStatus(dto.getStatus());
        product.setActive(dto.getActive());
    }
}
