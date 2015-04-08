package com.example.lqj.ddd;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.CheckedInputStream;


public class MainActivity extends ActionBarActivity implements SensorEventListener {
    private TextView tvV, tvX, tvY, tvZ;
    private SensorManager sensorManager = null;
    private float[] accelerometerValues = new float[3];//初始值为0
    private float[] magneticFieldValues = new float[3];//初始值为0
    private boolean hadAccelerometer = false;
    private boolean hadMagneticField = false;
    private OrientationEventListener mOrientationListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvV = (TextView) findViewById(R.id.textViewV);
        tvX = (TextView) findViewById(R.id.textViewX);
        tvY = (TextView) findViewById(R.id.textViewY);
        tvZ = (TextView) findViewById(R.id.textViewZ);

        //http://www.tuicool.com/articles/vqURjy


        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        StringBuffer sb = new StringBuffer();
        //获取手机全部的传感器
        List<Sensor> sensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        //迭代输出获得上的传感器
        for (Sensor sensor : sensors) {
            if (sensor.getType() == Sensor.TYPE_ACCELEROMETER)
                hadAccelerometer = true;
            if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
                hadMagneticField = true;

            sb.append(sensor.getName().toString());
            sb.append("\n");
            Log.i("Sensor", sensor.getName().toString());
        }

        if (!hadAccelerometer || !hadMagneticField) {
            //不能使用Android推荐的方法，用两种传感器来计算出精确的方向，只能使用传统（过时）的方法
            mOrientationListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int orientation) {
                    //以左右摆角20度（40度夹角）来判断方向
                    if (orientation == -1) {
                        tvV.setText("手机平放" + orientation);
                    } else if (orientation < 20 || orientation > 340) {
                        tvV.setText("手机顶部向上" + orientation);
                    } else if (orientation < 110 && orientation > 70) {
                        tvV.setText("手机左边向上" + orientation);
                    } else if (orientation < 200 && orientation > 160) {
                        tvV.setText("手机底边向上" + orientation);
                    } else if (orientation < 290 && orientation > 250) {
                        tvV.setText("手机右边向上" + orientation);
                    } else {
                        tvV.setText(orientation + "");
                    }
                }
            };
            if (mOrientationListener.canDetectOrientation())
                mOrientationListener.enable();
            else
                mOrientationListener.disable();
        }

        //给文本控件赋值
        //tvV.setText(sb.toString());

        findViewById(R.id.button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("MAX_COUNT", 8);
                //intent.putExtra("SHOW_MENU",false);
                //sendBroadcast(new Intent("android.intent.action.NAVIGATION_DISABLE"));
                startActivityForResult(intent, 8080);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 8080) {//与调用startActivityForResult方法时第二的参数一致
            //sendBroadcast(new Intent("android.intent.action.NAVIGATION_ENABLE"));
            if (resultCode == RESULT_OK && data != null) {
                String files = data.getStringExtra("IMAGES");
                if (files != null) {
                    String[] images = files.split("\\|", -1);
                    for (String image : images) {
                        Log.i("CAMERA", image.toString());
                    }
                }
            }
        } else
            super.onActivityResult(requestCode, resultCode, data);
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
//        Log.i(String.valueOf(event.sensor.getType()), "values[0]: " + event.values[0]);
//        Log.i(String.valueOf(event.sensor.getType()), "values[1]: " + event.values[1]);
//        Log.i(String.valueOf(event.sensor.getType()), "values[2]: " + event.values[2]);
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

        tvZ.setText("Z: " + values[0]);
        tvX.setText("X: " + degreesX);
        tvY.setText("Y: " + degreesY);

//        double cosx = Math.cos(values[1]);
//        double cosy = Math.cos(values[2]);
//        double v = Math.acos(Math.sqrt(cosx * cosx * cosy * cosy / (cosx * cosx + cosy * cosy)));
//        double d = Math.toDegrees(v);
//
//        tvV.setText(String.valueOf(d));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
