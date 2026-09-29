package com.neelastack.lakhdatar.controller;
import com.neelastack.lakhdatar.security.UserPrincipal;
import com.neelastack.lakhdatar.service.CheckInService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/checkin") @RequiredArgsConstructor
public class CheckInController {
 private final CheckInService service;
 public record Body(@NotNull UUID eventId,@NotBlank @Size(max=512) String qrToken,@NotBlank @Size(max=80) String gate){}
 @PostMapping("/scan") CheckInService.ScanResponse scan(@Valid @RequestBody Body b, Authentication a){UserPrincipal p=(UserPrincipal)a.getPrincipal();var r=service.scan(new CheckInService.ScanRequest(b.qrToken(),b.eventId(),p.userId(),p.role(),b.gate(),MDC.get("correlationId")));return new CheckInService.ScanResponse(r.result().name(),r.message(),r.ticket()==null?null:r.ticket().getTicketNumber(),r.ticket()==null?null:r.ticket().getAttendeeName(),r.ticketType(),r.ticket()==null?null:r.ticket().getCheckedInAt(),r.ticketSource(),r.issuedByName());}
}
