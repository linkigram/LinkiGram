"""Compile production AyuGhostUtils against fakes and exercise strict/read-after-send privacy."""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


root = Path(__file__).resolve().parents[1]
production = root / "TMessagesProj/src/main/java/com/radolyn/ayugram/utils/AyuGhostUtils.java"
text = production.read_text()
config = (root / "TMessagesProj/src/main/java/app/nimarkogram/messenger/NimarkoConfig.java").read_text()
start = config.index("    private static boolean loadGhostReadAfterSend() {")
end = config.index("    public static void toggleGhostReadAfterSend()", start)
migration = config[start:end]
assert 'ghostReadAfterSend = loadGhostReadAfterSend()' in config
assert 'ghostReadAfterSend = false;' in config[config.index('public static void applyPrivacyPreset'):]
names = set(re.findall(r"TLRPC\.(\w+)", text)) - {"InputPeer", "InputChannel", "TL_inputPeerChannel", "TL_inputEncryptedChat", "EncryptedChat"}
tl = '''package org.telegram.tgnet; import java.util.*; public class TLRPC {
 public static class InputPeer extends TLObject {public long chat_id,channel_id,user_id,access_hash;}
 public static class InputChannel extends TLObject {public long channel_id;}
 public static class TL_inputPeerChannel extends InputPeer {}
 public static class TL_inputEncryptedChat extends InputPeer {}
 public static class EncryptedChat extends TLObject {public int id; public long access_hash;}
 public static class Peer extends InputPeer {}
'''
for name in sorted(names):
    tl += f" public static class {name} extends TLObject {{"
    peer = "TL_inputEncryptedChat" if name in ("TL_messages_readEncryptedHistory", "TL_messages_setEncryptedTyping") else "InputPeer"
    tl += f"public {peer} peer; public InputChannel channel; public TLObject action;"
    tl += "public ArrayList<Integer> id=new ArrayList<>(); public boolean increment,typing; public int max_id,max_date,pts,pts_count;}\n"
tl += "}"
files = {
    "org/telegram/tgnet/TLObject.java": "package org.telegram.tgnet; public class TLObject {}",
    "org/telegram/tgnet/TLRPC.java": tl,
    "org/telegram/tgnet/RequestDelegate.java": "package org.telegram.tgnet; public interface RequestDelegate {void run(TLObject response, TLRPC.TL_error error);}",
    "org/telegram/tgnet/tl/TL_account.java": "package org.telegram.tgnet.tl; public class TL_account {public static class updateStatus extends org.telegram.tgnet.TLObject {public boolean offline;}}",
    "org/telegram/tgnet/tl/TL_stories.java": "package org.telegram.tgnet.tl; public class TL_stories {public static class TL_stories_readStories extends org.telegram.tgnet.TLObject {public org.telegram.tgnet.TLRPC.InputPeer peer;}public static class TL_stories_incrementStoryViews extends TL_stories_readStories {}}",
    "org/telegram/messenger/FileLog.java": "package org.telegram.messenger; public class FileLog {public static void d(String s){} public static void e(Exception e){}}",
    "org/telegram/messenger/DialogObject.java": "package org.telegram.messenger; public class DialogObject {public static long makeEncryptedDialogId(long id){return id<<32;} public static int getEncryptedChatId(long id){return (int)(id>>32);}}",
    "org/telegram/messenger/UserConfig.java": "package org.telegram.messenger; public class UserConfig {public static int selectedAccount;public static final int MAX_ACCOUNT_COUNT=2;static UserConfig[] a={new UserConfig(),new UserConfig()};public long uid=20; public static UserConfig getInstance(int i){return a[i];}public long getClientUserId(){return uid;}public boolean isClientActivated(){return true;}}",
    "org/telegram/messenger/Utilities.java": "package org.telegram.messenger; public class Utilities {public static final Queue stageQueue=new Queue(),globalQueue=new Queue();public static class Queue {public boolean defer;public Runnable pending;public void postRunnable(Runnable r){if(defer)pending=r;else r.run();}public void postRunnable(Runnable r,int delay){postRunnable(r);}}}",
    "org/telegram/messenger/MessagesController.java": "package org.telegram.messenger; import org.telegram.tgnet.*;public class MessagesController {public static MessagesController getInstance(int i){return new MessagesController();}public static TLRPC.InputChannel getInputChannel(TLRPC.InputPeer p){return new TLRPC.InputChannel();}public TLRPC.InputPeer getInputPeer(TLRPC.Peer p){return p;}public TLRPC.EncryptedChat getEncryptedChat(int i){return null;}public void processNewDifferenceParams(int a,int b,int c,int d){}}",
    "org/telegram/messenger/MessagesStorage.java": "package org.telegram.messenger; public class MessagesStorage {static MessagesStorage[] a={new MessagesStorage(),new MessagesStorage()};public Utilities.Queue queue=new Utilities.Queue();public static MessagesStorage getInstance(int i){return a[i];}public Utilities.Queue getStorageQueue(){return queue;}public void getDialogMaxMessageId(long id,java.util.function.IntConsumer done){done.accept(42);}}",
    "org/telegram/messenger/MessageObject.java": "package org.telegram.messenger; public class MessageObject {public int currentAccount; public Data messageOwner=new Data();public static class Data {public int date;public org.telegram.tgnet.TLRPC.Peer peer_id=new org.telegram.tgnet.TLRPC.Peer();} public int getId(){return 42;} public long getDialogId(){return 1;}public boolean isVoice(){return false;}public boolean isRoundVideo(){return false;}}",
    "com/radolyn/ayugram/utils/AyuGhostPreferences.java": "package com.radolyn.ayugram.utils;public class AyuGhostPreferences {public static boolean excluded;public static boolean isReadExcluded(int account,long dialog){return excluded;}public static boolean isTypingExcluded(int account,long dialog){return false;}}",
    "com/radolyn/ayugram/utils/AyuState.java": "package com.radolyn.ayugram.utils;public class AyuState {public static boolean getAllowReadPacket(){return false;}}",
    "org/telegram/tgnet/ConnectionsManager.java": '''package org.telegram.tgnet;
import com.radolyn.ayugram.utils.*;
public class ConnectionsManager {
 static ConnectionsManager[] a={new ConnectionsManager(0),new ConnectionsManager(1)};
 int account; public int sent; public boolean interleave;public ConnectionsManager(int i){account=i;}
 public static ConnectionsManager getInstance(int i){return a[i];}public int getCurrentTime(){return 1;}
 public void sendRequest(TLObject req,RequestDelegate done){
  if(interleave){interleave=false;TLRPC.TL_messages_readHistory other=new TLRPC.TL_messages_readHistory();other.peer=new TLRPC.InputPeer();other.peer.user_id=999;
   if(!AyuGhostUtils.interceptRequest(account,other,null).blockRequest())throw new AssertionError("Unrelated read leaked");}
  AyuGhostUtils.InterceptResult r=AyuGhostUtils.interceptRequest(account,req,done);
  if(!r.blockRequest()){sent++;if(r.effectiveOnComplete()!=null)r.effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);}
 }
}''',
    "app/nimarkogram/messenger/NimarkoConfig.java": '''package app.nimarkogram.messenger; public class NimarkoConfig {
 public static boolean ghostTyping,ghostRecording,ghostReadReceipts=true,ghostReadAfterSend,ghostStories,hideStoryViews,ghostOnline,ghostOfflineAfterSend;
}''',
    "Check.java": r'''
import com.radolyn.ayugram.utils.*; import org.telegram.tgnet.*;import org.telegram.messenger.*;import app.nimarkogram.messenger.NimarkoConfig;
public class Check {
 static int checks;
 static void check(boolean b){if(!b)throw new AssertionError("check "+checks);checks++;}
 static TLRPC.InputPeer peer(){TLRPC.InputPeer p=new TLRPC.InputPeer();p.user_id=1;return p;}
 static TLRPC.TL_messages_sendMessage send(){TLRPC.TL_messages_sendMessage s=new TLRPC.TL_messages_sendMessage();s.peer=peer();return s;}
 static TLRPC.TL_messages_readHistory read(){TLRPC.TL_messages_readHistory r=new TLRPC.TL_messages_readHistory();r.peer=peer();return r;}
 static AyuGhostUtils.InterceptResult start(){return AyuGhostUtils.interceptRequest(1,send(),(r,e)->{});}
 public static void main(String[] args){
  check(AyuGhostUtils.interceptRequest(1,read(),null).blockRequest());
  start().effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);
  check(ConnectionsManager.getInstance(1).sent==0);
  NimarkoConfig.ghostReadAfterSend=true;
  AyuGhostUtils.InterceptResult pending=start();
  check(ConnectionsManager.getInstance(1).sent==0); // not before acknowledgement
  pending.effectiveOnComplete().run(null,new TLRPC.TL_error());check(ConnectionsManager.getInstance(1).sent==0);
  ConnectionsManager.getInstance(1).interleave=true;
  start().effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);
  check(ConnectionsManager.getInstance(1).sent==1);check(ConnectionsManager.getInstance(0).sent==0);
  check(AyuGhostUtils.interceptRequest(1,read(),null).blockRequest());
  pending=start();NimarkoConfig.ghostReadAfterSend=false;pending.effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);
  check(ConnectionsManager.getInstance(1).sent==1);
  NimarkoConfig.ghostReadAfterSend=true;
  MessagesStorage.getInstance(1).queue.defer=true;start().effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);
  UserConfig.getInstance(1).uid=99;MessagesStorage.getInstance(1).queue.pending.run();
  check(ConnectionsManager.getInstance(1).sent==1);MessagesStorage.getInstance(1).queue.defer=false;
  pending=start();UserConfig.getInstance(1).uid=100;pending.effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);
  check(ConnectionsManager.getInstance(1).sent==1);
  AyuGhostPreferences.excluded=true;check(!AyuGhostUtils.interceptRequest(1,read(),null).blockRequest());
  start().effectiveOnComplete().run(new TLRPC.TL_boolTrue(),null);check(ConnectionsManager.getInstance(1).sent==1);
  AyuGhostPreferences.excluded=false;
  TLRPC.TL_channels_readHistory channel=new TLRPC.TL_channels_readHistory();channel.channel=new TLRPC.InputChannel();channel.channel.channel_id=3;
  check(AyuGhostUtils.interceptRequest(1,channel,null).blockRequest());
  TLRPC.TL_messages_readMessageContents contents=new TLRPC.TL_messages_readMessageContents();
  check(AyuGhostUtils.interceptRequest(1,contents,null).blockRequest());
  TLRPC.TL_messages_getMessagesViews views=new TLRPC.TL_messages_getMessagesViews();views.peer=peer();views.increment=true;
  AyuGhostUtils.interceptRequest(1,views,null);check(!views.increment);
  NimarkoConfig.ghostReadReceipts=false;check(!AyuGhostUtils.interceptRequest(1,read(),null).blockRequest());
  Pref.values.put("ghostReadAfterSend",true);check(!loadGhostReadAfterSend());
  Pref.values.put("ghostReadAfterSend",true);check(loadGhostReadAfterSend()); // explicit opt-in survives subsequent launches
  System.out.println("PASS: "+checks+" ghost read checks; strict send, success/error, exact bypass, account switch, exclusions, migration");
 }
 static class Pref {static java.util.Map<String,Boolean> values=new java.util.HashMap<>();boolean getBoolean(String k,boolean d){return values.getOrDefault(k,d);}Pref putBoolean(String k,boolean v){values.put(k,v);return this;}void apply(){}}
 static Pref getPreferences(){return new Pref();}static Pref getEditor(){return new Pref();}
MIGRATION
}
'''.replace("MIGRATION", migration),
}
# Error is used by the callback interface but not referenced directly in the utility.
files["org/telegram/tgnet/TLRPC.java"] = tl[:-1] + "public static class TL_error extends TLObject {} }"
with tempfile.TemporaryDirectory(prefix="ghost-reads-") as temp:
    folder = Path(temp)
    for name, content in files.items():
        p = folder / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content)
    home = os.environ.get("JAVA_HOME")
    javac = str(Path(home) / "bin/javac") if home else shutil.which("javac")
    java = str(Path(home) / "bin/java") if home else shutil.which("java")
    subprocess.run([javac, "-d", temp, *map(str, folder.rglob("*.java")), str(production)], check=True)
    subprocess.run([java, "-cp", temp, "Check"], check=True)
