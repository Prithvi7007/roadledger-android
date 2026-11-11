package fun.cadacrew.roadledger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;

/** Exact byte preservation and SHA-256 verification for the user's own imported CSV. */
public final class RoadLedgerSourceArchive {
    private RoadLedgerSourceArchive(){}
    public static String sha256(byte[] bytes){
        if(bytes==null)throw new IllegalArgumentException("Source bytes are required");
        try{
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            StringBuilder result=new StringBuilder(64);
            for(byte b:md.digest(bytes))result.append(String.format(Locale.US,"%02x",b&0xff));
            return result.toString();
        }catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    public static String encode(byte[] bytes){
        if(bytes==null)throw new IllegalArgumentException("Source bytes are required");
        return Base64.getEncoder().encodeToString(bytes);
    }
    public static byte[] decodeVerified(String encoded,String expectedSha256){
        if(encoded==null||expectedSha256==null||!expectedSha256.matches("[a-fA-F0-9]{64}"))
            throw new IllegalArgumentException("Invalid archived source metadata");
        byte[] bytes;
        try{bytes=Base64.getDecoder().decode(encoded);}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("Archived source is damaged",e);}
        if(!sha256(bytes).equalsIgnoreCase(expectedSha256))
            throw new IllegalArgumentException("Archived source checksum mismatch");
        return bytes;
    }
}
