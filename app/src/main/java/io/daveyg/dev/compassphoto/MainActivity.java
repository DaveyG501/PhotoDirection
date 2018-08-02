package io.daveyg.dev.compassphoto;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements SensorEventListener {
    static final int REQUEST_IMAGE_CAPTURE = 1;

    private SensorManager mSensorManager;
    private Sensor mMagnometer;
    private Sensor mAccelerometer;
    private float[] mLastAccelerometer = new float[3];
    private float[] mLastMagnometer = new float[3];
    private float[] mR = new float[9];
    private float[] mOrientation = new float[3];
    private boolean mLastAccelerometerSet = false;
    private boolean mLastMagnometerSet = false;
    private String mCurrentPhotoPath;
    private float azimuthInDegrees = 0;
    private TextView degreeText;
    private DecimalFormat df = new DecimalFormat("###.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mMagnometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        degreeText = findViewById(R.id.degreeValue);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(imageFileName, ".jpg", storageDir);
        return image;
    }

    public void dispatchCameraIntent(View view) throws IOException {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) == null) {
            return;
        }
        try {
            File photoFile = createImageFile();
            mCurrentPhotoPath = photoFile.getAbsolutePath();
            Uri photoURI = FileProvider.getUriForFile(this, "io.daveyg.dev.fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == mAccelerometer) {
            System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
            mLastAccelerometerSet = true;
        }
        if (event.sensor == mMagnometer) {
            System.arraycopy(event.values, 0, mLastMagnometer, 0, event.values.length);
            mLastMagnometerSet = true;
        }
        if (mLastMagnometerSet && mLastAccelerometerSet) {
            SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnometer);
            SensorManager.getOrientation(mR, mOrientation);
            float azimuthInRadians = mOrientation[0];
            azimuthInDegrees = (float) (Math.toDegrees(azimuthInRadians) + 360) % 360;
            degreeText.setText(df.format(azimuthInDegrees));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            try {
                ExifInterface exif = new ExifInterface(mCurrentPhotoPath);
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION, convertToRationalEquation(azimuthInDegrees));
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /*
     * This is a very rough, quick and dirty, inaccurate sort of way to get the Exif API to do what I want
     */
    private String convertToRationalEquation(float value) {
        float timesHundred = value * 100;
        float rounded = Math.round(timesHundred);
        return String.valueOf(rounded) + "/100";
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //Do Nothing
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mMagnometer, SensorManager.SENSOR_DELAY_GAME);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }
}
