package com.playground.pizza.orders;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  // SELECT ... FOR UPDATE: serialises concurrent payment callbacks for the same order.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(UUID id);

  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
  List<Order> findByStatusInOrderByCreatedAtDesc(Collection<OrderStatus> statuses);
  // Half-open [from, to) so a day range needs no end-of-day arithmetic.
  List<Order> findByStatusInAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
      Collection<OrderStatus> statuses, Instant from, Instant to);
}
