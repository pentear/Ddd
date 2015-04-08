package com.example.lqj.ddd;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 调用拍照功能的类（需要系统权限）。
 * 可使用intent.putExtra("MAX_COUNT", 2)设置最大可拍照片的张数，张数拍到最大值时自动返回，默认为只拍1张。
 * 可使用intent.putExtra("FILES_PATH", <路径名>)设置拍照文件的完整存放地点（路径以“/”结尾），默认放在SD卡根目录中。
 * 可使用intent.putExtra("IMAGE_WIDTH",640)、intent.putExtra("IMAGE_HEIGHT",480)设置照片的大小，程序自动匹配与该值最相近的照片大小（宽高之和差值最小）默认为640*480。
 * 可使用intent.putExtra("SHOW_MENU",false)设置删除功能是否在列表上操作，默认为true（即在弹出菜单中操作）。
 * 通过onActivityResult方法（resultCode == RESULT_OK和data.getStringExtra("IMAGES")）获取结果，返回的文件名列表用“|”分隔。
 */
public class CameraActivity extends Activity implements SurfaceHolder.Callback, Camera.AutoFocusCallback, View.OnClickListener, SensorEventListener {
    private final String mSdPath = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private SurfaceHolder mSurfaceHolder = null;
    private Camera mCamera = null;
    private Camera.Parameters mCameraParams = null;
    /**
     * 拍满几张照片后自动返回（默认为1）
     */
    private int mImageCountMax = 1;
    /**
     * 已经拍好的照片数
     */
    private int mImageCount = 0;
    /**
     * 照片文件存入的位置（以“/”结尾）
     */
    private String mPath = "";
    /**
     * 照片默认宽度
     */
    private int mImageWidth = 640;
    /**
     * 照片默认高度
     */
    private int mImageHeight = 480;
    /**
     * 是否显示删除菜单
     */
    private boolean mShowMenu = true;
    /**
     * 照片文件名列表
     */
    private List<String> mImageFiles = new ArrayList<String>();

    /**
     * 照片缩略图视图对象列表
     */
    private List<View> mImageViews = new ArrayList<View>();

    private SensorManager sensorManager = null;
    private float[] accelerometerValues = new float[3];//初始值为0
    private float[] magneticFieldValues = new float[3];//初始值为0
    private boolean hadAccelerometer = false;
    private boolean hadMagneticField = false;
    private int orientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private int lastOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
    private OrientationEventListener mOrientationListener = null;

    private SurfaceView mSurfaceView = null;
    private ImageButton mBtnBack = null;
    private ImageButton mBtnFlash = null;
    private ImageButton mBtnCamera = null;
    private TextView mImageCountTips = null;
    private CameraSeekBar mSeekBar = null;
    private HorizontalScrollView mLayoutImagesScrollView = null;
    private LinearLayout mLayoutImages = null;

    @Override
    protected void onDestroy() {
        mImageFiles.clear();
        mImageViews.clear();
        mLayoutImages.removeAllViews();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        findViews();

        try {
            mImageCountMax = getIntent().getIntExtra("MAX_COUNT", 1);
        } catch (Exception e) {
            mImageCountMax = 1;
            e.printStackTrace();
        }
        try {
            mPath = getIntent().getStringExtra("FILES_PATH");
        } catch (Exception e) {
            mPath = mSdPath;
            e.printStackTrace();
        }
        if (mPath == null || mPath.equals("")) {
            mPath = mSdPath;
        }

        if (mImageCountMax > 1)//只拍一张照片，不显示已拍的照片张数
            mImageCountTips.setVisibility(View.VISIBLE);
        else
            mImageCountTips.setVisibility(View.GONE);

        try {
            mImageWidth = getIntent().getIntExtra("IMAGE_WIDTH", 640);
            mImageHeight = getIntent().getIntExtra("IMAGE_HEIGHT", 480);
        } catch (Exception e) {
            mImageWidth = 640;
            mImageHeight = 480;
            e.printStackTrace();
        }
        if (mImageWidth < 1 || mImageHeight < 1) {
            mImageWidth = 640;
            mImageHeight = 480;
        }

        try {
            mShowMenu = getIntent().getBooleanExtra("SHOW_MENU", true);
        } catch (Exception e) {
            mShowMenu = true;
            e.printStackTrace();
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //获取手机全部的传感器
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        //迭代输出获得上的传感器
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                hadAccelerometer = true;
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                hadMagneticField = true;
        }

        if (!hadAccelerometer || !hadMagneticField) {
            //不能使用Android推荐的方法，用两种传感器来计算出精确的方向，只能使用传统（过时）的方法
            mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    CameraActivity.this.orientation = orientation;
                }
            };
            if (mOrientationListener.canDetectOrientation())
                mOrientationListener.enable();
            else
                mOrientationListener.disable();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        findViews();
    }

    /**
     * 初始化布局
     */
    private void findViews() {
        setContentView(R.layout.camera_main);
        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mBtnBack = (ImageButton) findViewById(R.id.btnBack);
        mBtnFlash = (ImageButton) findViewById(R.id.btnFlash);
        mBtnCamera = (ImageButton) findViewById(R.id.btnCamera);
        mImageCountTips = (TextView) findViewById(R.id.textViewImageCount);
        mSeekBar = (CameraSeekBar) findViewById(R.id.seekBar);
        mLayoutImagesScrollView = (HorizontalScrollView) findViewById(R.id.layoutImagesScrollView);
        mLayoutImages = (LinearLayout) findViewById(R.id.layoutImages);

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mBtnBack.setOnClickListener(this);
        mBtnFlash.setOnClickListener(this);
        mBtnCamera.setOnClickListener(this);

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mCameraParams != null && mCamera != null) {
                    mCameraParams.setZoom(progress);
                    mCamera.setParameters(mCameraParams);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //重绘缩略图列表
        mImageViews.clear();
        mLayoutImages.removeAllViews();
        for (String file : mImageFiles) {
            addImagesView(file);
        }
        showCount();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // 打开相机摄像头
        if (mCamera == null) {
            try {
                mCamera = Camera.open();
                mCamera.setPreviewDisplay(holder);
                mCamera.setDisplayOrientation(getPreviewDegree(this));
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "打开相机失败,请检查系统相机是否正常！",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    /**
     * 根据手机方向获得相机预览画面旋转的角度(竖屏手机需要旋转90度后进行预览)
     */
    private int getPreviewDegree(Activity activity) {
        // 获得手机的方向
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degree = 0;
        // 根据手机的方向计算相机预览画面应该选择的角度
        switch (rotation) {
            case Surface.ROTATION_0:
                degree = 90;
                break;
            case Surface.ROTATION_90:
                degree = 0;
                break;
            case Surface.ROTATION_180:
                degree = 270;
                break;
            case Surface.ROTATION_270:
                degree = 180;
                break;
        }
        return degree;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // 确定键和拍照键
        if (keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_CAMERA) {
            mCamera.autoFocus(this);// 自动对焦
            return true;
        }
        //取消键
        else if (keyCode == KeyEvent.KEYCODE_BACK) {
            onClickBack();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // 释放资源
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        // 设置参数并开始预览
        if (mCamera == null)
            return;

        try {
            mCameraParams = mCamera.getParameters();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "打开相机失败,请检查系统相机是否正常！", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        //设置最大缩放焦距
        if (mCameraParams.isZoomSupported()) {
            mSeekBar.setMax(mCameraParams.getMaxZoom());
            mSeekBar.setProgress(mCameraParams.getZoom());
            mSeekBar.setVisibility(View.VISIBLE);
        } else {
            mSeekBar.setVisibility(View.GONE);
        }

        // 读取注册表信息（相机退出后再进入时无法通过getFlashMode获取到上次的闪光灯模式）
        SharedPreferences sp = getSharedPreferences("CAMERA_PARAMS", 0);
        String flashMode = sp.getString("FLASH_MODE", Camera.Parameters.FLASH_MODE_OFF);//默认为关闭闪光灯

        //闪光灯只亮一下(持续为 Parameters.FLASH_MODE_TORCH)
        if (flashMode.equals(Camera.Parameters.FLASH_MODE_ON))
            mBtnFlash.setImageResource(R.drawable.camera_flash_on);
        else if (flashMode.equals(Camera.Parameters.FLASH_MODE_AUTO))//自动
            mBtnFlash.setImageResource(R.drawable.camera_flash_auto);
        else  //默认关闭
            mBtnFlash.setImageResource(R.drawable.camera_flash_off);

        mCameraParams.setFlashMode(flashMode);//设置上次的闪光灯模式

        Camera.Size pictureSize = getOptimalSupportPictureSize(mCameraParams);
        Camera.Size previewSize = getOptimalSupportPreviewSize(mCameraParams);
        mCameraParams.setPictureFormat(ImageFormat.JPEG);
        mCameraParams.setPreviewSize(previewSize.width, previewSize.height);
        mCameraParams.setPictureSize(pictureSize.width, pictureSize.height);

        try {
            mCamera.setParameters(mCameraParams);
        } catch (Exception e) {
            Log.v("CAMERA", "surfaceChang exception:" + e.getMessage());
        }
        mCamera.startPreview();
    }

    /**
     * 设置照片大小
     */
    private Camera.Size getOptimalSupportPictureSize(Camera.Parameters params) {
        Camera.Size optimalSize = mCamera.new Size(mImageWidth, mImageHeight);
        try {
            List<Camera.Size> supportSizeList = params.getSupportedPictureSizes();
            if (supportSizeList != null) {
                double minDiff = Double.MAX_VALUE;
                for (Camera.Size size : supportSizeList) {//自动匹配与设置值最相近的照片大小（宽高之和差值最小）
                    if (Math.abs(size.height - mImageHeight) + Math.abs(size.width - mImageWidth) < minDiff) {
                        optimalSize = size;
                        minDiff = Math.abs(size.height - mImageHeight) + Math.abs(size.width - mImageWidth);
                    }
                }
            }
        } catch (Exception e) {
            optimalSize = mCamera.new Size(mImageWidth, mImageHeight);
        }
        return optimalSize;
    }

    /**
     * 设置预览界面的大小
     */
    private Camera.Size getOptimalSupportPreviewSize(Camera.Parameters params) {
        Rect rect = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        Camera.Size viewSize;
        //根据屏幕横竖方式，把显示区域的大小转为显示图像的宽高值
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            viewSize = mCamera.new Size(rect.height(), rect.width());
        } else {
            viewSize = mCamera.new Size(rect.width(), rect.height());
        }
        double viewRatio = (double) viewSize.width / viewSize.height;
        Camera.Size optimalSize = mCamera.new Size(viewSize.width, viewSize.height);

        try {
            List<Camera.Size> supportSizeList = params.getSupportedPreviewSizes();
            if (supportSizeList != null) {
                double minDiff = Double.MAX_VALUE;
                for (Camera.Size size : supportSizeList) {//自动匹配与显示界面比值最相近的预览大小（尽量减少预览时的图像变形）
                    if (Math.abs(viewRatio - (double) size.width / size.height) < minDiff) {
                        minDiff = Math.abs(viewRatio - (double) size.width / size.height);
                        optimalSize = size;
                    }
                }
            }
        } catch (Exception e) {
            optimalSize = mCamera.new Size(viewSize.width, viewSize.height);
        }

        Camera.Size viewOptimalSize;
        if (viewRatio > (double) optimalSize.width / optimalSize.height) {
            //控件宽度不变，按比较拉长高度
            viewOptimalSize = mCamera.new Size(viewSize.width, viewSize.width * optimalSize.height / optimalSize.width);
        } else {
            //控件高度不变，按比较拉长宽度
            viewOptimalSize = mCamera.new Size(viewSize.height * optimalSize.width / optimalSize.height, viewSize.height);
        }
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            //竖屏时，图像和控件宽高不一致
            viewOptimalSize = mCamera.new Size(viewOptimalSize.height, viewOptimalSize.width);
        }
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(viewOptimalSize.width, viewOptimalSize.height);
        layoutParams.setMargins(-Math.abs(rect.width() - viewOptimalSize.width) / 2, -Math.abs(rect.height() - viewOptimalSize.height) / 2, 0, 0);
        mSurfaceView.setLayoutParams(layoutParams);

        return optimalSize;
    }

    /**
     * 显示已经拍好的照片张数
     */
    private void showCount() {
        mImageCountTips.setText(String.format("已拍%d张", mImageCount));
    }

    @Override
    public void onAutoFocus(boolean success, Camera camera) {
        if (success && mCamera != null) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info); //获取后置摄像头信息
            mCameraParams = mCamera.getParameters();
            int jpgOrientation = 0;
            if (this.orientation != OrientationEventListener.ORIENTATION_UNKNOWN) {
                jpgOrientation = (this.orientation + 45) / 90 * 90;//90度的倍数
            }
            jpgOrientation = info.orientation + jpgOrientation;
            jpgOrientation = jpgOrientation % 360;

            if (mCameraParams != null) {
                mCameraParams.setRotation(jpgOrientation);
                mCamera.setParameters(mCameraParams);
            }

            mCamera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    String fullPathFile = makeFile(mPath, data);
                    if (addImagesView(fullPathFile)) {
                        mImageCount++;
                        if (mImageCount < mImageCountMax) {
                            showCount();
                            mCamera.stopPreview();
                            mCamera.startPreview();
                        } else {
                            onClickBack();
                        }
                    } else {
                        Toast.makeText(CameraActivity.this, "拍照异常!", Toast.LENGTH_SHORT).show();
                        mCamera.stopPreview();
                        mCamera.startPreview();
                    }
                }
            });
        }
    }

    /**
     * 保存拍好的照片（JPG）文件
     *
     * @param path 预设路径
     * @param data 照片数据
     * @return 最终保存的文件名
     */
    private String makeFile(String path, byte[] data) {
        Calendar c = Calendar.getInstance();
        String jpgName = String.format("%04d%02d%02d%02d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        int index = 0;
        File fileJpg;

        //检查并创建目录
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        //确定文件名
        try {
            do {
                fileJpg = new File(String.format("%s%s%d.jpg", path, jpgName, index++));
            } while (fileJpg.exists());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

        //保存文件
        try {
            OutputStream out = new FileOutputStream(fileJpg);
            out.write(data);
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            if (fileJpg.exists())
                fileJpg.delete();
            return "";
        }
        return fileJpg.getAbsolutePath();
    }

    /**
     * 把拍好的照片放入预览列表中
     *
     * @param file 完整的照片文件名
     * @return 是否成功放入列表
     */
    private boolean addImagesView(String file) {
        if (new File(file).exists()) {
            Resources res = getResources();
            int menuMargin = Math.round(res.getDimension(R.dimen.camera_menu_margin));
            Bitmap bmp = getScaledBitmap(file, menuMargin, menuMargin);//缩放的大小与菜单栏高度一致
            if (bmp != null) {
                int index = mImageFiles.indexOf(file);
                if (index < 0) {
                    index = mImageFiles.size();
                    mImageFiles.add(file);
                }

                final View view = getLayoutInflater().inflate(R.layout.camera_images_item, null);
                final ImageView imageView = (ImageView) view.findViewById(R.id.imageView);
                final ImageButton button = (ImageButton) view.findViewById(R.id.imageButton);
                button.setVisibility(mShowMenu ? View.GONE : View.VISIBLE);

                imageView.setImageBitmap(bmp);
                view.setPadding(2, 3, 2, 3);
                //标记索引，用于点击时判断点击了哪个view
                button.setTag(index);
                imageView.setTag(index);

                mImageViews.add(view);
                mLayoutImages.addView(view);

                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            final int tag = Integer.parseInt(v.getTag().toString());
                            if (tag < mImageFiles.size() && tag >= 0) {
                                if (mShowMenu) {
                                    //先显示菜单
                                    CameraMenuDialog dlg = new CameraMenuDialog(
                                            CameraActivity.this);
                                    dlg.addMenu(2, "查看照片", tag);
                                    dlg.addMenu(1, "删除照片", tag);
                                    dlg.addMenu(0, "返回");
                                    dlg.setOnMenuClickListener(new CameraMenuDialog.OnMenuClickListener() {
                                        @Override
                                        public void onMenuClick(int menuId, String menuName, Object tag, View view) {
                                            if (tag instanceof Integer) {
                                                switch (menuId) {
                                                    case 1:
                                                        deleteImage((int) tag);
                                                        break;
                                                    case 2:
                                                        showImage((int) tag);
                                                        break;
                                                }
                                            }
                                        }
                                    });
                                    dlg.create().show();
                                } else {
                                    //直接显示照片
                                    showImage(tag);
                                }
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                });
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            final int tag = Integer.parseInt(v.getTag().toString());
                            if (tag < mImageFiles.size() && tag >= 0) {
                                CameraMenuDialog dlg = new CameraMenuDialog(
                                        CameraActivity.this);
                                dlg.addMenu(1, "删除照片", tag);
                                dlg.addMenu(0, "返回");
                                dlg.setOnMenuClickListener(new CameraMenuDialog.OnMenuClickListener() {
                                    @Override
                                    public void onMenuClick(int menuId, String menuName, Object tag, View view) {
                                        if (menuId == 1 && tag instanceof Integer) {
                                            deleteImage((int) tag);
                                        }
                                    }
                                });
                                dlg.create().show();
                            }
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                    }
                });
                //移动滚动条到最后
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        int linearWidth = mLayoutImages.getWidth();
                        mLayoutImagesScrollView.scrollTo(linearWidth, 0);
                    }
                }, 500);

                return true;
            }
        }

        return false;
    }

    /**
     * 删除指定的照片文件
     *
     * @param index 索引（mImageFiles上的位置）
     */
    private void deleteImage(int index) {
        if (index >= 0 && index < mImageFiles.size()) {
            try {
                View view = mImageViews.get(index);
                File file = new File(mImageFiles.get(index));
                mImageViews.remove(index);
                mImageFiles.remove(index);
                mLayoutImages.removeView(view);
                mImageCount--;
                showCount();
                for (int i = index; i < mImageViews.size(); i++) {
                    ImageView imageView = (ImageView) mImageViews.get(i).findViewById(R.id.imageView);
                    ImageButton button = (ImageButton) mImageViews.get(i).findViewById(R.id.imageButton);
                    if (imageView != null)
                        imageView.setTag(i);
                    if (button != null)
                        button.setTag(i);
                }
                if (file.exists())
                    file.delete();

                Toast.makeText(this, "图片已删除", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "图片删除出错!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 全屏显示照片
     *
     * @param index 索引（从0开始）
     */
    private void showImage(int index) {
        if (index >= 0 && index < mImageFiles.size()) {
            File file = new File(mImageFiles.get(index));
            if (file.exists()) {
                lastOrientation = this.getResources().getConfiguration().orientation;
                Intent intent = new Intent(this, CameraView.class);
                intent.putExtra("FILE", file.getAbsolutePath());
                intent.putExtra("INDEX", index);
                startActivityForResult(intent, 8888);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 8888) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    int indexDel = data.getIntExtra("INDEX", -1);
                    deleteImage(indexDel);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (lastOrientation != this.getResources().getConfiguration().orientation) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        findViews();//由于SurfaceView无法及时进行重绘，需要重置整个界面
                    }
                }, 200);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 根据指定的宽高缩放图像
     *
     * @param file   图片文件名（完整路径）
     * @param width  缩放后的宽度
     * @param height 缩放后的高度
     * @return 缩放后的图像
     */
    private Bitmap getScaledBitmap(String file, int width, int height) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inJustDecodeBounds = true;//只获取图片的实际宽度和高度，不进行具体解码操作
        BitmapFactory.decodeFile(file, opt);
        int previewWidth = width;
        int previewHeight = height;
        int degree = getPictureDegree(file);
        if (degree != 0 && degree % 180 != 0) {
            previewWidth = height;
            previewHeight = width;
        }
        opt.inSampleSize = 1;//初始值，表示不进行缩放
        if (opt.outWidth > 0 && opt.outHeight > 0 && previewWidth > 0 && previewHeight > 0) {
            int scaleWidth = (int) Math.ceil((float) opt.outWidth / (float) previewWidth);//计算宽度的缩放比
            int scaleHeight = (int) Math.ceil((float) opt.outHeight / (float) previewHeight);//计算高度的缩放比
            opt.inSampleSize = scaleWidth > scaleHeight ? scaleWidth : scaleHeight;//选择绽放比最大的值进行缩放
        }
        //以下代码为了进一步减少出现OOM（内存溢出）的可能性
        opt.inDither = false;//不进行图片抖动处理
        opt.inPreferredConfig = null;//设置让解码器以最佳方式解码//选择绽放比最大的值进行缩放

        opt.inJustDecodeBounds = false;//开始解码
        Bitmap bitmap = BitmapFactory.decodeFile(file, opt);
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
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnBack://返回（退出）
                onClickBack();
                break;
            case R.id.btnFlash://闪光灯
                if (mCameraParams != null) {
                    //更改闪光灯模式后，把信息写入注册表，留着下次再次打开相机时使用
                    SharedPreferences.Editor settings = getSharedPreferences("CAMERA_PARAMS", 0).edit();
                    String flashMode = mCameraParams.getFlashMode();

                    //闪光灯只亮一下(持续为 Parameters.FLASH_MODE_TORCH)
                    if (flashMode.equals(Camera.Parameters.FLASH_MODE_ON)) {
                        //打开转自动
                        mBtnFlash.setImageResource(R.drawable.camera_flash_auto);
                        mCameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                        settings.putString("FLASH_MODE", Camera.Parameters.FLASH_MODE_AUTO);
                    } else if (flashMode.equals(Camera.Parameters.FLASH_MODE_AUTO)) {
                        //自动转关闭
                        mBtnFlash.setImageResource(R.drawable.camera_flash_off);
                        mCameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        settings.putString("FLASH_MODE", Camera.Parameters.FLASH_MODE_OFF);
                    } else {
                        //关闭转打开
                        mBtnFlash.setImageResource(R.drawable.camera_flash_on);
                        mCameraParams.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                        settings.putString("FLASH_MODE", Camera.Parameters.FLASH_MODE_ON);
                    }
                    settings.commit();
                    mCamera.setParameters(mCameraParams);
                }
                break;
            case R.id.btnCamera://拍照
                if (mCamera != null) {
                    mCamera.autoFocus(this);// 自动对焦
                }
                break;
        }
    }

    /**
     * 按了左上角的返回键或系统回退键
     */
    private void onClickBack() {
        Intent intent = new Intent();
        if (mImageCount > 0) {
            StringBuilder sb = new StringBuilder();
            for (String fn : mImageFiles) {
                sb.append(fn);
                sb.append('|');
            }
            String data = sb.toString().trim();
            if (data.length() > 0) {
                intent.putExtra("IMAGES", data.substring(0, data.length() - 1));
                setResult(RESULT_OK, intent);
            }
        }
        finish();
    }

    @Override
    protected void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hadAccelerometer && hadMagneticField) {
            //注册两个传感器，使用Android推荐的方法
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL); //为加速度传感器注册监听器
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                    SensorManager.SENSOR_DELAY_NORMAL); //为磁力传感器注册监听器
        } else {
            //注册方向传感器
            sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (hadAccelerometer && hadMagneticField) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                accelerometerValues = event.values;//加速度传感器
            }
            if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticFieldValues = event.values;//磁力传感器
            }
            calculateOrientation(accelerometerValues, magneticFieldValues);
        }
    }

    /**
     * 通过加速度传感器和磁力传感器值计算方向
     *
     * @param aValues 加速度传感器值
     * @param mValues 磁力传感器值
     */
    private void calculateOrientation(float[] aValues, float[] mValues) {
        if (aValues == null || mValues == null)
            return;
        if (aValues.length < 3 || mValues.length < 3)
            return;

        float[] values = new float[3];

        float[] R = new float[9];
        SensorManager.getRotationMatrix(R, null, aValues, mValues);
        SensorManager.getOrientation(R, values);

        //转成为度数值（以竖屏为参考值）
        values[0] = (float) Math.toDegrees(values[0]);//Z轴（顶的方向：0度为正北，±180为正南，-90为正西，90为正东）
        float degreesX = (float) Math.toDegrees(values[1]);//X轴（顶的方向[纵轴线]与水平面的夹角：-90垂直向上，90垂直向下，范围±90度）
        float degreesY = (float) Math.toDegrees(values[2]);//Y轴（左边向上为正，右边向上为负，范围±180度）
        float degreesDriftY = Math.abs(degreesY);
        if (degreesDriftY > 90) {
            degreesDriftY = 180 - degreesDriftY;
        }

        if (degreesX > 45)
            this.orientation = 180;
        else if (degreesX < -45)
            this.orientation = 0;
        else if (degreesDriftY > 30)
            this.orientation = degreesY > 0 ? 90 : 270;
    }
}
