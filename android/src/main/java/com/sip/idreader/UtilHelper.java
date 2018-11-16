package com.sip.idreader;

import android.graphics.Bitmap;
import android.util.Base64;

import com.zkteco.android.IDReader.WLTService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by train on 2018/11/15.
 */

public class UtilHelper {
    public static Bitmap Bgr2Bitmap(byte[] bgrbuf) {
        int width = WLTService.imgWidth;
        int height = WLTService.imgHeight;
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        int row = 0;
        int col = width - 1;

        for(int i = bgrbuf.length - 1; i >= 3; i -= 3) {
            int color = bgrbuf[i] & 255;
            color += bgrbuf[i - 1] << 8 & '\uff00';
            color += bgrbuf[i - 2] << 16 & 16711680;
            bmp.setPixel(col--, row, color);
            if(col < 0) {
                col = width - 1;
                ++row;
            }
        }

        return bmp;
    }
    public static String bitmapToBase64(Bitmap bitmap) {

        String result = null;
        ByteArrayOutputStream baos = null;
        try {
            if (bitmap != null) {
                baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);

                baos.flush();
                baos.close();

                byte[] bitmapBytes = baos.toByteArray();
                result = Base64.encodeToString(bitmapBytes, Base64.NO_WRAP);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (baos != null) {
                    baos.flush();
                    baos.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

}
