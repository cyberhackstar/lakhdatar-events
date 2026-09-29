package com.neelastack.lakhdatar.controller;
import com.neelastack.lakhdatar.config.AppProperties; import com.neelastack.lakhdatar.service.*; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import jakarta.validation.constraints.*; import lombok.RequiredArgsConstructor; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1/public") @RequiredArgsConstructor
public class PublicEventController {
 private final PublicEventService events; private final TicketQueryService tickets; private final OrderService orders; private final AppProperties props; private final RateLimitService limits; private final ClientAddressService clientAddress;

 private static final java.time.ZoneId CATALOG_ZONE=java.time.ZoneId.of("Asia/Kolkata");
 private static final CacheControl PUBLIC_SHORT=CacheControl.maxAge(java.time.Duration.ofSeconds(20)).cachePublic().sMaxAge(java.time.Duration.ofSeconds(30)).staleWhileRevalidate(java.time.Duration.ofSeconds(60));
 /** Paginated, filtered catalogue. Availability is informational only; checkout is authoritative. */
 @GetMapping("/events") public ResponseEntity<PublicEventService.PageView<PublicEventService.EventCard>> list(
   @RequestParam(required=false) @Size(max=100) String q,@RequestParam(required=false) @Size(max=60) String category,
   @RequestParam(required=false) @Size(max=120) String city,@RequestParam(required=false) @Size(max=120) String organizer,
   @RequestParam(required=false) Boolean featured,@RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
   @RequestParam(required=false) @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
   @RequestParam(required=false) @Min(0) @Max(10000000) Long minPrice,@RequestParam(required=false) @Min(0) @Max(10000000) Long maxPrice,
   @RequestParam(defaultValue="0") @Min(0) @Max(1000) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size){
  var f=new PublicEventService.Filter(q,category,city,organizer,featured,from==null?null:from.atStartOfDay(CATALOG_ZONE).toInstant(),
    to==null?null:to.plusDays(1).atStartOfDay(CATALOG_ZONE).toInstant(),minPrice==null?null:minPrice*100,maxPrice==null?null:maxPrice*100);
  return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(events.list(f,page,size));}
 @GetMapping("/events/search") public ResponseEntity<PublicEventService.PageView<PublicEventService.EventCard>> search(@RequestParam @Size(min=1,max=100) String q,
   @RequestParam(defaultValue="0") @Min(0) @Max(1000) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size){
  return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(events.list(new PublicEventService.Filter(q,null,null,null,null,null,null,null,null),page,size));}
 @GetMapping("/events/featured") public ResponseEntity<PublicEventService.PageView<PublicEventService.EventCard>> featured(@RequestParam(defaultValue="6") @Min(1) @Max(12) int limit){
  return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(events.featured(limit));}
 @GetMapping("/events/upcoming") public ResponseEntity<PublicEventService.PageView<PublicEventService.EventCard>> upcoming(
   @RequestParam(defaultValue="0") @Min(0) @Max(1000) int page,@RequestParam(defaultValue="12") @Min(1) @Max(50) int size){
  return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(events.upcoming(page,size));}
 @GetMapping("/events/facets") public ResponseEntity<PublicEventService.Facets> facets(){return ResponseEntity.ok().cacheControl(PUBLIC_SHORT).body(events.facets());}
 @GetMapping("/events/{slug}") public ResponseEntity<PublicEventService.EventView> event(@PathVariable @Size(max=100) String slug){return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(10)).cachePublic()).body(events.getBySlug(slug));}
 public record RecoverBody(@NotBlank @Pattern(regexp="LK-[A-Z0-9]{12}") String orderNumber,@NotBlank @Email @Size(max=255) String email){}
 @PostMapping("/orders/recover") public ResponseEntity<RecoveryResponse> recover(@Valid @RequestBody RecoverBody b,HttpServletRequest req){String key=clientAddress.resolve(req);String emailKey=sha256RateKey(b.email().trim().toLowerCase());if(!limits.allow("recover:"+key,10,java.time.Duration.ofMinutes(5)) || !limits.allow("recover-order:"+sha256RateKey(b.orderNumber()),10,java.time.Duration.ofMinutes(5)) || !limits.allow("recover-email:"+emailKey,10,java.time.Duration.ofMinutes(5)))return ResponseEntity.status(429).build();return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(recoverOrder(b));}
 private RecoveryResponse recoverOrder(RecoverBody b){var o=orders.findForRecovery(b.orderNumber(),b.email());return new RecoveryResponse(o.orderNumber(),o.status().name(),o.eventName(),o.tickets().stream().map(x->new RecoveryTicket(x.ticketId(),x.ticketNumber(),x.accessToken())).toList());}
 public record RecoveryTicket(UUID ticketId,String ticketNumber,String accessToken){} public record RecoveryResponse(String orderNumber,String status,String eventName,List<RecoveryTicket> tickets){}
 private String sha256RateKey(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.trim().toUpperCase().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 @GetMapping("/tickets/{ticketId}") public ResponseEntity<TicketQueryService.TicketView> ticket(@PathVariable UUID ticketId,@RequestHeader(name="X-Ticket-Token",required=false) @Size(min=20,max=512) String token){if(token==null||token.isBlank())throw new com.neelastack.lakhdatar.exception.ApiException(HttpStatus.UNAUTHORIZED,"INVALID_TICKET_LINK","Ticket link is invalid or expired");return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tickets.get(ticketId,token));}
}
