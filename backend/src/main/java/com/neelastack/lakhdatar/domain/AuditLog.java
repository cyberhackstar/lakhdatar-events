package com.neelastack.lakhdatar.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes; import lombok.Getter; import lombok.Setter; import java.time.Instant;
@Entity @Table(name="audit_logs") @Getter @Setter
public class AuditLog {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="actor_user_id") private Long actorUserId;
 @Column(nullable=false,length=80) private String action;
 @Column(name="entity_type",length=80) private String entityType;
 @Column(name="entity_id",length=80) private String entityId;
 @Column(name="correlation_id",length=80) private String correlationId;
 @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private String details;
 @Column(name="created_at",updatable=false) private Instant createdAt=Instant.now();
}
