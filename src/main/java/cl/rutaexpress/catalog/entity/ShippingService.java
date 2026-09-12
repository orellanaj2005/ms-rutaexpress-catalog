package cl.rutaexpress.catalog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.NumericBooleanConverter;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Catalog "service" offering (e.g. "Envio Express", "Envio Estandar").
 * NOTE: this is a JPA entity, not a Spring {@code @Service} - the business
 * logic class is named {@link cl.rutaexpress.catalog.service.CatalogService}
 * to avoid confusion between the two.
 */
@Entity
@Table(name = "SHIPPING_SERVICES")
public class ShippingService {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "shipping_services_seq")
    @SequenceGenerator(name = "shipping_services_seq", sequenceName = "SHIPPING_SERVICES_SEQ", allocationSize = 1)
    private Long id;

    @Column(name = "NAME", nullable = false, length = 150)
    private String name;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "RATE", nullable = false, precision = 10, scale = 2)
    private BigDecimal rate;

    @Column(name = "CAPACITY", nullable = false)
    private Integer capacity;

    // Oracle 23ai has a native BOOLEAN type, and Hibernate 7 maps Java boolean
    // to it by default on that dialect. The migration created ACTIVE as
    // NUMBER(1) (portable across older Oracle versions too), so this
    // converter tells Hibernate to keep treating it as 0/1 instead of
    // expecting the native BOOLEAN column type.
    @Convert(converter = NumericBooleanConverter.class)
    @Column(name = "ACTIVE", nullable = false)
    private boolean active;

    // ojdbc11 throws ORA-18716 when Hibernate 7's default JDBC type for
    // Instant (TimestampUtcAsOffsetDateTimeJdbcType) calls
    // getObject(col, OffsetDateTime.class) against a plain TIMESTAMP column.
    // Forcing the classic TIMESTAMP JDBC type keeps Instant as the Java type
    // but reads/writes via getTimestamp()/setTimestamp() instead, avoiding
    // the buggy driver code path.
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "CREATED_AT", nullable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "UPDATED_AT", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "VERSION", nullable = false)
    private int version;

    protected ShippingService() {
        // JPA
    }

    public ShippingService(String name, String description, BigDecimal rate, Integer capacity, boolean active) {
        this.name = name;
        this.description = description;
        this.rate = rate;
        this.capacity = capacity;
        this.active = active;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public int getVersion() {
        return version;
    }
}
