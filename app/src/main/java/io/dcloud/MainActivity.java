package io.dcloud;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.huashi.bluetooth.HSBlueApi;
import com.huashi.bluetooth.IDCardInfo;
import com.huashi.bluetooth.Utility;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/*
蓝牙连接身份证读卡器类，不是入口文件
*/

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private TextView tv_sam, tv_info;
    private ImageView iv;
    private Button btconn, btread, btclose, btsleep, btweak;
    String filepath = "";
    boolean isSlepp = false;

    private HSBlueApi api;
    private IDCardInfo ic;
    private boolean isConn = false;
    int ret;

    private View diaView;
    private TextView tv_ts;
    private ListView lv;
    private Button scanf;
    private List<BluetoothDevice> bundDevices;
    private List<BluetoothDevice> notDevices;

    private BluetoothAdapter bluetoothAdapter;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isConn){
            api.unInit();
            isConn = false;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e("UUUU", Environment.getExternalStorageDirectory().toString());
        initData();
    }

    public String initData() {
        api = new HSBlueApi(this,filepath);
        try {
            ret = api.init();
        } catch (Exception e){
            System.out.println(e);
        }
        ret = api.init();
        if (ret == -1) {
            return "failed";
        } else {
            return "success";
        }

    }

    public String connect(){
        ret = api.connect();
        if (ret == 0){
            return "success";
        }else {
            return "failed";
        }
    }


    public String disconnect(){
        int ret = api.unInit();
        if (ret == 0) {
            isConn = false;
            return "success";
        } else {
            return "failed";
        }
    }

    public void sleep(){
        api.sleep();
    }

    public  void wake(){
        api.weak();
    }

    public String readinfo(){
        Map<String, Object> rs = new HashMap<>();
        ret = api.Authenticate(500);
        ic = new IDCardInfo();
        ret = api.Read_Card(ic, 2000);
        if (ret!=1) {
            rs.put("error","读卡失败");
        } else {

            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            byte[] fp = new byte[1024];
            fp = ic.getFpDate();
            String m_FristPFInfo = "";
            String m_SecondPFInfo = "";

            if (fp[4] == (byte)0x01) {
                rs.put("m_FristPFInfo",String.format("%s-%d", Utility.GetFPcode(fp[5]), fp[6]));
            } else {
                rs.put("m_FristPFInfo","身份证无指纹 \n");
            }
            if (fp[512 + 4] == (byte)0x01) {
                rs.put("m_SecondPFInfo",String.format("%s-%d", Utility.GetFPcode(fp[512 + 5]),fp[512 + 6]));
            } else {
                rs.put("m_SecondPFInfo","身份证无指纹 \n");
            }
            if (ic.getcertType() == " ") {
                rs.put("idcard",ic.getIDCard());
                rs.put("pname",ic.getPeopleName());
                rs.put("sex",ic.getSex());
                rs.put("strBirthday",df.format(ic.getBirthDay()));
                rs.put("nation",ic.getPeople());
                rs.put("signningOrgan",ic.getDepartment());
                rs.put("serviceDate",ic.getStrartDate() + "-"+ ic.getEndDate());
                rs.put("address",ic.getAddr());
            } else {
                if(ic.getcertType() == "J")
                {
                    rs.put("idcard",ic.getPassCheckID());
                    rs.put("pname",ic.getPeopleName());
                    rs.put("sex",ic.getSex());
                    rs.put("strBirthday",df.format(ic.getBirthDay()));
                    rs.put("address",ic.getAddr());
                    rs.put("serviceDate",ic.getStrartDate() + "-"+ ic.getEndDate());
                    rs.put("issuesNum",ic.getissuesNum());
                    rs.put("signningOrgan",ic.getDepartment());
                }
                else{
                    if(ic.getcertType() == "I")
                    {
                        rs.put("idcard",ic.getIDCard());
                        rs.put("pname",ic.getPeopleName());
                        rs.put("chname",ic.getstrChineseName());
                        rs.put("sex",ic.getSex());
                        rs.put("strBirthday",df.format(ic.getBirthDay()));
                        rs.put("nation",ic.getstrNationCode());
                        rs.put("signningOrgan",ic.getDepartment());
                        rs.put("serviceDate",ic.getStrartDate() + "-"+ ic.getEndDate());
                        rs.put("address",ic.getAddr());
                        rs.put("strCertVer",ic.getstrCertVer());
                    }
                }
            }
            try {
                byte[] bmpBuf = new byte[102 * 126 * 3 + 54 + 126 * 2]; // 照片头像bmp数据
                String bmpPath = "";

                int ret = api.unpack(ic.getwltdata(), bmpBuf, bmpPath);

                if (ret != 1) {// 解码失败
                    rs.put("photo",null);
                } else {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bmpBuf, 0,
                            bmpBuf.length);
                    rs.put("photo","data:image/png;base64,"+bitmaptoString(bitmap));
                }

            } catch (Exception e) {
                rs.put("photo",null);
            }
        }

        JSONObject jsonObj=new JSONObject(rs);
        System.out.println(jsonObj.toString());
        return jsonObj.toString();
    }

    public String bitmaptoString(Bitmap bitmap) {
        // 将Bitmap转换成Base64字符串
        StringBuffer string = new StringBuffer();
        ByteArrayOutputStream bStream = new ByteArrayOutputStream();

        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bStream);
            bStream.flush();
            bStream.close();
            byte[] bytes = bStream.toByteArray();
            string.append(Base64.encodeToString(bytes, Base64.NO_WRAP));
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("string.."+string.length());
        return string.toString();
    }

    public int GetBlueState(){
        return api.GetBlueState();
    }



}

