package com.neelastack.lakhdatar.service;

import com.google.zxing.BarcodeFormat; import com.google.zxing.EncodeHintType; import com.google.zxing.WriterException; import com.google.zxing.client.j2se.MatrixToImageWriter; import com.google.zxing.common.BitMatrix; import com.google.zxing.qrcode.QRCodeWriter; import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.neelastack.lakhdatar.config.AppProperties;
import org.springframework.stereotype.Service;
import javax.crypto.Mac; import javax.crypto.spec.SecretKeySpec; import java.io.ByteArrayOutputStream; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.Base64; import java.util.EnumMap; import java.util.Map; import java.util.UUID;

@Service
public class QrCredentialService {
    private final byte[] secret; private final int imageSize;
    public QrCredentialService(AppProperties props){ String s=props.qr().signingSecret(); if(s==null||s.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalStateException("QR_SIGNING_SECRET must be at least 32 bytes"); secret=s.getBytes(StandardCharsets.UTF_8); imageSize=props.qr().imageSize(); }
    public String credentialFor(UUID ticketPublicId){ return "LK1."+ticketPublicId+"."+sign("QR|"+ticketPublicId); }
    public String hash(String credential){
        try{ byte[] h=MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8)); return Base64.getUrlEncoder().withoutPadding().encodeToString(h); }catch(Exception e){throw new IllegalStateException(e);}
    }
    public boolean verifyCredential(String credential,UUID ticketPublicId,String expectedHash){
        String expected=credentialFor(ticketPublicId); return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),credential.getBytes(StandardCharsets.US_ASCII)) && MessageDigest.isEqual(hash(credential).getBytes(StandardCharsets.US_ASCII),expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
    public UUID parseTicketId(String credential){
        try{String[] p=credential.split("\\."); if(p.length!=3||!"LK1".equals(p[0])) return null; return UUID.fromString(p[1]);}catch(Exception e){return null;}
    }
    public String pngDataUri(String credential){
        try{ QRCodeWriter writer=new QRCodeWriter(); Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class); hints.put(EncodeHintType.ERROR_CORRECTION,ErrorCorrectionLevel.H); hints.put(EncodeHintType.MARGIN,3); BitMatrix m=writer.encode(credential,BarcodeFormat.QR_CODE,imageSize,imageSize,hints); ByteArrayOutputStream out=new ByteArrayOutputStream(); MatrixToImageWriter.writeToStream(m,"PNG",out); return "data:image/png;base64,"+Base64.getEncoder().encodeToString(out.toByteArray()); }catch(WriterException|java.io.IOException e){throw new IllegalStateException("QR render failed",e);}
    }
    private String sign(String body){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(secret,"HmacSHA256"));return Base64.getUrlEncoder().withoutPadding().encodeToString(m.doFinal(body.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
