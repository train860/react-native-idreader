package com.sip.idreader;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.zkteco.android.IDReader.IDPhotoHelper;
import com.zkteco.android.IDReader.WLTService;
import com.zkteco.android.biometric.core.device.ParameterHelper;
import com.zkteco.android.biometric.core.device.TransportType;
import com.zkteco.android.biometric.module.idcard.IDCardReader;
import com.zkteco.android.biometric.module.idcard.IDCardReaderFactory;
import com.zkteco.android.biometric.module.idcard.exception.IDCardReaderException;
import com.zkteco.android.biometric.module.idcard.meta.IDCardInfo;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by train on 2018/11/15.
 */

public class IDReaderModule extends ReactContextBaseJavaModule implements LifecycleEventListener{

    private ReactApplicationContext mContext;

    private IDCardReader mIdCardReader;

    /**
     * 是否需要读取指纹
     */
    private boolean mNeedFP = false;

    /**
     * 身份证模块打开状态
     */
    private boolean mIDCardReaderEnable = false;
    /**
     * 身份证线程运行控制标记
     */
    private boolean mRunFlag = false;
    /**
     * 身份证读取线程
     */
    private IDCardReadThread mIDCardReadThread;


    public IDReaderModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mContext=reactContext;
        Looper.prepare();
    }

    private class IDCardReadThread extends Thread {
        @Override
        public void run() {
            System.out.println("mRunFlag======"+mRunFlag);
            while (mRunFlag) {
                if (mIdCardReader != null) {
                    try {
                        boolean cardAvailable = mIdCardReader.findCard(0);
                        if (cardAvailable) {
                            boolean selected = mIdCardReader.selectCard(0);
                            if (selected) {
                                mHandler.sendEmptyMessage(WHAT_FIND_CARD);
                                int readCardEx;
                                if (!mNeedFP) {
                                    readCardEx = mIdCardReader.readCardEx(0, 0);
                                } else {
                                    readCardEx = mIdCardReader.readCardEx(0, 1);
                                }
                                if (readCardEx == 1 || readCardEx == 2 || readCardEx == 3) {
                                    //居民身份证
                                    IDCardInfo lastIDCardInfo = mIdCardReader.getLastIDCardInfo();
                                    Message message = new Message();
                                    message.obj = lastIDCardInfo;
                                    message.what = WHAT_READ_CARD;
                                    mHandler.sendMessage(message);
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.d("MainActivity", "Read card error = " + e.getMessage());
                    }
                }
            }
            super.run();
        }
    }

    /**
     * 通讯标记
     */
    private static final int WHAT_FIND_CARD = 10098;
    private static final int WHAT_READ_CARD = 10099;

    /**
     * 子线程通知主线程更新
     */
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;

            switch (what) {
                case WHAT_FIND_CARD:
                    //发现身份证,正在读取,请勿移开
                    break;
                case WHAT_READ_CARD:
                    IDCardInfo idCardInfo = (IDCardInfo) msg.obj;

                    String photo="";
                    if (idCardInfo.getPhotolength() > 0) {

                        byte[] buf = new byte[WLTService.imgLength];
                        if (1 == WLTService.wlt2Bmp(idCardInfo.getPhoto(), buf)) {
                            photo=UtilHelper.bitmapToBase64(UtilHelper.Bgr2Bitmap(buf));
                        }
                    }
                    WritableMap params = Arguments.createMap();
                    params.putString("name",idCardInfo.getName());
                    params.putString("photo",photo);
                    mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("IDReaderResult",params);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    @Override
    public String getName() {
        return "IDReaderModule";
    }

    @Override
    public void onHostResume() {

    }

    @Override
    public void onHostPause() {

    }

    @Override
    public void onHostDestroy() {
        this.closeDevice();
    }

    @ReactMethod
    public void readData(){
        startIDCardReader();
    }
    @ReactMethod
    public void deviceState(Promise promise){
        WritableMap map = Arguments.createMap();
        if(mIDCardReaderEnable&&mRunFlag){
            try {
                boolean status=mIdCardReader.getStatus(0);
                map.putBoolean("online",status);
            } catch (IDCardReaderException e) {
                e.printStackTrace();
                map.putBoolean("online",false);
            }

        }else{
            map.putBoolean("online",false);
        }
        promise.resolve(map);
    }
    @ReactMethod
    public void closeDevice(){

        if (mIDCardReaderEnable) {
            if (mRunFlag) {
                mRunFlag = false;
                try {
                    mIdCardReader.close(0);
                } catch (IDCardReaderException e) {
                    e.printStackTrace();
                }
                mIDCardReadThread.interrupt();
            }
        }
    }

    /**
     * 初始化身份证阅读器
     */
    private void startIDCardReader() {
        if (mRunFlag) {
            return;
        }
        try {

            Map<String, Object> idReaderParams = new HashMap<>(2);
            idReaderParams.put(ParameterHelper.PARAM_SERIAL_SERIALNAME, "/dev/ttyMT1");
            idReaderParams.put(ParameterHelper.PARAM_SERIAL_BAUDRATE, 115200);
            mIdCardReader = IDCardReaderFactory.createIDCardReader(mContext, TransportType.SERIALPORT, idReaderParams);
            mIdCardReader.open(0);
            mIDCardReadThread = new IDCardReadThread();
            mIDCardReadThread.start();
            mRunFlag = true;
            mIDCardReaderEnable=true;

        } catch (Exception e) {
            e.printStackTrace();
            //身份证模块初始化错误
            mIDCardReaderEnable=false;
            WritableMap params = Arguments.createMap();
            params.putString("error",e.getMessage());
            mContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit("IDReaderResult",params);
        }
    }
}
