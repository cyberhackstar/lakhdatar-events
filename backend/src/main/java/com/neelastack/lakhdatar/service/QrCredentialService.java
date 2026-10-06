package com.neelastack.lakhdatar.service;

import com.google.zxing.BarcodeFormat; import com.google.zxing.EncodeHintType; import com.google.zxing.WriterException; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix; import com.google.zxing.qrcode.QRCodeWriter; import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.neelastack.lakhdatar.config.AppProperties;
import org.springframework.stereotype.Service;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec; import java.io.ByteArrayOutputStream; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.Base64; import java.util.EnumMap; import java.util.Map; import java.util.UUID; import java.util.Objects; import java.util.LinkedHashMap; import java.util.Collections;

@Service
public class QrCredentialService {
    private final byte[] secret; private final byte[] previousSecret; private final int imageSize;
    private static final int MAX_PNG_CACHE_ENTRIES = 1000;
    private final Map<String,String> pngCache = Collections.synchronizedMap(new LinkedHashMap<>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> eldest) { return size() > MAX_PNG_CACHE_ENTRIES; }
    });
    public QrCredentialService(AppProperties props){
        Objects.requireNonNull(props, "AppProperties must be configured");
        AppProperties.Qr qr=Objects.requireNonNull(props.qr(), "app.qr configuration is required");
        String s=qr.signingSecret(); if(s==null||s.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("QR_SIGNING_SECRET must be at least 32 bytes");
        secret=s.getBytes(StandardCharsets.UTF_8); String previous=qr.previousSigningSecret(); previousSecret=previous==null||previous.isBlank()?null:previous.getBytes(StandardCharsets.UTF_8);
        if(previousSecret!=null&&previousSecret.length<32) throw new IllegalStateException("QR_SIGNING_SECRET_PREVIOUS must be at least 32 bytes when configured"); imageSize=qr.imageSize();
    }
    public String credentialFor(UUID ticketPublicId){ return "LK1."+ticketPublicId+"."+sign("QR|"+ticketPublicId,secret); }
    public String hash(String credential){
        try{ byte[] h=MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8)); return Base64.getUrlEncoder().withoutPadding().encodeToString(h); }catch(Exception e){throw new IllegalStateException(e);}
    }
    public boolean verifyCredential(String credential,UUID ticketPublicId,String expectedHash){
        if(credential==null||expectedHash==null)return false;
        String body="QR|"+ticketPublicId;
        String[] p=credential.split("\\."); if(p.length!=3||!"LK1".equals(p[0])||!ticketPublicId.toString().equals(p[1])) return false;
        boolean signatureValid=MessageDigest.isEqual(sign(body,secret).getBytes(StandardCharsets.US_ASCII),p[2].getBytes(StandardCharsets.US_ASCII)) || (previousSecret!=null && MessageDigest.isEqual(sign(body,previousSecret).getBytes(StandardCharsets.US_ASCII),p[2].getBytes(StandardCharsets.US_ASCII)));
        return signatureValid && MessageDigest.isEqual(hash(credential).getBytes(StandardCharsets.US_ASCII),expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
    public UUID parseTicketId(String credential){
        try{String[] p=credential.split("\\."); if(p.length!=3||!"LK1".equals(p[0])) return null; return UUID.fromString(p[1]);}catch(Exception e){return null;}
    }
    public String pngDataUri(String credential){
        if (credential == null || credential.isBlank()) throw new IllegalArgumentException("QR credential is required");
        String cached = pngCache.get(credential);
        if (cached != null) return cached;
        try{
            QRCodeWriter writer=new QRCodeWriter(); Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.H); hints.put(EncodeHintType.MARGIN,3);
            BitMatrix m=writer.encode(credential,BarcodeFormat.QR_CODE,imageSize,imageSize,hints); ByteArrayOutputStream out=new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(m,"PNG",out);
            String uri="data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray());
            pngCache.put(credential, uri);
            return uri;
        }catch(WriterException|java.io.IOException e){throw new IllegalStateException("QR render failed",e);}
    }
    private String sign(String body, byte[] key){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(body.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
