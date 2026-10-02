package com.neelastack.lakhdatar.service;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PaymentProviderContractTest {
 @Test void providerSelectionIsPersistedAndGuarded(){
   String event=read("src/main/java/com/neelastack/lakhdatar/domain/Event.java");
   String migration=read("src/main/resources/db/migration/V13__payment_provider_abstraction.sql");
   String management=read("src/main/java/com/neelastack/lakhdatar/service/EventManagementService.java");
   assertTrue(event.contains("PaymentProvider"));
   assertTrue(migration.contains("CASHFREE") && migration.contains("RAZORPAY"));
   assertTrue(management.contains("Payment provider cannot be changed after payment activity exists"));
 }
 @Test void checkoutIsProviderNeutral(){
   String order=read("src/main/java/com/neelastack/lakhdatar/service/OrderService.java");
   assertTrue(order.contains("PaymentGatewayRouter"));
   assertTrue(order.contains("verifyPaymentSignature"));
   assertTrue(order.contains("fetchPaymentsForOrder"));
 }
 private String read(String p){try{return Files.readString(Path.of(p));}catch(Exception e){throw new AssertionError(e);}}
}
