package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*; import lombok.Getter; import lombok.Setter; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="ticket_types") @Getter @Setter
public class TicketType {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId=UUID.randomUUID();
 @Column(name="event_id",nullable=false) private Long eventId;
 @Column(nullable=false,length=120) private String name;
 @Column(columnDefinition="text") private String description;
 @Column(name="price_minor_units",nullable=false) private Long priceMinorUnits;
 @Column(nullable=false,length=8) private String currency="INR";
 @Column(name="total_quantity",nullable=false) private Integer totalQuantity;
 @Column(name="reserved_quantity",nullable=false) private Integer reservedQuantity=0;
 @Column(name="sold_quantity",nullable=false) private Integer soldQuantity=0;
 @Column(name="min_per_order",nullable=false) private Integer minPerOrder=1;
 @Column(name="max_per_order",nullable=false) private Integer maxPerOrder=10;
 @Column(name="sale_starts_at") private Instant saleStartsAt;
 @Column(name="sale_ends_at") private Instant saleEndsAt;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private Enums.TicketTypeStatus status=Enums.TicketTypeStatus.ACTIVE;
 @Version @Column(nullable=false) private Long version; // null until first persist so Spring Data uses persist(), not merge()
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
 public int availableQuantity(){return totalQuantity-reservedQuantity-soldQuantity;}
}
