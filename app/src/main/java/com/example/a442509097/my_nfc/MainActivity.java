package com.example.a442509097.my_nfc;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcEvent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/*监测NFC对象是否为null,不开开关也能检测的到,但是如果为null则表明此机器没有NFC硬件*/
//勾选最后的一个读(其它)即可,而且只需设置当前app这一个目录即可,而且不用应用到子文件夹或文件

/**
 * 说明:
 * 1.当返回时会中修改app文件夹回为原来的权限,再次进入时如果发现取出file文件夹列表为null则会自动更改为需要的权限
 * 2.base.apk那个文件不用更改权限,也照样可以选择发送.
 * 3.正常情况下按返回键时就是销毁此activity
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MainActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback, CreateBeamUrisCallback { //文本和uri(文件)的注册,可根据要求只选一个
    private NfcAdapter nfcAdapter;
    private TextView textView;
    private ListView listView;
    private Button button;
    private ArrayList<HashMap<String, Object>> listItems;
    private final static String PATH = "/data/app";
    private String targetFilename;
    /**
     * 是否已经允许SU权限的标识
     */
    public static boolean CHMOD_FlAG = true;
    public static boolean BLUETOOTH_FLAG = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView = (ListView) findViewById(R.id.listView);
        button = (Button) findViewById(R.id.button);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        //nfcAdapter.setNdefPushMessageCallback(this, this); //注册回调函数(注册,监听(下面的方法))//此方法如果在xml中不设置NFC权限的情况下会自动报错
        nfcAdapter.setBeamPushUrisCallback(this, this); //设置uri回调(文件传输)
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        button.setOnClickListener(new View.OnClickListener() { //取用蓝牙模式
            @Override
            public void onClick(View v) {
                if (!bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.enable();
                    BLUETOOTH_FLAG = true;
                    Toast.makeText(getApplicationContext(), "蓝牙模式开启", Toast.LENGTH_SHORT).show();
                } else {
                    bluetoothAdapter.disable();
                    BLUETOOTH_FLAG = false;
                    Toast.makeText(getApplicationContext(), "蓝牙模式关闭", Toast.LENGTH_SHORT).show();
                }
            }
        });
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(getApplicationContext(), "读取中...", Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }).start();
        if ((listItems = getApkData()) != null) { //判断返回回来的是不是null数据
            SimpleAdapter simpleAdapter = new SimpleAdapter(getApplicationContext(), listItems, R.layout.items, new String[]{"itemImage", "itemTitle"}, new int[]{R.id.imageView, R.id.textView1});
            simpleAdapter.setViewBinder(new SimpleAdapter.ViewBinder() { //必须要加入这段,否则默认情况下无法显示
                @Override
                public boolean setViewValue(View view, Object data, String textRepresentation) {
                    if (view instanceof ImageView && data instanceof Drawable) {
                        ImageView iv = (ImageView) view;
                        iv.setImageDrawable((Drawable) data);
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            listView.setAdapter(simpleAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    //String s=parent.toString();
                    HashMap<String, Object> hashMap = listItems.get(position);
                    String path = (String) hashMap.get("itemTitle"); //取得路径
                    if (nfcAdapter.isEnabled() && !BLUETOOTH_FLAG) { //判断NFC是否开启了并且不是处于蓝牙模式的
                        targetFilename = path; //赋值给路径,等待接触发送
                        Toast.makeText(getApplicationContext(), "选择完成,请打开另一部手机的NFC,然后背靠背", Toast.LENGTH_LONG).show();
                    } else if (BLUETOOTH_FLAG || bluetoothAdapter.isEnabled()) { //或蓝牙已经是打开的状态(假设NFC没打开,而蓝牙已经打开了的情况下)
                        targetFilename = path;
                        sendViaBluetooth(targetFilename);
                    } else {
                        Toast.makeText(getApplicationContext(), "请开启NFC或蓝牙模式后继续", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    /**
     * 通过蓝牙发送文件
     *
     * @param filePath 文件file地址
     */
    private void sendViaBluetooth(String filePath) { //使用蓝牙传输文件
        Intent intent = new Intent(Intent.ACTION_SEND); //调用系统的发送选项
        File file = new File(filePath);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file)); //传入的是一个uri对象
        intent.setType("*/*");
        startActivity(Intent.createChooser(intent, "选择蓝牙后发送")); //此方法无论怎么样,每次都会重新创建选择
        //startActivity(it); //此方法会默认选用上次的选择
    }

    /*文件的传输*/
    @Override //当开始传输文件时自动调用 //当文件传输时,当前配置为另一台端不会打开界面,而是直接显示传输界面.
    public Uri[] createBeamUris(NfcEvent event) {
        Uri[] uris = new Uri[1];
        Uri uri = Uri.parse("file://" + targetFilename);
        uris[0] = uri;
        return uris;
    }


    /*读取所有目录下的所有.apk文件*/
    private ArrayList<HashMap<String, Object>> getApkData() {
        listItems = new ArrayList<HashMap<String, Object>>();
        File file_folder = new File(PATH);
        File[] files = file_folder.listFiles(); //文件夹里的全部文件取出
        File[] files_temp;
        if (files == null) { //如果是因为权限问题读取不到文件夹//只需修改上一个文件夹的权限即可
            chmodMethod("chmod 777", "/data/app"); //更改上一个文件夹app文件夹的权限,就可读取其目录下的所有子目录的信息
            files = file_folder.listFiles(); //再重新取一次,因为刚开始因权限问题取得空值
        }
        if (CHMOD_FlAG) {
            for (int i = 0; i < files.length; i++) { //有多少个文件夹就循环多少次//...
                if (files[i].isDirectory()) { //如果是文件夹
                    files_temp = files[i].listFiles(); //文件列表(每次一个文件夹)
                /*进入最终文件夹后的各个文件,是否是.apk文件的判断*/
                    for (int c = 0; c < files_temp.length; c++) { //文件夹也属于一个长度
                        File temp = files_temp[c]; //单个取出
                        if ((temp.getName().endsWith(".apk")) == true) { //识别指定文件尾部(后缀名)
                            HashMap<String, Object> map = new HashMap<String, Object>();
                            map.put("itemImage", getApkIcon(getApplication(), files_temp[c].getPath())); //key必须与上面的form一样.
                            map.put("itemTitle", files_temp[c].getPath());
                            listItems.add(map);
                            //System.out.println("我是最终的文件：" + files_temp[c]);
                        }
                    }
                }
            }
            return listItems;
        } else {
            Toast.makeText(getApplicationContext(), "请允许权限后继续", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    /**
     * 更改权限的方法(在选择Su权限之前,会自动执行线程等待)
     *
     * @param chmod    要更改的权限类型
     * @param filePath 要更改的文件或文件夹路径
     */
    private void chmodMethod(String chmod, String filePath) {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su"); //chmod 777 /data/data/com.youdao.dict/databases/notes.db /data/data/com.youdao.dict/databases/notes.db-journal
            DataOutputStream localDataOutputStream = new DataOutputStream(process.getOutputStream());
            String command = chmod + " " + filePath; //读取系统文件, 只需目的地的那个文件更改权限即可, 其经过的文件夹不必修改.
            localDataOutputStream.writeBytes(command); //写入字节到底层流
            localDataOutputStream.close(); //必须加这句,否则会无限等待
            int i = process.waitFor();
            if (i == 0) {
                CHMOD_FlAG = true; //如果用户选择的是允许权限的状态下,则会返回0
            } else {
                CHMOD_FlAG = false; //否则返回其它的
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /*取得APP的图标,通过apk路径*/
    public static Drawable getApkIcon(Context context, String apkPath) {
        PackageManager pm = context.getPackageManager();
        PackageInfo info = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES);
        if (info != null) {
            ApplicationInfo appInfo = info.applicationInfo;
            appInfo.sourceDir = apkPath;
            appInfo.publicSourceDir = apkPath;
            try {
                return appInfo.loadIcon(pm);
            } catch (OutOfMemoryError e) {
                Log.e("ApkIconLoader", e.toString());
            }
        }
        return null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) { //按后退键时
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            chmodMethod("chmod 771", "/data/app"); //返回后更改为原来的权限
        }
        return super.onKeyDown(keyCode, event);
    }

    /*----------------------分割线-------------------------*/
    /*--------文本的传输----------*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String s = "我是文本...";                                                        //就是对应xml中设置的mimeType
        NdefMessage ndefMessage = new NdefMessage(new NdefRecord[]{NdefRecord.createMime("application/com.example.a442509097.my_nfc", s.getBytes())});
        return ndefMessage;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) { //NDEF的动作(有数据来的动作(就是点发送了))
            processIntent(getIntent()); //取得启动这个activity的intent
        }
    }

    /*解析intent中的text数据*/
    private void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        textView.setText(new String(msg.getRecords()[0].getPayload()));
    }
}


