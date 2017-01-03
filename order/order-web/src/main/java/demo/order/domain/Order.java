package demo.order.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import demo.domain.AbstractEntity;
import demo.domain.Aggregate;
import demo.domain.Command;
import demo.domain.Module;
import demo.order.action.*;
import demo.order.controller.OrderController;
import demo.order.event.OrderEvent;
import demo.payment.domain.Payment;
import org.springframework.hateoas.Link;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;

@Entity(name = "orders")
public class Order extends AbstractEntity<OrderEvent, Long> {
    @Id
    @GeneratedValue
    private Long id;

    @Enumerated(value = EnumType.STRING)
    private OrderStatus status;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private Set<LineItem> lineItems = new HashSet<>();

    @OneToOne(cascade = CascadeType.ALL)
    private Address shippingAddress;

    private Long accountId, paymentId;

    public Order() {
        this.status = OrderStatus.ORDER_CREATED;
    }

    public Order(Long accountId, Address shippingAddress) {
        this();
        this.accountId = accountId;
        this.shippingAddress = shippingAddress;
        if (shippingAddress.getAddressType() == null)
            this.shippingAddress.setAddressType(AddressType.SHIPPING);
    }

    @JsonProperty("orderId")
    @Override
    public Long getIdentity() {
        return this.id;
    }

    @Override
    public void setIdentity(Long id) {
        this.id = id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Set<LineItem> getLineItems() {
        return lineItems;
    }

    public void setLineItems(Set<LineItem> lineItems) {
        this.lineItems = lineItems;
    }

    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    @Command(method = "connectAccount", controller = OrderController.class)
    public Order connectAccount(Long accountId) {
        return getAction(ConnectAccount.class)
                .getFunction()
                .apply(this, accountId);
    }

    @Command(method = "connectPayment", controller = OrderController.class)
    public Order connectPayment(Long paymentId) {
        return getAction(ConnectPayment.class)
                .getFunction()
                .apply(this, paymentId);
    }

    @Command(method = "createPayment", controller = OrderController.class)
    public Order createPayment() {
        return getAction(CreatePayment.class)
                .getFunction()
                .apply(this);
    }

    @Command(method = "processPayment", controller = OrderController.class)
    public Order processPayment() {
        return getAction(ProcessPayment.class)
                .getFunction()
                .apply(this);
    }

    @Command(method = "reserveInventory", controller = OrderController.class)
    public Order reserveInventory(Long paymentId) {
        getAction(ReserveInventory.class)
                .getConsumer()
                .accept(this);
        return this;
    }

    public boolean delete() {
        getAction(DeleteOrder.class)
                .getConsumer()
                .accept(this);

        return true;
    }

    @JsonIgnore
    public Double calculateTotal() {
        return getLineItems()
                .stream()
                .mapToDouble(a -> (a.getPrice() + a.getTax()) * a.getQuantity())
                .sum();
    }

    @JsonIgnore
    public Payment getPayment() {
        Payment result = null;

        if (paymentId != null)
            result = getModule(OrderModule.class).getPaymentService().get(paymentId);

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Module<A>, A extends Aggregate<OrderEvent, Long>> T getModule() throws
            IllegalArgumentException {
        OrderModule orderModule = getModule(OrderModule.class);
        return (T) orderModule;
    }

    /**
     * Returns the {@link Link} with a rel of {@link Link#REL_SELF}.
     */
    @Override
    public Link getId() {
        return linkTo(OrderController.class)
                .slash("orders")
                .slash(getIdentity())
                .withSelfRel();
    }
}