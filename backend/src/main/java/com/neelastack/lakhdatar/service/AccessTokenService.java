package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class AccessTokenService {
    private final byte[] secret;
    private final byte[] previousSecret;
    private final long ttlSeconds;

    public AccessTokenService(AppProperties props){
        String current=props.security().ticketViewSecret();
        if(current==null || current.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("TICKET_VIEW_SECRET must be at least 32 bytes");
        secret=current.getBytes(StandardCharsets.UTF_8);
        String previous=props.security().ticketViewPreviousSecret();
        previousSecret=previous==null || previous.isBlank() ? null : previous.getBytes(StandardCharsets.UTF_8);
        if(previousSecret!=null && previousSecret.length<32) throw new IllegalStateException("TICKET_VIEW_SECRET_PREVIOUS must be at least 32 bytes when configured");
        ttlSeconds = Math.max(300L, props.security().ticketViewTtl().toSeconds());
    }
    public String issue(UUID ticketId){ long exp=Instant.now().getEpochSecond()+ttlSeconds; String body="TV1|"+ticketId+"|"+exp; return body.replace('|','.')+"."+sign(body,secret); }
    public boolean verify(String token,UUID ticketId){
        try{
            String[] p=token.split("\\."); if(p.length!=4 || !"TV1".equals(p[0]) || !ticketId.toString().equals(p[1])) return false;
            long exp=Long.parseLong(p[2]); if(exp<Instant.now().getEpochSecond()) return false;
            String body="TV1|"+p[1]+"|"+p[2];
            if(constantTime(sign(body,secret),p[3])) return true;
            return previousSecret!=null && constantTime(sign(body,previousSecret),p[3]);
        }catch(Exception e){return false;}
    }
    private String sign(String body, byte[] key){
        try{ Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key,"HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("Ticket token signing failed",e);}
    }
    private boolean constantTime(String a,String b){ if(a==null||b==null)return false; return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII)); }
}
