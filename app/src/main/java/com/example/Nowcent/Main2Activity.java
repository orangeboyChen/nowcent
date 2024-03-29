package com.example.Nowcent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.example.Nowcent.Client.JsonToConnectMessage;
import static com.example.Nowcent.Client.JsonToUser;
import static com.example.Nowcent.Client.JsonToUserImg;
import static com.example.Nowcent.Client.JsonToUserMessage;
import static com.example.Nowcent.Client.UserImgToJson;

public class Main2Activity extends Activity implements View.OnClickListener {
    int port;
    String group;
    Button btn_Send;
    Button btn_Exit;
    TextView txv_Name;
    TextView txv_Group;
    EditText edt;
    Socket socket;
    Client client;
    Thread recThread;
    Thread sendThread;
    Thread connectThread;
//    SimpleAdapter simpleAdapter;
    ListView listView;
//    List<Map<String,Object>> msgList=new ArrayList<Map<String,Object>>();
    String[] groupUser;
    Adapter adapter;
    ArrayList<ListItem> arrayList;

    CountDownTimer hbTimer=new CountDownTimer(6000,6000) {
        @Override
        public void onTick(long l) {
        }
        @Override
        public void onFinish() {
            reConnect();
            hbTimer.cancel();
        }
    };

    boolean isAllowThread =true;
    boolean isFront=true;
    boolean isConnect=true;
    boolean isExit=false;
    int unreadMsgCount =1;
    int defaultValue=0;
    User user;

    private static final String TAG = "MainActivity";


    public void reconnect(String msg,boolean isError){
        if(!isError) {
            setList(new Message_Connect("你",3));
        }
        groupUser=new String[1];
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txv_Group.setText(user.getGroup()+"(你当前离线)");
            }
        });
        isAllowThread =false;
        isConnect=false;
        for(int i=0;i<=2;i++) {
            try {
                SocketAddress socketAddress = new InetSocketAddress(getResources().getString(R.string.ip), 6000);
                socket = new Socket();
                socket.connect(socketAddress, 300);

                client = new Client(socket);
                client.send(new Message(FLAG.RECONNECT,Client.UserToJson(new User(user.getName(),user.getPassword(),group))));
                Message message=client.get();
                if(message.getFlag()==FLAG.RECONNECT) {
                    user = JsonToUser(message.getMsg());
                    port=user.getPort();
                    socket = new Socket(getResources().getString(R.string.ip), port);
                    client = new Client(socket);
                    isConnect=true;
                    isAllowThread =true;
                    new Thread(recThread).start();
                    hbTimer.cancel();
                    hbTimer.start();
                    break;
                }
                Thread.sleep(1000);
            }
            catch (Exception e) {
                Log.d(TAG,"error socket");
                e.printStackTrace();
            }
        }

        if(!isConnect){
            if(isFront){
                Looper.prepare();
                AlertDialog.Builder alert = new AlertDialog.Builder(Main2Activity.this)
                        .setTitle("未连接")
                        .setMessage("您已退出，请重新登录")
                        .setCancelable(false)
                        .setPositiveButton("好", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Intent intent = new Intent();
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.setClass(Main2Activity.this, MainActivity.class);
                                startActivity(intent);
                                finish();
                            }
                        });
                alert.show();
                Looper.loop();
            }
            else{
                startNotification("未连接", "请重新登录", 1, true, true);
            }
        }
    }

    Runnable reconnectRunnable=new Runnable() {
        @Override
        public void run() {
            reconnect("RECONNECT",false);
        }
    };
    Runnable connectRunnable=new Runnable() {
        @Override
        public void run() {
            try{
                socket=new Socket(getResources().getString(R.string.ip),user.getPort());
                client=new Client(socket);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    Runnable receiveMsgRunnable=new Runnable() {
        @Override
        public void run() {
            while (isAllowThread) {
                try{
                    Message message = client.get();
                    if(message!=null) {
                        hbTimer.cancel();
                        hbTimer.start();
                        client.send(new Message(FLAG.HB));
                        handleRecMessage(message);
                    }
                }
                catch (Exception e){
                    //e.printStackTrace();
                }
            }
        }
    };


    Runnable sendMsgRunnable=new Runnable() {
        @Override
        public void run() {
            try {
                if(!edt.getText().toString().equals("")) {
                    SimpleDateFormat simpleDateFormat=new SimpleDateFormat("M-d H:mm");
                    Date date=new Date(System.currentTimeMillis());
                    String time=simpleDateFormat.format(date);

                    //Get str
                    String str;
                    do{
                        str = edt.getText().toString();
                    }while(str==null);
//                    Log.d(TAG, "str:" + str);

                    UserMessage userMessage=new UserMessage(user.getName(),time,str,user.getGroup());
//                    Log.d(TAG,Client.UserMessageToJson(userMessage));
                    client.send(new Message(FLAG.USER_MESSAGE,Client.UserMessageToJson(userMessage)));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            edt.setText("");
                        }
                    });
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    };

    private void handleRecMessage(Message message){
        UserMessage userMessage;
        Message_Connect messageConnect;
        UserImg userImg;

        if(message!=null)
        switch(message.getFlag()){
            case FLAG.CLOUD:
                userMessage=JsonToUserMessage(message.getMsg());
                setList(userMessage);
                break;
            case FLAG.USER_MESSAGE:
                userMessage=JsonToUserMessage(message.getMsg());
                if(!isFront) {
                    if(unreadMsgCount ==1){
                        startNotification(userMessage.getGroup(), userMessage.getUser()+":"+userMessage.getMsg(),1,true,true);
                    }
                    else{
                        startNotification(userMessage.getGroup(), "["+ unreadMsgCount +"条]"+userMessage.getUser()+":"+userMessage.getMsg(),1,true,true);
                    }
                    unreadMsgCount++;
                }
                else{
                    unreadMsgCount =1;
                }
                setList(userMessage);
                break;
            case FLAG.CONNECT_INFO:
                messageConnect =JsonToConnectMessage(message.getMsg());
                setList(messageConnect);
                break;
            case FLAG.HB:
                client.send(new Message(FLAG.HB));
                break;
            case FLAG.USERLIST:
                //Handle
                String str=message.getMsg();
                String str2=str.replaceAll("[\\[\\]\"]","");
                Log.d(TAG,str2);
                groupUser=str2.split(", ");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        txv_Group.setText(user.getGroup()+"("+groupUser.length+")");
                    }
                });
                break;
            case FLAG.USER_IMG:
                userImg=JsonToUserImg(message.getMsg());
                if(!isFront) {
                    if(unreadMsgCount ==1){
                        startNotification(userImg.getGroup(), userImg.getNickName()+":[动画表情]",1,true,true);
                    }
                    else{
                        startNotification(userImg.getGroup(), "["+ unreadMsgCount +"条]"+userImg.getNickName()+":[动画表情]",1,true,true);
                    }
                    unreadMsgCount++;
                }
                else{
                    unreadMsgCount =1;
                }
                setList(userImg);
                break;
            case FLAG.CLOUD_IMG:
                userImg=JsonToUserImg(message.getMsg());
                setList(userImg);
                break;
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Unsafe
        //if (android.os.Build.VERSION.SDK_INT > 9) {
            //StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            //StrictMode.setThreadPolicy(policy);
        //}
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        Intent intent=getIntent();
        isFront=true;
        //Get user
        user=Client.JsonToUser(intent.getStringExtra("user"));
        group=user.getGroup();
        //Instance
        btn_Exit=(Button)this.findViewById(R.id.btn_exit);
        btn_Send =(Button)this.findViewById(R.id.btn_send);
        txv_Name =(TextView)this.findViewById(R.id.txv_top);
        txv_Group =(TextView)this.findViewById(R.id.txv_group);
        edt=(EditText)this.findViewById(R.id.edt_msg);
        listView=(ListView)this.findViewById(R.id.listview);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                txv_Name.setText(user.getNickName());
            }
        });
        txv_Group.setText(user.getGroup());
        recThread=new Thread(receiveMsgRunnable);
        connectThread=new Thread(connectRunnable);

        recThread.start();
        connectThread.start();
        hbTimer.start();



        arrayList=new ArrayList<ListItem>();
        adapter=new Adapter(Main2Activity.this,arrayList);
//        simpleAdapter=new SimpleAdapter(Main2Activity.this,msgList,R.layout.listview,
//                new String[]{"user","time","msg","img","emojiimg"},
//                new int[]{R.id.txv_user,R.id.txv_time,R.id.txv_msg,R.id.img_user,0});

        listView.setAdapter(adapter);

        btn_Send.setOnClickListener(this);
        btn_Exit.setOnClickListener(this);
        txv_Group.setOnClickListener(this);

        edt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(edt.getText().toString().equals("/")){
                    final String[] emojis={"GPM","Happy"};

                    AlertDialog.Builder builder=new AlertDialog.Builder(Main2Activity.this)
                            .setTitle("发送表情")
                            .setItems(emojis,new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    defaultValue=i;
                                    Log.d(TAG,Integer.toString(i));
                                    switch (defaultValue){
                                        case 0:
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    client.send(new Message(FLAG.USER_IMG,UserImgToJson(new UserImg(user.getName(),R.drawable.gpm,user.getGroup()))));
                                                }
                                            }).start();
                                            break;
                                        case 1:
                                            new Thread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    client.send(new Message(FLAG.USER_IMG,UserImgToJson(new UserImg(user.getName(),R.drawable.happy,user.getGroup()))));
                                                }
                                            }).start();
                                    }
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            edt.setText("");
                                        }
                                    });
                                }
                            });
                    builder.show();
                    Looper.loop();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

    }

    @Override
    public void onClick(View view){
        switch (view.getId()){
            case R.id.btn_send:
//
//                if(edt.getText().toString().equals("1")){
//                    reConnect();
//                }
//                else if(edt.getText().toString().equals("2")){
//                    new Thread(
//                            new Runnable() {
//                                @Override
//                                public void run() {
//                                    client.send(new Message(FLAG.USERLIST));
//                                }
//                            }).start();
//                }
//                else {
                    sendThread = new Thread(sendMsgRunnable);
                    sendThread.start();
//                }
                break;
            case R.id.btn_exit:
                isAllowThread =false;
                isExit=true;
                new Thread(){
                    public void run(){
                        try {
                            client.send(new Message(FLAG.EXIT));
                            socket.close();
                        }catch (Exception e){}
                    }
                }.start();
                Intent intent=new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(Main2Activity.this,MainActivity.class);
                startActivity(intent);
                adapter=null;
                listView=null;
                finish();
                break;
            case R.id.txv_group:
                StringBuilder stringBuilder=new StringBuilder();
                for(int i=0;i<groupUser.length-1;i++){
                    stringBuilder.append(groupUser[i]+"\n\r");
                }
                stringBuilder.append(groupUser[groupUser.length-1]+"\n\r");
                String str=stringBuilder.toString();

                AlertDialog.Builder alert=new AlertDialog.Builder(Main2Activity.this)
//                        .setTitle("在线用户")
                        .setMessage(str)
                        .setCancelable(true)
                        .setPositiveButton("好", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        });
                alert.show();
                break;
        }
    }

    @Override
    public void onStop(){
        super.onStop();
        isFront=false;
        //startNotification("即讯正在后台运行","请不要清除进程",2,false,false);
        Log.d(TAG,"STOP");
    }

    @Override
    public void onStart(){
        super.onStart();
        isFront=true;
        NotificationManager notificationManager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
        unreadMsgCount =1;
        if(!isConnect){
            reConnect();
        }

        Log.d(TAG,"START");
    }

    private void reConnect(){
        if(!isExit) {
            Log.d(TAG, "reConnect()");
            new Thread(reconnectRunnable).start();
        }
    }

    public void startNotification(String title,String msg,int id,boolean cancelable,boolean importance){
        Log.d(TAG,"Notification");
        Intent intent=new Intent(getApplicationContext(),Main2Activity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);
        PendingIntent pendingIntent=PendingIntent.getActivity(Main2Activity.this,10,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager notificationManager=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        Notification notification;
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.O){
            Log.d(TAG,"Notification8888888888888888888888");
            NotificationChannel notificationChannel = new NotificationChannel("1", "消息提醒", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setShowBadge(true);
            notificationManager.createNotificationChannel(notificationChannel);
            Notification.Builder builder=new Notification.Builder(this)
            .setContentTitle(title)
            .setContentText(msg)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setSmallIcon(R.mipmap.iclauncher)
            .setContentIntent(pendingIntent)
            .setNumber(unreadMsgCount)
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setChannelId("1");
            notification=builder.build();
            //if(!cancelable){
            //    notification.flags|=Notification.FLAG_ONGOING_EVENT;
            //}
            notificationManager.notify(id,notification);
            return;
        }
        else{
            NotificationCompat.Builder builder=new NotificationCompat.Builder(Main2Activity.this.getApplicationContext())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setSound(Settings.System.DEFAULT_NOTIFICATION_URI)
            .setDefaults(Notification.DEFAULT_ALL)
            .setTicker(title+"："+msg)
            .setContentTitle(title)
            .setContentText(msg)
            .setAutoCancel(true)
            .setPriority(Notification.PRIORITY_HIGH)
            .setContentIntent(pendingIntent);
            notification=builder.build();
            if(!cancelable){
                notification.flags|=Notification.FLAG_ONGOING_EVENT;
            }
            notificationManager.notify(1,notification);

        }
    }

    @Override
    public void onBackPressed(){
        moveTaskToBack(false);
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    public void setList(UserMessage userMessage){
//        Map<String,Object> map=new HashMap<String, Object>();
//        map.put("user",userMessage.getUser());
//        map.put("time",userMessage.getTime());
//        map.put("msg",userMessage.getMsg());
//        if (userMessage.getUser().equals("GPM")) {
//            map.put("img", R.drawable.gpm_png);
//        } else if (userMessage.getUser().equals("orangeboy")) {
//            map.put("img", R.drawable.admin_png);
//        } else {
//            map.put("img", R.drawable.user_png);
//        }
//        msgList.add(map);
        arrayList.add(new ListItem(userMessage));
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                }
        );

    }

    private void setList(Message_Connect messageConnect){
//        Map<String,Object> map=new HashMap<String, Object>();
//        map.put("user","系统");
//        switch(messageConnect.getType()){
//            case 1:
//                map.put("time", messageConnect.getName()+"已加入");
//                break;
//            case 2:
//                map.put("time", messageConnect.getName()+"已退出");
//                break;
//            case 3:
//                map.put("time", messageConnect.getName()+"正在重连");
//                break;
//        }
//        msgList.add(map);

        arrayList.add(new ListItem(messageConnect));
        runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        adapter.notifyDataSetChanged();
                    }
                }
        );
    }

    public void setList(UserImg userImg){
        arrayList.add(new ListItem(userImg));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }

}
