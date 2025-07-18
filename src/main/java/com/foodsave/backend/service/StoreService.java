package com.foodsave.backend.service;

import com.foodsave.backend.dto.StoreDTO;
import com.foodsave.backend.dto.UserDTO;
import com.foodsave.backend.entity.Store;
import com.foodsave.backend.entity.User;
import com.foodsave.backend.domain.enums.StoreStatus;
import com.foodsave.backend.exception.ResourceNotFoundException;
import com.foodsave.backend.repository.StoreRepository;
import com.foodsave.backend.repository.UserRepository;
import com.foodsave.backend.repository.ProductRepository;
import com.foodsave.backend.security.SecurityUtils;
import com.foodsave.backend.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final SecurityUtils securityUtils;
    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;

    public Page<StoreDTO> getAllStores(Pageable pageable) {
        return storeRepository.findAll(pageable)
                .map(StoreDTO::fromEntity);
    }

    public List<StoreDTO> getActiveStores() {
        return storeRepository.findByActiveAndStatus(true, StoreStatus.ACTIVE)
                .stream()
                .map(this::convertToStoreDTO)
                .toList();
    }

    private StoreDTO convertToStoreDTO(Store store) {
        StoreDTO dto = StoreDTO.fromEntity(store);
        // Добавляем количество активных продуктов
        long productCount = productRepository.countActiveByStoreId(store.getId());
        dto.setProductCount((int) productCount);
        return dto;
    }

    public StoreDTO getStoreById(Long id) {
        return storeRepository.findById(id)
                .map(StoreDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));
    }

    public StoreDTO createStore(StoreDTO storeDTO) {
        // Find existing user by email
        User existingUser = userRepository.findByEmail(storeDTO.getUser().getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + storeDTO.getUser().getEmail()));

        // Create store
        Store store = new Store();
        store.setName(storeDTO.getName());
        store.setDescription(storeDTO.getDescription());
        store.setAddress(storeDTO.getAddress());
        store.setPhone(storeDTO.getPhone());
        store.setEmail(storeDTO.getEmail());
        store.setLogo(storeDTO.getLogo());
        store.setOpeningHours(storeDTO.getOpeningHours());
        store.setClosingHours(storeDTO.getClosingHours());
        store.setCategory(storeDTO.getCategory());
        store.setActive(storeDTO.isActive());
        store.setStatus(storeDTO.getStatus());
        store.setOwner(existingUser); // Link with existing user

        // Назначаем менеджера, если указан
        if (storeDTO.getManagerId() != null) {
            User manager = userRepository.findById(storeDTO.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + storeDTO.getManagerId()));
            
            // Изменяем роль пользователя на STORE_MANAGER
            manager.setRole(com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER);
            userRepository.save(manager);
            
            store.setManager(manager);
        }

        Store savedStore = storeRepository.save(store);
        StoreDTO responseDTO = StoreDTO.fromEntity(savedStore);
        responseDTO.setUser(UserDTO.fromEntity(existingUser));
        return responseDTO;
    }

    public StoreDTO createStore(StoreDTO storeDTO, String ownerEmail) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + ownerEmail));
        Store store = new Store();
        updateStoreFromDTO(store, storeDTO);
        store.setOwner(owner);
        
        // Назначаем менеджера, если указан
        if (storeDTO.getManagerId() != null) {
            User manager = userRepository.findById(storeDTO.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + storeDTO.getManagerId()));
            
            // Изменяем роль пользователя на STORE_MANAGER
            manager.setRole(com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER);
            userRepository.save(manager);
            
            store.setManager(manager);
        }
        
        store.setStatus(StoreStatus.PENDING);
        return StoreDTO.fromEntity(storeRepository.save(store));
    }

    public StoreDTO updateStore(Long id, StoreDTO storeDTO) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));

        store.setName(storeDTO.getName());
        store.setDescription(storeDTO.getDescription());
        store.setAddress(storeDTO.getAddress());
        store.setPhone(storeDTO.getPhone());
        store.setEmail(storeDTO.getEmail());
        store.setLogo(storeDTO.getLogo());
        store.setOpeningHours(storeDTO.getOpeningHours());
        store.setClosingHours(storeDTO.getClosingHours());
        store.setCategory(storeDTO.getCategory());
        store.setActive(storeDTO.isActive());
        store.setStatus(storeDTO.getStatus());

        // Обновляем менеджера
        if (storeDTO.getManagerId() != null) {
            // Сбрасываем роль предыдущего менеджера, если он был
            if (store.getManager() != null) {
                User previousManager = store.getManager();
                previousManager.setRole(com.foodsave.backend.domain.enums.UserRole.CUSTOMER);
                userRepository.save(previousManager);
            }
            
            // Назначаем нового менеджера
            User newManager = userRepository.findById(storeDTO.getManagerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with id: " + storeDTO.getManagerId()));
            
            newManager.setRole(com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER);
            userRepository.save(newManager);
            
            store.setManager(newManager);
        } else {
            // Если менеджер не указан, сбрасываем роль текущего менеджера
            if (store.getManager() != null) {
                User currentManager = store.getManager();
                currentManager.setRole(com.foodsave.backend.domain.enums.UserRole.CUSTOMER);
                userRepository.save(currentManager);
                store.setManager(null);
            }
        }

        Store savedStore = storeRepository.save(store);
        StoreDTO responseDTO = StoreDTO.fromEntity(savedStore);
        responseDTO.setUser(UserDTO.fromEntity(store.getOwner()));
        return responseDTO;
    }

    public void deleteStore(Long id) {
        if (!storeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Store not found with id: " + id);
        }
        storeRepository.deleteById(id);
    }

    public StoreDTO updateStoreStatus(Long id, StoreStatus status) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + id));
        store.setStatus(status);
        return StoreDTO.fromEntity(storeRepository.save(store));
    }

    public Page<StoreDTO> searchStores(String query, Pageable pageable) {
        return storeRepository.searchStores(query, pageable)
                .map(StoreDTO::fromEntity);
    }

    public Page<StoreDTO> findNearbyStores(double latitude, double longitude, double radius, Pageable pageable) {
        // Convert radius from kilometers to degrees (approximate)
        double latDelta = radius / 111.0;
        double lngDelta = radius / (111.0 * Math.cos(Math.toRadians(latitude)));
        
        return storeRepository.findStoresInArea(
                latitude - latDelta,
                latitude + latDelta,
                longitude - lngDelta,
                longitude + lngDelta,
                pageable
        ).map(StoreDTO::fromEntity);
    }

    public Page<StoreDTO> getStoresByLocation(String location, Pageable pageable) {
        return storeRepository.findByAddressContaining(location, pageable)
                .map(StoreDTO::fromEntity);
    }

    public Page<StoreDTO> getStoresByOwner(String ownerEmail, Pageable pageable) {
        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + ownerEmail));
        return storeRepository.findByOwner(owner, pageable)
                .map(StoreDTO::fromEntity);
    }

    public StoreDTO getCurrentUserStore() {
        User currentUser = securityUtils.getCurrentUser();
        return storeRepository.findByOwner(currentUser)
                .map(StoreDTO::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found for current user"));
    }

    public StoreDTO getCurrentUserManagedStore() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication != null && authentication.getPrincipal() instanceof com.foodsave.backend.security.UserPrincipal) {
            com.foodsave.backend.security.UserPrincipal userPrincipal = 
                (com.foodsave.backend.security.UserPrincipal) authentication.getPrincipal();
            
            if (userPrincipal.getRole() == com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER) {
                User manager = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found"));
                
                Store store = storeRepository.findByManager(manager, Pageable.unpaged())
                    .getContent().stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("No store found for this manager"));
                
                return StoreDTO.fromEntity(store);
            }
        }
        
        throw new ResourceNotFoundException("Current user is not a manager or no managed store found");
    }

    private void updateStoreFromDTO(Store store, StoreDTO dto) {
        store.setName(dto.getName());
        store.setDescription(dto.getDescription());
        store.setAddress(dto.getAddress());
        store.setPhone(dto.getPhone());
        store.setEmail(dto.getEmail());
        store.setLogo(dto.getLogo());
        store.setOpeningHours(dto.getOpeningHours());
        store.setClosingHours(dto.getClosingHours());
        store.setCategory(dto.getCategory());
        store.setActive(dto.isActive());
        store.setStatus(dto.getStatus());
    }

    /**
     * Assign a user to a store
     */
    public void assignUserToStore(Long storeId, Long userId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + storeId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        // Add store to user's stores
        user.getStores().add(store);
        userRepository.save(user);
    }

    /**
     * Unassign a user from a store
     */
    public void unassignUserFromStore(Long storeId, Long userId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + storeId));
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        
        // Remove store from user's stores
        user.getStores().remove(store);
        userRepository.save(user);
    }

    /**
     * Get users assigned to a store
     */
    public Page<UserDTO> getStoreUsers(Long storeId, Pageable pageable) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found with id: " + storeId));
        
        return userRepository.findByStoresContaining(store, pageable)
                .map(UserDTO::fromEntity);
    }

    public Page<StoreDTO> getStoresByManager(String managerEmail, Pageable pageable) {
        User manager = userRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with email: " + managerEmail));
        
        return storeRepository.findByManager(manager, pageable)
                .map(StoreDTO::fromEntity);
    }

    public Optional<StoreDTO> getCurrentManagerStore() {
        String currentUserEmail = securityUtil.getCurrentUserEmail();
        if (currentUserEmail == null) {
            return Optional.empty();
        }
        
        User user = userRepository.findByEmail(currentUserEmail)
                .orElse(null);
        
        if (user == null || user.getRole() != com.foodsave.backend.domain.enums.UserRole.STORE_MANAGER) {
            return Optional.empty();
        }
        
        Optional<Store> store = storeRepository.findByManager(user);
        return store.map(StoreDTO::fromEntity);
    }

    public Long getCurrentManagerStoreId() {
        return getCurrentManagerStore()
                .map(StoreDTO::getId)
                .orElse(null);
    }
}
