package com.neelastack.lakhdatar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neelastack.lakhdatar.domain.AuditLog;
import com.neelastack.lakhdatar.repository.AuditLogRepository;
import com.neelastack.lakhdatar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;

    public void log(Long actor,String action,String type,String id,String correlation){
        AuditLog a=new AuditLog();
        a.setActorUserId(actor); a.setAction(action); a.setEntityType(type); a.setEntityId(id);
        a.setCorrelationId(correlation!=null?correlation:MDC.get("correlationId"));
        LinkedHashMap<String,Object> details=new LinkedHashMap<>();
        Authentication auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof UserPrincipal p){
            details.put("actorRole",p.role());
        }
        put(details,"sourceIp",MDC.get("clientIp"));
        put(details,"userAgent",MDC.get("userAgent"));
        put(details,"correlationId",a.getCorrelationId());
        try { a.setDetails(objectMapper.writeValueAsString(details)); }
        catch (JsonProcessingException ignored) { a.setDetails("{}"); }
        repo.save(a);
    }

    private void put(LinkedHashMap<String,Object> details,String key,String value){
        if(value!=null && !value.isBlank()) details.put(key,value);
    }
}
