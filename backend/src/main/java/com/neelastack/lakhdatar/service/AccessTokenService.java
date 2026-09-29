package com.neelastack.lakhdatar.service;

import com.neelastack.lakhdatar.config.AppProperties;
import org.springframework.stereotype.Service;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.time.Instant; import java.util.Base64; import java.util.UUID;

@Service
public class AccessTokenService {
    private final byte[] secret; private final long ttlSeconds;
    public AccessTokenService(AppProperties props){
        String s=props.security().ticketViewSecret(); if(s==null || s.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("TICKET_VIEW_SECRET must be at least 32 bytes");
        secret=s.getBytes(StandardCharsets.UTF_8);
        ttlSeconds = Math.max(300L, props.security().ticketViewTtl().toSeconds());
    }
    public String issue(UUID ticketId){ long exp=Instant.now().getEpochSecond()+ttlSeconds; String body="TV1|"+ticketId+"|"+exp; return body.replace('|','.')+"."+sign(body); }
    public boolean verify(String token,UUID ticketId){
        try{
            String[] p=token.split("\\."); if(p.length!=4 || !"TV1".equals(p[0]) || !ticketId.toString().equals(p[1])) return false;
            long exp=Long.parseLong(p[2]); if(exp<Instant.now().getEpochSecond()) return false;
            return constantTime(sign("TV1|"+p[1]+"|"+p[2]),p[3]);
        }catch(Exception e){return false;}
    }
    private String sign(String body){
        try{ Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret,"HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("Ticket token signing failed",e);}
    }
    private boolean constantTime(String a,String b){ if(a==null||b==null)return false; return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII),b.getBytes(StandardCharsets.US_ASCII)); }
}
