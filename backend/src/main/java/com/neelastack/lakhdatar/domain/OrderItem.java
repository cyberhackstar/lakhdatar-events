package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter;
@Entity @Table(name="order_items") @Getter @Setter
public class OrderItem {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="order_id",nullable=false) private Long orderId;
 @Column(name="ticket_type_id",nullable=false) private Long ticketTypeId;
 @Column(nullable=false) private Integer quantity;
 @Column(name="unit_price_minor",nullable=false) private Long unitPriceMinor;
 @Column(name="subtotal_minor",nullable=false) private Long subtotalMinor;
}
