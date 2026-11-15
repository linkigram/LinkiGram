#!/usr/bin/env python3
"""Host tests for production history merge and date bounds; Android services are fakes."""
import pathlib
import re
import sqlite3
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
JAVA = ROOT / "TMessagesProj/src/main/java"


def method(source, signature):
    start = source.index(signature)
    brace = source.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


source = (JAVA / "com/radolyn/ayugram/proprietary/AyuHistoryHook.java").read_text()
methods = "\n".join(method(source, name) for name in (
    "public static void mergeLoadAsync(",
    "public static Pair<Integer, Integer> getMinAndMaxIds(",
    "private static int doHook_compareMessages(",
))
fixture = r'''
import java.util.*;
import java.util.function.Consumer;
import app.nimarkogram.messenger.search.MessageSearchDateRange;

class Pair<A,B> { A first; B second; Pair(A a,B b){first=a;second=b;} }
class ConnectionsManager { static final int DEFAULT_DATACENTER_ID=Integer.MAX_VALUE; }
class UserConfig {
    static long owner=1; long clientUserId=1;
    static UserConfig getInstance(int a){return new UserConfig();}
    long getClientUserId(){return owner;}
}
class Queue {
    static ArrayList<Runnable> pending=new ArrayList<>();
    void postRunnable(Runnable r){pending.add(r);}
    static void drain(){while(!pending.isEmpty())pending.remove(0).run();}
}
class MessagesStorage {
    static MessagesStorage getInstance(int a){return new MessagesStorage();}
    Queue getStorageQueue(){return new Queue();}
}
class MessagesController { static MessagesController getInstance(int a){return new MessagesController();} }
class AndroidUtilities { static void runOnUIThread(Runnable r){r.run();} }
class FileLog { static void e(Exception e){throw new AssertionError(e);} }
class TLRPC {
    static class Message {int id,date;boolean ayuDeleted;}
    static class TL_message extends Message {}
}
class MessageObject {
    TLRPC.Message messageOwner;
    MessageObject(int id){messageOwner=new TLRPC.TL_message();messageOwner.id=id;messageOwner.date=id;}
    int getId(){return messageOwner.id;}
    boolean isSending(){return false;}
}
class DeletedMessageFull {
    TLRPC.TL_message message=new TLRPC.TL_message();long topic;
    DeletedMessageFull(int id,long topic){message.id=id;message.date=id;this.topic=topic;}
}
class AyuMessagesController {
    static ArrayList<DeletedMessageFull> rows=new ArrayList<>(); static String selected;
    static AyuMessagesController getInstance(){return new AyuMessagesController();}
    List<DeletedMessageFull> query(long start,long end,int limit,Long topic){
        ArrayList<DeletedMessageFull> result=new ArrayList<>();
        for(DeletedMessageFull r:rows)if(r.message.id>=start&&r.message.id<=end&&(topic==null||topic==r.topic))result.add(r);
        result.sort((a,b)->Integer.compare(b.message.id,a.message.id));
        return result.subList(0,Math.min(limit,result.size()));
    }
    List<DeletedMessageFull> getMessages(long u,long d,long s,long e,int n){selected="dialog";return query(s,e,n,null);}
    List<DeletedMessageFull> getTopicMessages(long u,long d,long t,long s,long e,int n){selected="topic";return query(s,e,n,t);}
    List<DeletedMessageFull> getThreadMessages(long u,long d,long t,long s,long e,int n){selected="thread";return query(s,e,n,t);}
}
public class AyuHistoryHook {
    static int checks;
    static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check "+checks);}
    static boolean hasContent(DeletedMessageFull f){return f!=null;}
    static TLRPC.TL_message map(DeletedMessageFull f,int a){f.message.ayuDeleted=true;return f.message;}
    static MessageObject createMessageObject(int a,TLRPC.TL_message m,Object u,Object c,MessagesController mc){
        MessageObject o=new MessageObject(m.id);o.messageOwner=m;return o;
    }
    static ArrayList<MessageObject> result;
    static void load(int anchor,int type,int count,boolean topic,boolean thread,int... ids){
        ArrayList<MessageObject> page=new ArrayList<>();for(int id:ids)page.add(new MessageObject(id));
        result=null;mergeLoadAsync(0,42,7,topic,thread,type,anchor,count,page,r->result=r);Queue.drain();
    }
    static void ids(int... expected){
        check(result!=null&&result.size()==expected.length);
        for(int i=0;i<expected.length;i++)check(result.get(i).getId()==expected[i]);
    }
    static void dateTests(){
        TimeZone utc=TimeZone.getTimeZone("UTC");
        Calendar c=Calendar.getInstance(utc);c.clear();c.set(2026,0,15,12,0,0);long day=c.getTimeInMillis();
        MessageSearchDateRange r=MessageSearchDateRange.ofDays(day,day,utc);
        check(r.isActive());check(r.getMaxDate()-r.getMinDate()==86401);
        check(r.getStartDayMillis()==r.getEndDayMillis());
        check(r.equals(MessageSearchDateRange.ofDays(day,day,utc)));
        check(!MessageSearchDateRange.ALL.isActive());
        TimeZone ny=TimeZone.getTimeZone("America/New_York");
        c=Calendar.getInstance(ny);c.clear();c.set(2026,2,8,12,0,0);long spring=c.getTimeInMillis();
        r=MessageSearchDateRange.ofDays(spring,spring,ny);check(r.getMaxDate()-r.getMinDate()==23*3600+1);
        c.clear();c.set(2026,10,1,12,0,0);long autumn=c.getTimeInMillis();
        r=MessageSearchDateRange.ofDays(autumn,autumn,ny);check(r.getMaxDate()-r.getMinDate()==25*3600+1);
        try{MessageSearchDateRange.ofDays(day+86400000,day,utc);throw new AssertionError();}catch(IllegalArgumentException good){checks++;}
        try{MessageSearchDateRange.ofDays(0,0,utc);throw new AssertionError();}catch(IllegalArgumentException good){checks++;}
    }
    public static void main(String[] args){
        dateTests();
        AyuMessagesController.rows.add(new DeletedMessageFull(110,7));
        AyuMessagesController.rows.add(new DeletedMessageFull(100,7));
        AyuMessagesController.rows.add(new DeletedMessageFull(90,8));
        load(0,0,2,false,false,105,100);ids(110,105,100); // newest archived message survives reopening
        check(result.get(0).messageOwner.ayuDeleted);check(!result.get(2).messageOwner.ayuDeleted); // no duplicate live ID
        load(105,0,2,false,false,100,95);ids(100,95); // middle-page bounds
        load(95,0,2,false,false);ids(90); // archive-only older page
        load(0,0,2,false,false);ids(110,100,90); // completely deleted dialog
        load(0,0,2,true,false);ids(110,100);check("topic".equals(AyuMessagesController.selected));
        load(0,0,2,false,true);ids(110,100);check("thread".equals(AyuMessagesController.selected));
        com.radolyn.ayugram.messages.AyuDeletedDialogs.suppressed=true;
        load(0,0,2,false,false,105);ids(105);
        com.radolyn.ayugram.messages.AyuDeletedDialogs.suppressed=false;
        result=null;mergeLoadAsync(0,42,0,false,false,0,0,20,new ArrayList<>(),r->result=r);
        UserConfig.owner=2;Queue.drain();check(result==null); // late callbacks cannot cross accounts
        System.out.println("PASS: "+checks+" production merge/date checks (fake Android services)");
    }
    // PRODUCTION_METHODS
}
'''.replace("// PRODUCTION_METHODS", methods)
# The production entity uses messageId, whereas the lightweight TL fixture uses id.
fixture = fixture.replace("full.message.messageId", "full.message.id")

with tempfile.TemporaryDirectory(prefix="history-tests-", dir="/tmp/opencode") as tmp:
    tmp = pathlib.Path(tmp)
    (tmp / "AyuHistoryHook.java").write_text(fixture)
    stub = tmp / "com/radolyn/ayugram/messages/AyuDeletedDialogs.java"
    stub.parent.mkdir(parents=True)
    stub.write_text("package com.radolyn.ayugram.messages; public class AyuDeletedDialogs {"
                    "public static boolean suppressed; public static boolean isSuppressed(int a,long d){return suppressed;} }")
    subprocess.run(["javac", "-d", str(tmp), str(tmp / "AyuHistoryHook.java"), str(stub),
                    str(JAVA / "app/nimarkogram/messenger/search/MessageSearchDateRange.java")], check=True)
    subprocess.run(["java", "-cp", str(tmp), "AyuHistoryHook"], check=True)
    # Execute the production DAO range query against a database reopened from disk.
    dao = (JAVA / "com/radolyn/ayugram/database/dao/DeletedMessageDao.java").read_text()
    query = re.search(r'@Query\("([^"]+)"\)\s+List<DeletedMessageFull> getMessages\(', dao).group(1)
    db_path = tmp / "archive.sqlite"
    with sqlite3.connect(db_path) as db:
        db.execute("CREATE TABLE deletedmessage (userId INTEGER, dialogId INTEGER, messageId INTEGER, text TEXT)")
        db.executemany("INSERT INTO deletedmessage VALUES (?,?,?,?)", [(1, 42, 110, "saved"), (2, 42, 120, "other account")])
    with sqlite3.connect(db_path) as reopened:
        rows = reopened.execute(query, dict(userId=1, dialogId=42, startId=0, endId=2147483647, limit=200)).fetchall()
        assert rows == [(1, 42, 110, "saved")], rows
    print("PASS: production DAO query retains the archive across SQLite reopen and isolates accounts")
