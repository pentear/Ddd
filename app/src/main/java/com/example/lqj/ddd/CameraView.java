package com.example.lqj.ddd;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.example.lqj.ddd.util.SystemUiHider;

import java.io.IOException;


/**
 * 照片全屏显示界面
 */
public class CameraView extends Activity {
    /**
     * 自动隐藏延时时间（毫秒）
     */
    private static final int HIDE_DELAY_MILLIS = 3000;
    /**
     * 按钮隐藏控件对象
     */
    private SystemUiHider mSystemUiHider;
    /**
     * 照片在列表中的序号
     */
    private int index = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.hide();
        setContentView(R.layout.camera_view);

        final View controlsView = findViewById(R.id.fullscreen_content_controls);
        final View contentView = findViewById(R.id.imageView);

        mSystemUiHider = SystemUiHider.getInstance(this, contentView, SystemUiHider.FLAG_HIDE_NAVIGATION);
        mSystemUiHider.setup();
        mSystemUiHider
                .setOnVisibilityChangeListener(new SystemUiHider.OnVisibilityChangeListener() {
                    int mControlsHeight;
                    int mShortAnimTime;

                    @Override
                    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
                    public void onVisibilityChange(boolean visible) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                            //当前环境支付动画方式隐藏
                            if (mControlsHeight == 0) {
                                mControlsHeight = controlsView.getHeight();
                            }
                            if (mShortAnimTime == 0) {
                                mShortAnimTime = getResources().getInteger(
                                        android.R.integer.config_shortAnimTime);
                            }
                            controlsView.animate()
                                    .translationY(visible ? 0 : mControlsHeight)
                                    .setDuration(mShortAnimTime);
                        } else {
                            //不支持动画，则直接隐藏
                            controlsView.setVisibility(visible ? View.VISIBLE : View.GONE);
                        }

                        if (visible) {
                            delayedHide(HIDE_DELAY_MILLIS);
                        }
                    }
                });

        //点击图片时切换按钮的可见状态
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSystemUiHider.toggle();
            }
        });

        //点击返回按钮
        findViewById(R.id.buttonReturn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        //点击删除按钮
        findViewById(R.id.buttonDelete).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (index >= 0) {
                    //返回要删除的照片程序进行删除
                    Intent intent = new Intent();
                    intent.putExtra("INDEX", index);
                    setResult(RESULT_OK, intent);
                    finish();
                }
            }
        });

        try {
            index = getIntent().getIntExtra("INDEX", -1);
        } catch (Exception e) {
            index = -1;
            e.printStackTrace();
        }
        if (index >= 0) {
            try {
                String fn = getIntent().getStringExtra("FILE");
                if (fn != null) {
                    ((ImageView) contentView).setImageBitmap(getBitmap(fn));
                }
            } catch (Exception e) {
                index = -1;
                e.printStackTrace();
            }
        }
    }

    /**
     * 根据指定的照片文件名获了图像信息
     *
     * @param fileName 照片完整文件名
     * @return 图像
     */
    private Bitmap getBitmap(String fileName) {
        try {
            DisplayMetrics dm = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(dm);
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;

            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inJustDecodeBounds = true;//只获取图片的实际宽度和高度，不进行具体解码操作
            BitmapFactory.decodeFile(fileName, opt);
            int degree = getPictureDegree(fileName);
            if (degree != 0 && degree % 180 != 0) {
                screenWidth = dm.heightPixels;
                screenHeight = dm.widthPixels;
            }
            opt.inSampleSize = 1;//初始值，表示不进行缩放
            if (opt.outWidth > 0 && opt.outHeight > 0 && screenWidth > 0 && screenHeight > 0) {
                int scaleWidth = Math.round((float) opt.outWidth / (float) screenWidth);//计算宽度的缩放比
                int scaleHeight = Math.round((float) opt.outHeight / (float) screenHeight);//计算高度的缩放比
                opt.inSampleSize = scaleWidth > scaleHeight ? scaleWidth : scaleHeight;//选择绽放比最大的值进行缩放
            }
            //以下代码为了进一步减少出现OOM（内存溢出）的可能性
            opt.inDither = false;//不进行图片抖动处理
            opt.inPreferredConfig = null;//设置让解码器以最佳方式解码//选择绽放比最大的值进行缩放

            opt.inJustDecodeBounds = false;//开始解码
            Bitmap bitmap = BitmapFactory.decodeFile(fileName, opt);
            if (degree != 0 && bitmap != null) {
                Matrix matrix = new Matrix();
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                matrix.postRotate(degree);
                try {
                    Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h,
                            matrix, true);
                    bitmap.recycle();
                    bitmap = null;
                    return newBitmap;
                } catch (OutOfMemoryError e) {
                }
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 读取图片属性：旋转的角度
     *
     * @param file 图片绝对路径
     * @return degree旋转的角度
     */
    private int getPictureDegree(String file) {
        int degree = 0;
        try {
            ExifInterface exifInterface = new ExifInterface(file);
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    degree = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    degree = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    degree = 270;
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return degree;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        //完成窗体创建后隐藏按钮
        delayedHide(100);
    }

    /**
     * 界面点击监听事件
     */
    View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            delayedHide(HIDE_DELAY_MILLIS);
            return false;
        }
    };

    Handler mHideHandler = new Handler();
    Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            mSystemUiHider.hide();
        }
    };

    /**
     * 重置延时隐藏
     *
     * @param delayMillis 延时时间（毫秒）
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
