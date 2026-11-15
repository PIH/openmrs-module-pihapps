package org.openmrs.module.pihapps.orders;

import lombok.Getter;
import lombok.Setter;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.Order;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Date;

/**
 * Something that happened to an order during its fulfillment, such as results being submitted for validation
 */
@Entity
@Table(name = "pihapps_order_fulfillment_event")
@Getter
@Setter
public class OrderFulfillmentEvent extends BaseOpenmrsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_fulfillment_event_id")
    private Integer orderFulfillmentEventId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OrderFulfillmentEventType eventType;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Column(name = "event_datetime", nullable = false)
    private Date eventDatetime;

    @Override
    public Integer getId() {
        return orderFulfillmentEventId;
    }

    @Override
    public void setId(Integer id) {
        this.orderFulfillmentEventId = id;
    }
}
