#!/usr/bin/env python3
"""Compile production NFC payload/HCE code against fakes; no NFC hardware coverage."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1] / "TMessagesProj/src/main/java"
STUBS = {
    "android/os/Bundle.java": "package android.os; public class Bundle {}",
    "android/text/TextUtils.java": "package android.text; public class TextUtils {public static boolean isEmpty(CharSequence s){return s==null||s.length()==0;}}",
    "android/net/Uri.java": '''package android.net;
public class Uri {
 private java.net.URI u; private Uri(String s){u=java.net.URI.create(s);}
 public static Uri parse(String s){return new Uri(s);}
 public String getScheme(){return u.getScheme();} public String getHost(){return u.getHost();}
 public String getQueryParameter(String key){
  if(u.getRawQuery()==null)return null;
  for(String p:u.getRawQuery().split("&")){String[] kv=p.split("=",2);if(kv[0].equals(key)){
   try{return java.net.URLDecoder.decode(kv.length>1?kv[1]:"","UTF-8");}catch(Exception e){throw new RuntimeException(e);}
  }}return null;
 }
}''',
    "android/nfc/cardemulation/HostApduService.java": "package android.nfc.cardemulation; public abstract class HostApduService {public abstract byte[] processCommandApdu(byte[] a,android.os.Bundle b); public abstract void onDeactivated(int reason);}",
    "android/nfc/NdefRecord.java": '''package android.nfc; public class NdefRecord {
 public String uri; public static NdefRecord createUri(String s){NdefRecord r=new NdefRecord();r.uri=s;return r;}
}''',
    "android/nfc/NdefMessage.java": '''package android.nfc; public class NdefMessage {
 NdefRecord[] records;public NdefMessage(NdefRecord[] r){records=r;}
 public byte[] toByteArray(){try{return records[0].uri.getBytes("UTF-8");}catch(Exception e){throw new RuntimeException(e);}}
}''',
    "org/telegram/messenger/FileLog.java": "package org.telegram.messenger; public class FileLog {public static void e(Exception e){throw new AssertionError(e);}}",
    "org/telegram/tgnet/TLRPC.java": "package org.telegram.tgnet; public class TLRPC {public static class User {public String first_name,last_name,username;}}",
    "org/telegram/messenger/UserConfig.java": '''package org.telegram.messenger; public class UserConfig {
 public static int selectedAccount=0; public static final int MAX_ACCOUNT_COUNT=4;
 public static long owner=123; public static boolean activated=true;
 public static UserConfig getInstance(int a){return new UserConfig();}
 public boolean isClientActivated(){return activated;} public long getClientUserId(){return owner;}
 public org.telegram.tgnet.TLRPC.User getCurrentUser(){org.telegram.tgnet.TLRPC.User u=new org.telegram.tgnet.TLRPC.User();u.first_name="N+%";return u;}
}''',
    "app/nimarkogram/messenger/nfc/LinkiShareController.java": "package app.nimarkogram.messenger.nfc; public class LinkiShareController {public static boolean allowed=true; public static boolean isSharingAllowed(){return allowed;}}",
}
CHECK = '''
import app.nimarkogram.messenger.nfc.*;
import org.telegram.messenger.UserConfig;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CheckNfc {
 static int checks;
 static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
 static byte[] hex(String s){byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 static byte[] command(LinkiShareNdefService h,String s){return h.processCommandApdu(hex(s),null);}
 static void status(byte[] b,String s){check(b.length>=2&&Arrays.equals(Arrays.copyOfRange(b,b.length-2,b.length),hex(s)));}
 public static void main(String[] args){
  LinkiSharePayload p=new LinkiSharePayload(123,"Иван + 100%","test_user");
  LinkiSharePayload q=LinkiSharePayload.fromUri(p.toUri());
  check(q!=null&&q.userId==123&&q.name.equals(p.name)&&q.username.equals(p.username));
  for(String s:new String[]{"", "https://linki-share?id=123", "linkigram://other?id=1", "linkigram://linki-share?id=-1", "linkigram://linki-share?id=no"})check(LinkiSharePayload.fromUri(s)==null);
  char[] huge=new char[2049];Arrays.fill(huge,'a');check(LinkiSharePayload.fromUri(new String(huge))==null);
  LinkiShareNdefService h=new LinkiShareNdefService();
  String aid="00A4040007D276000085010100", cc="00A4000C02E103", ndef="00A4000C02E104";
  status(command(h,aid),"6986");
  LinkiShareNdefService.setArmed(true,0);status(command(h,aid),"9000");
  status(command(h,cc),"9000");byte[] cap=command(h,"00B000000F");status(cap,"9000");check(cap.length==17&&cap[2]==0x20);
  status(command(h,ndef),"9000");byte[] data=command(h,"00B0000000");status(data,"9000");
  String text=new String(data,2,data.length-4,StandardCharsets.UTF_8);check(text.contains("id=123")&&text.contains("N%2B%25"));
  LinkiShareController.allowed=false;status(command(h,"00B0000000"),"6986");
  LinkiShareController.allowed=true;status(command(h,"00B0000000"),"6986"); // lock invalidated the cached file
  status(command(h,aid),"9000");status(command(h,ndef),"9000");
  UserConfig.owner=456;LinkiShareNdefService.setArmed(true,0);status(command(h,"00B0000000"),"6986");
  status(command(h,aid),"9000");status(command(h,ndef),"9000");
  data=command(h,"00B0000000");text=new String(data,2,data.length-4,StandardCharsets.UTF_8);check(text.contains("id=456"));
  LinkiShareNdefService.setArmed(false);status(command(h,"00B0000000"),"6986");
  LinkiShareNdefService.setArmed(true);status(command(h,"00B0000000"),"6986");
  status(command(h,aid),"9000");status(command(h,ndef),"9000");
  status(command(h,"00B07FFF01"),"6F00");status(command(h,"0000"),"6F00");
  h.onDeactivated(0);status(command(h,"00B0000000"),"6986");
  System.out.println("PASS: "+checks+" production NFC payload/HCE checks (fake Android/NDEF)");
 }
}
'''

with tempfile.TemporaryDirectory(prefix="linkishare-test-", dir="/tmp/opencode") as tmp:
    tmp = Path(tmp)
    sources = []
    for name, text in STUBS.items():
        p = tmp / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(text)
        sources.append(str(p))
    test = tmp / "CheckNfc.java"
    test.write_text(CHECK)
    production = [ROOT / "app/nimarkogram/messenger/nfc" / name for name in ("LinkiSharePayload.java", "LinkiShareNdefService.java")]
    subprocess.run(["javac", "-encoding", "UTF-8", "-d", str(tmp), *sources, *map(str, production), str(test)], check=True)
    subprocess.run(["java", "-cp", str(tmp), "CheckNfc"], check=True)
